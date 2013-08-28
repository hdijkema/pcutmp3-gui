/*
 * Created on 13.07.2005
 */
package de.zebee.mpa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Vector;

import org.blinkenlights.jid3.MP3File;
import org.blinkenlights.jid3.MediaFile;
import org.blinkenlights.jid3.v1.ID3V1_0Tag;
import org.blinkenlights.jid3.v2.ID3V2_3_0Tag;
import org.blinkenlights.jid3.v2.TPE2TextInformationID3V2Frame;

import de.zebee.mpa.util.ScannedMP3;

/**
 * @author Sebastian Gesemann
 */
public class MainCLI {
	
	public interface Report {
		public void println(String msg);
	};

    private static String EVIL_CHARS   = "?*\":/\\";
    private static String REPLACE_WITH = "  '   ";
    private static long   sampleHz     = 44100;
    
    private Report _reporter;

    public static String replaceEvilCharacters(String s) {
        StringBuffer sb = new StringBuffer(s);
        for (int cc = EVIL_CHARS.length() - 1; cc >= 0; cc--) {
            char c = EVIL_CHARS.charAt(cc);
            for (int i = 0; i < sb.length(); i++) {
                if (sb.charAt(i) == c) {
                    sb.setCharAt(i, REPLACE_WITH.charAt(cc));
                }
            }
        }
        s = sb.toString();
        sb.setLength(0);
        StringTokenizer st = new StringTokenizer(s, " ", false);
        for (boolean first = true; st.hasMoreTokens();) {
            if (first) {
                first = false;
            }
            else {
                sb.append(' ');
            }
            sb.append(st.nextToken());
        }
        return sb.toString();
    }

    public long toSampleRate(long frm) {
        long cfrm = (long) (frm * (double) (sampleHz / 44100.0));
        _reporter.println("frm = " + frm + ", cfrm = " + cfrm + ", sampleHz = " + sampleHz);
        return cfrm;
    }

    public static int MSFstring2sector(String time) {
        time = time.trim();
        int colon1 = time.indexOf(':');
        int colon2 = time.indexOf(':', colon1 + 1);
        if (colon2 < 0)
            return -1;
        try {
            int min = Integer.parseInt(time.substring(0, colon1));
            int sec = Integer.parseInt(time.substring(colon1 + 1, colon2));
            int frm = Integer.parseInt(time.substring(colon2 + 1));
            return frm + 75 * (sec + 60 * min);
        }
        catch (NumberFormatException nfe) {
            return -1;
        }
    }

    public static final String DEFAULT_NAMING_SCHEME = "%n. %p - %t";
    
    public MainCLI(Report r) {
    	_reporter = r;
    	// does currently nothing
    }

    public void run(String[] args) throws IOException {
        _reporter.println("\nPCutMP3 -- Properly Cut MP3 v0.98 BETA\n");

        if (args == null || args.length < 1) {
            printHelp();
        }

        String cutParams = null;
        boolean cutCue = false;
        String outScheme = DEFAULT_NAMING_SCHEME;
        boolean missingOptionParam = false;
        int nonOptionCounter = 0;
        String srcFile = null;
        String outDir = null;

        for (int i = 0; i < args.length; i++) {
            String currArg = args[i];
            if (currArg.equals("--cue")) {
                if (i + 1 >= args.length) {
                    missingOptionParam = true;
                    break;
                }
                cutParams = args[++i];
                cutCue = true;
            }
            // else if (currArg.equals("--crop")) {
            // if (i + 1 >= args.length) {
            // missingOptionParam = true;
            // break;
            // }
            // cutParams = args[++i];
            // cutCue = false;
            // }
            else if (currArg.equals("--out")) {
                if (i + 1 >= args.length) {
                    missingOptionParam = true;
                    break;
                }
                outScheme = args[++i];
            }
            else if (currArg.equals("--dir")) {
                if (i + 1 >= args.length) {
                    missingOptionParam = true;
                    break;
                }
                outDir = args[++i];
            }
            else {
                if (nonOptionCounter == 0) {
                    srcFile = currArg;
                    nonOptionCounter++;
                }
            }
        }
        if (missingOptionParam) {
            _reporter.println("missing option parameter");
            return;
        }

        Cue cueFile = null;

        if (cutParams != null) {
            if (cutCue) {

                cueFile = loadCUE(cutParams, 1L << 50);

                if (srcFile == null) {
                    srcFile = cueFile.getPathToMP3();
                    File fo = new File(srcFile);

                    // If Filename isn't absolute then try to use the one given
                    // in the parms
                    if (!fo.isAbsolute()) {
                        File t = new File(cutParams);

                        String p = t.getParent();
                        if (p != null) {
                            fo = new File(p, srcFile);
                            srcFile = fo.getAbsolutePath();
                        }
                    }

                    if (!srcFile.toLowerCase().endsWith(".mp3")) {
                        String t = fo.getName();
                        int p = t.lastIndexOf('.');
                        if (p < 0) {
                            srcFile += ".mp3";
                        }
                        else {
                            srcFile = srcFile.substring(0, srcFile.length() - t.length() + p)
                                    + ".mp3";
                        }
                    }
                }
            }
        }

        File srcFileFile = null;
        if (srcFile != null) {
            srcFileFile = new File(srcFile);
            if (!srcFileFile.canRead()) {
                _reporter.println("can't access source mp3 file (" + srcFileFile + ")");
                return;
            }
        }
        else {
            _reporter.println("source mp3 file not given");
            System.exit(1);
        }

        ScannedMP3 scannedMP3 = null;
        try {
            _reporter.println("scanning \"" + srcFile + "\" ...");
            scannedMP3 = new ScannedMP3(new FileInputStream(srcFileFile));
        }
        catch (FileNotFoundException e) {
            _reporter.println("file not found (" + srcFileFile + ")");
            return;
        }
        catch (IOException e) {
            _reporter.println("i/o error occured while scanning source mp3 file (" + srcFileFile
                    + ")");
            e.printStackTrace();
            return;
        }

        /*
         * if (cutParams != null && !cutCue) { cueFile =
         * parseManualCrop(cutParams, scannedMP3 .getSamplingFrequency()); }
         */

        _reporter.println(scannedMP3.toString());
        sampleHz = scannedMP3.getSamplingFrequency();
        _reporter.println("sampleHz = " + sampleHz);
        if (cueFile != null) {
            if (outScheme.indexOf("%n") < 0 && outScheme.indexOf("%t") < 0) {
                _reporter.println("The usage of either %n or %t is mandatory in the naming");
                _reporter.println("scheme if you want to extract more than one track!");
                return;
            }
        }

        if (cueFile != null) {
            // convert sectors.
            {
                int i;
                for (i = 0; i < cueFile.getNumberTracks(); i++) {
                    long smp = cueFile.getTrack(i).getStartSector();
                    long smp1 = toSampleRate(smp);
                    cueFile.getTrack(i).setStartSector(smp1);
                    long emp = cueFile.getTrack(i).getEndSector();
                    long emp1 = toSampleRate(emp);
                    cueFile.getTrack(i).setEndSector(emp1);
                    ;
                    _reporter.println("Start sector: smp = " + smp + ", smp1 = " + smp1);
                    _reporter.println("End sector  : emp = " + emp + ", emp1 = " + emp1);
                }
            }

            boolean writeTag = cueFile.getNumberTracks() > 0;

            // This basically sets the end tracks sector to the total number
            // sectors of the MP3
            if (cutCue) {
                cueFile.getTrack(cueFile.getNumberTracks() - 1).setEndSector(
                        scannedMP3.getSampleCount());
            }

            String src = new File(srcFile).getName();
            int li = src.lastIndexOf('.');
            if (li >= 0) {
                src = src.substring(0, li);
            }

            String tt = ""; // track title
            String tp = ""; // track performer
            String ta = ""; // track album
            int tn = -1; // track number
            String ap = ""; // album performer

            if (writeTag) {
                ta = cueFile.getTitle();
                ap = cueFile.getPerformer();
            }

            if (outDir != null && outDir.length() > 0) {
                char p = File.separatorChar;
                if (outDir.charAt(outDir.length() - 1) != p) {
                    outDir += p;
                }

                File directory = new File(outDir);
                if (!directory.exists()) {
                    directory.mkdir();
                }

            }
            else {
                outDir = null;
            }

            for (int i = 0; i < cueFile.getNumberTracks(); i++) {

                Track t = cueFile.getTrack(i);
                tn = t.getTrackNumber();
                tt = t.getTitle();
                tp = t.getPerformer();

                String fn = replaceEvilCharacters(evalScheme(outScheme, src, tn + "", tt, tp, ta))
                        + ".mp3";

                if (outDir != null)
                    fn = outDir + fn;

                _reporter.println("writing \"" + fn + "\" ...");
                FileOutputStream fops = new FileOutputStream(fn);

                try {
                    scannedMP3.crop(t.getStartSector(), t.getEndSector(), new FileInputStream(
                            srcFileFile), fops);
                } finally {
                    fops.close();
                }

                if (writeTag) {
                    try {
                        File oSourceFile = new File(fn);
                        MediaFile oMediaFile = new MP3File(oSourceFile);

                        ID3V1_0Tag oID3V1_0Tag = new ID3V1_0Tag();
                        ID3V2_3_0Tag oID3V2_3_0Tag = new ID3V2_3_0Tag();

                        if (tn >= 0) {
                            oID3V2_3_0Tag.setTrackNumber(tn);
                        }
                        if (ta != null && ta.length() > 0) {
                            oID3V2_3_0Tag.setAlbum(ta);
                            oID3V1_0Tag.setAlbum(ta);
                        }
                        if (ap != null && ap.length() > 0) {
                            oID3V2_3_0Tag
                                    .setTPE2TextInformationFrame(new TPE2TextInformationID3V2Frame(
                                            ap));
                        }
                        if (tp != null && tp.length() > 0) {
                            oID3V2_3_0Tag.setArtist(tp);
                            oID3V1_0Tag.setArtist(tp);
                        }
                        if (tt != null && tt.length() > 0) {
                            oID3V2_3_0Tag.setTitle(tt);
                            oID3V1_0Tag.setTitle(tt);
                        }

                        // set this v2.3.0 tag in the media file object
                        oMediaFile.setID3Tag(oID3V2_3_0Tag);

                        // update the actual file to reflect the current state
                        // of our object
                        oMediaFile.sync();
                    }
                    catch (Exception e) {
                    }
                }
            }
            _reporter.println("done.");
        }
    }

    public static String evalScheme(String scheme, String srcName, String trackNo,
            String trackTitle, String trackPerf, String trackAlb) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            if (c == '%') {
                if (i + 1 < scheme.length()) {
                    char c2 = scheme.charAt(++i);
                    switch (c2) {
                        case 's': {
                            sb.append(srcName);
                            break;
                        }
                        case 'n': {
                            sb.append(trackNo);
                            break;
                        }
                        case 't': {
                            sb.append(trackTitle);
                            break;
                        }
                        case 'p': {
                            sb.append(trackPerf);
                            break;
                        }
                        case 'a': {
                            sb.append(trackAlb);
                            break;
                        }
                        case '%': {
                            sb.append('%');
                            break;
                        }
                    }
                }
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String filter(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public Cue loadCUE(String cueFilename, long sampleCount) throws IOException {

        Cue cue = new Cue();

        cue.setSampleCount(sampleCount);

        FileInputStream fips = new FileInputStream(cueFilename);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(fips));
            int trackNumber = 1;

            // Filename and Path of the MP3 to be split
            String mp3Filename = null;

            // Current Track
            Track currentTrack = null;

            try {
                // First Line in Cue File
                String line = br.readLine().trim();

                while (line != null) {
                    StringTokenizer st = new StringTokenizer(line, " ", false);

                    if (st.countTokens() > 0) {

                        String token = st.nextToken().toLowerCase();

                        if (token.equals("file")) {
                            if (mp3Filename == null) {
                                String t = "";
                                while (st.hasMoreTokens()) {
                                    t = st.nextToken();
                                }

                                t = line.substring(5, line.length() - t.length()).trim();
                                if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
                                    t = t.substring(1, t.length() - 1);
                                }
                                mp3Filename = t;
                                cue.setPathToMP3(mp3Filename);
                            }
                        }
                        else if (token.equals("performer")) {
                            if (st.hasMoreTokens()) {
                                String t = st.nextToken();
                                while (st.hasMoreTokens()) {
                                    t += " " + st.nextToken();
                                }

                                if (currentTrack != null) {
                                    currentTrack.setPerformer(filter(t));
                                }
                                else {
                                    cue.setPerformer(filter(t));
                                }
                            }
                        }
                        else if (token.equals("title")) {
                            if (st.hasMoreTokens()) {
                                String t = st.nextToken();
                                while (st.hasMoreTokens()) {
                                    t += " " + st.nextToken();
                                }

                                if (currentTrack != null) {
                                    currentTrack.setTitle(filter(t));
                                }
                                else {
                                    cue.setTitle(filter(t));
                                }

                            }

                        }
                        else if (token.equals("track")) {

                            currentTrack = new Track();
                            currentTrack.setTrackNumber(trackNumber);

                            if (st.hasMoreTokens()) {
                                trackNumber = Math.max(trackNumber,
                                        Integer.parseInt(st.nextToken()));
                            }

                        }
                        else if (token.equals("index")) {
                            try {
                                int idx = Integer.parseInt(st.nextToken());
                                long smp1 = MSFstring2sector(st.nextToken()) * 588L;
                                long smp = toSampleRate(smp1);
                                _reporter.println("smp(588) = " + smp1 + ", smp = " + smp);
                                if (idx == 1) {

                                    if (currentTrack != null) {
                                        currentTrack.setStartSector(smp);
                                    }

                                }
                            }
                            catch (NoSuchElementException nse) {
                            }

                            if (currentTrack != null) {
                                cue.addTrack(trackNumber, currentTrack);
                                trackNumber++;
                            }
                            currentTrack = null;
                        }
                    }

                    // Next line
                    line = br.readLine().trim();
                }
                br.close();
            }
            catch (Exception e) {
            }

            cue.fillOutEndTrackSectors();

        } finally {
            fips.close();
        }

        return cue;
    }

    public long[][] parseManualCrop(String param, float samplingFrequency) {
        Vector<long[]> r = new Vector<long[]>();
        HashSet<Integer> set = new HashSet<Integer>();
        for (StringTokenizer tracks = new StringTokenizer(param, ",", false); tracks
                .hasMoreTokens();) {
            String trk = tracks.nextToken();
            StringTokenizer parts = new StringTokenizer(trk, ":-", false);
            if (parts.countTokens() == 3) {
                String nt = parts.nextToken().toLowerCase();
                int no;
                long si = 0, ee = 0;
                try {
                    no = Math.abs(Integer.parseInt(nt));
                    for (;;) {
                        Integer k = new Integer(no);
                        if (set.add(k))
                            break;
                        no++;
                    }
                    for (int pt = 2; pt <= 3; pt++) {
                        nt = parts.nextToken();
                        int z;
                        if (nt.indexOf('s') >= 0 || nt.indexOf('m') >= 0) {
                            if (nt.endsWith("s"))
                                nt = nt.substring(0, nt.length() - 1);
                            int ttt = nt.indexOf('m');
                            int mmm = 0;
                            if (ttt >= 0) {
                                String ms = nt.substring(0, ttt);
                                if (ms.length() > 0)
                                    mmm = Integer.parseInt(ms);
                                nt = nt.substring(ttt + 1);
                            }
                            float sss = Float.parseFloat(nt);
                            z = Math.round((mmm * 60 + sss) * samplingFrequency);
                        }
                        else
                            z = Math.abs(Integer.parseInt(nt));
                        if (pt == 2)
                            si = z;
                        else
                            ee = z;
                    }
                }
                catch (NumberFormatException nfe) {
                    _reporter.println("Error parsing custom track list");
                    return null;
                }
                r.add(new long[] { no, si, ee });
            }
            else {
                _reporter.println("Error parsing custom track list");
                return null;
            }
        }
        long[][] rr = new long[r.size()][];
        for (int i = 0; i < r.size(); i++) {
            rr[i] = r.elementAt(i);
        }
        return rr;
    }

    private void printHelp() {
        _reporter.println("Description:");
        _reporter.println("  This tool is able to do sample granular cutting of MP3 streams via");
        _reporter.println("  the LAME-Tag's delay/padding values. A player capable of properly");
        _reporter.println("  interpreting the LAME-Tag is needed in order to enjoy this tool.\n");
        _reporter.println("Syntax:");
        _reporter.println("  java -jar pcutmp3.jar [<options>] [<source-mp3-filename>]");
        _reporter.println("  (Default operation is scanning only)\n");
        _reporter.println("Available options:");
        _reporter.println("  --cue <cue-filename>     split source mp3 via cue sheet");
        _reporter.println("                           mp3 source can be omitted if it's already");
        _reporter.println("                           referenced by the CUE sheet");
        // _reporter.println(
        // "  --crop t:s-e[,t:s-e[..]] crop tracks manually, t = track#");
        // _reporter.println(
        // "                           s = start sample/time (inclusive)");
        // _reporter.println(
        // "                           e = end sample/time (exclusive)");
        // _reporter.println(
        // "                           Time is specified in [XXm]YY[.ZZ]s");
        // _reporter.println(
        // "                           for XX minutes and YY.ZZ seconds");
        _reporter.println("  --out <scheme>           specify custom naming scheme where");
        _reporter.println("                           %s = source filename (without extension)");
        _reporter.println("                           %n = track number (leading zero)");
        _reporter.println("                           %t = track title (from CUE sheet)");
        _reporter.println("                           %p = track performer (from CUE sheet)");
        _reporter.println("                           %a = album name (from CUE sheet)");
        _reporter.println("                           Default is \"" + DEFAULT_NAMING_SCHEME
                + "\"");
        _reporter.println("  --dir <directory>        specify destination directory");
        _reporter.println("                           Default is the current working directory");
        _reporter.println("  --album <albumname>      set album name (for ID3 tag)");
        _reporter.println("  --artist <artistname>    set artist name (for ID3 tag)");
        _reporter.println("\nNote:");
        _reporter.println("  Option parameters which contain space characters must be");
        _reporter.println("  enclosed via quotation marks (see examples).");
        _reporter.println("\nExamples:");
        _reporter.println("  java -jar pcutmp3.jar --cue something.cue --out \"%n - %t\"");
        _reporter.println("  java -jar pcutmp3.jar --crop 1:0-8000,2:88.23s-3m10s largefile.mp3");
        _reporter.println("");
        _reporter.println("Originally developed by Sebastian Gesemann.\n");
        _reporter.println("Maintained by Chris Banes");
        _reporter.println("\n  ID3v2 Support added by Chris Banes using the library JID3.\n"
                + "     http://jid3.blinkenlights.org/");
    }
}
