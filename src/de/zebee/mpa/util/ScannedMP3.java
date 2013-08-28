/*
 * Created on 08.07.2005
 */
package de.zebee.mpa.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Vector;

/**
 * @author Sebastian Gesemann
 */
public class ScannedMP3 {

    public static final long     UNKNOWN_START_SAMPLE = -(1L << 42);

    // FRame REcord Size (in bytes)
    private static final int     FRRES                = 4 + 2 + 2 + 2;
    // Frame Record Count Per Page
    private static final int     FRCPP                = 0x2000 / FRRES;
    // Byte Buffer Size Per Page
    private static final int     BBSPP                = FRCPP * FRRES;

    private FrameHeader          firstFrameHeader     = null;
    private XingInfoLameTagFrame xiltFrame            = new XingInfoLameTagFrame();
    private boolean              isVBR                = false;
    private float                avgBitrate           = 0;
    private int                  maxRes               = 0;
    private int                  encDelay             = 576;
    private int                  encPadding           = 576 * 3;
    private Vector<byte[]>       byteBuffers          = new Vector<byte[]>();       ;
    private byte[]               currBB               = null;
    private int                  currBBofs            = 0;
    private int                  musicFrameCount      = 0;
    private int                  samplesPerFrame      = 0;
    private long                 startSample          = UNKNOWN_START_SAMPLE;

    @Override
    public String toString() {
        boolean accurate = false;
        String s = "first frame header = " + firstFrameHeader.toString() + "\n";
        if (xiltFrame != null && xiltFrame.isValid()) {
            s += "Xing/Info";
            if (xiltFrame.lameTagPresent()) {
                s += " and LAME";
                accurate = true;
            }
        }
        else {
            s += "no Xing/Info/LAME";
        }
        s += " tag present\n";
        s += "bitrate = ";
        if (isVBR) {
            s += avgBitrate + " kbps (VBR)\n";
        }
        else {
            s += Math.round(avgBitrate) + " kbps (CBR)\n";
        }
        s += "accurate length = " + (accurate ? "yes" : "no") + "\n";
        s += this.getSampleCount() + " samples";
        if ((this.getSampleCount() % 588) == 0) {
            s += " (is a multiple of 588)";
        }
        else {
            s += " (is NOT a multiple of 588)";
        }
        return s;
    }

    private void accessFrameRecord(int idx) {
        int page = idx / FRCPP;
        while (page >= byteBuffers.size()) {
            byteBuffers.add(null);
        }
        byte[] bb = byteBuffers.get(page);
        if (bb == null) {
            bb = new byte[BBSPP];
            byteBuffers.set(page, bb);
        }
        currBB = bb;
        currBBofs = (idx % FRCPP) * FRRES;
    }

    private static int getInt16(byte[] bb, int ofs) {
        return (bb[ofs] << 8) | (bb[ofs + 1] & 0xFF);
    }

    private static int getInt32(byte[] bb, int ofs) {
        return (getInt16(bb, ofs) << 16) | (getInt16(bb, ofs + 2) & 0xFFFF);
    }

    private static void setInt16(byte[] bb, int ofs, int i) {
        bb[ofs] = (byte) (i >>> 8);
        bb[ofs + 1] = (byte) i;
    }

    private static void setInt32(byte[] bb, int ofs, int i) {
        setInt16(bb, ofs, i >>> 16);
        setInt16(bb, ofs + 2, i);
    }

    private int getFrameFileOfs() {
        return getInt32(currBB, currBBofs);
    }

    private void setFrameFileOfs(int offset) {
        setInt32(currBB, currBBofs, offset);
    }

    private int getBitResPtr() {
        return getInt16(currBB, currBBofs + 4);
    }

    private void setBitResPtr(int brptr) {
        setInt16(currBB, currBBofs + 4, brptr);
    }

    private int getFrameSize() {
        return getInt16(currBB, currBBofs + 6);
    }

    private void setFrameSize(int fs) {
        setInt16(currBB, currBBofs + 6, fs);
    }

    private int getMainDataSectionSize() {
        return getInt16(currBB, currBBofs + 8);
    }

    private void setMainDataSectionSize(int mdss) {
        setInt16(currBB, currBBofs + 8, mdss);
    }

    private final class MyCountingJunkHandler implements JunkHandler {
        private int offset = 0;

        public void write(int bite) throws IOException {
            offset++;
        }

        public void inc(int i) {
            offset += i;
        }

        public void endOfJunkBlock() throws IOException {
        }
    }

    public ScannedMP3(InputStream ips) throws IOException {
        try {
            byte[] temp = new byte[MPAFrameParser.MAX_MPAFRAME_SIZE];
            MyCountingJunkHandler jh = new MyCountingJunkHandler();
            MPAFrameParser mpafp = new MPAFrameParser(ips, jh);
            int filter = MPAFrameParser.FILTER_LAYER3;
            FrameHeader fh = null;
            int frameCounter = 0;
            boolean firstFrameFound = false;
            boolean isMPEG1 = false;
            int firstkbps = 0;
            int sumMusicFrameSize = 0;
            try {
                for (;;) {
                    fh = mpafp.getNextFrame(filter, temp, fh);
                    int frameSize = fh.getFrameSize();
                    if (!firstFrameFound) {
                        firstFrameFound = true;
                        firstkbps = fh.getBitrateKBPS();
                        samplesPerFrame = fh.getSamplesPerFrame();
                        isMPEG1 = fh.getMpegID() == FrameHeader.MPEG1_ID;
                        maxRes = isMPEG1 ? 511 : 255;
                        filter = MPAFrameParser.getFilterFor(fh);
                        firstFrameHeader = new FrameHeader(fh.getHeader32());
                        if (xiltFrame.parse(temp, 0)) {
                            if (xiltFrame.xingTagPresent())
                                isVBR = true;
                            frameCounter--;
                            if (xiltFrame.lameTagPresent()) {
                                encDelay = xiltFrame.getEncDelay();
                                encPadding = xiltFrame.getEncPadding();
                            }
                        }
                    }
                    else {
                        boolean checkBitRate = true;
                        if (frameCounter == 0) {
                            checkBitRate = false;
                            // first music frame. might be a PCUT-tag
                            // reservoir-filler frame
                            int sie = fh.getSideInfoEnd();
                            // a pcut frame contains its tag in the first 10
                            // bytes of the
                            // main data section
                            boolean pcutFrame = (sie + 10 <= frameSize) && (temp[sie] == 0x50) // P
                                    && (temp[sie + 1] == 0x43) // C
                                    && (temp[sie + 2] == 0x55) // U
                                    && (temp[sie + 3] == 0x54); // T
                            if (pcutFrame) {
                                // temp[sie+4] tag revision (always 0 for now)
                                long t = temp[sie + 5]; // fetch 40 bit start
                                                        // sample
                                t = (t << 8) | (temp[sie + 6] & 0xFF);
                                t = (t << 8) | (temp[sie + 7] & 0xFF);
                                t = (t << 8) | (temp[sie + 8] & 0xFF);
                                t = (t << 8) | (temp[sie + 9] & 0xFF);
                                startSample = t;
                            }
                            else {
                                for (int o = fh.getSideInfoStart(), e = fh.getSideInfoEnd(); o < e; o++) {
                                    if (temp[o] != 0) {
                                        checkBitRate = true;
                                        break;
                                    }
                                }
                            }
                        }
                        // we don't want the first "music frame" to be checked
                        // if it's
                        // possibly a PCUT generated reservoir frame
                        if (checkBitRate && fh.getBitrateKBPS() != firstkbps)
                            isVBR = true;
                    }
                    if (frameCounter >= 0) {
                        sumMusicFrameSize += frameSize;
                        accessFrameRecord(frameCounter);
                        setFrameFileOfs(jh.offset);
                        setFrameSize(frameSize);
                        final int sis = fh.getSideInfoStart();
                        int ofs = sis;
                        int brPointer = temp[ofs] & 0xFF;
                        if (isMPEG1) {
                            brPointer = (brPointer << 1) | ((temp[ofs + 1] & 0x80) >>> 7);
                        }
                        setBitResPtr(brPointer);
                        setMainDataSectionSize(frameSize - sis - fh.getSideInfoSize());
                    }
                    jh.inc(frameSize);
                    frameCounter++;
                }
            }
            catch (EOFException x) {
            }
            musicFrameCount = frameCounter;
            if (firstFrameFound) {
                float framerate = ((float) firstFrameHeader.getSamplingrateHZ())
                        / firstFrameHeader.getSamplesPerFrame();
                avgBitrate = (sumMusicFrameSize / musicFrameCount) * framerate / 125.f;
            }
            if (!firstFrameFound) {
                throw new IOException("no mp3 data found");
            }
        } finally {
            try {
                ips.close();
            }
            catch (IOException x) {
            }
        }
    }

    public int getSamplingFrequency() {
        if (firstFrameHeader == null)
            return 44100;
        return firstFrameHeader.getSamplingrateHZ();
    }

    public long getSampleCount() {
        return (long) musicFrameCount * samplesPerFrame - encDelay - encPadding;
    }

    private static final int minOverlapSamplesStart = 576;
    private static final int minOverlapSamplesEnd   = 1152;

    public void crop(long startSample, long endSample, InputStream ips, OutputStream ops)
            throws IOException {
        try {
            startSample = Math.max(startSample, -encDelay);
            endSample = Math.min(endSample, getSampleCount() + encPadding);
            final int maskATH;
            {
                int t = 0xFF;
                if (startSample != 0)
                    t &= XingInfoLameTagFrame.MASK_ATH_KILL_NO_GAP_START;
                if (endSample != getSampleCount())
                    t &= XingInfoLameTagFrame.MASK_ATH_KILL_NO_GAP_END;
                maskATH = t;
            }
            int firstFrameInclusive = Math.max(0,
                    (int) ((startSample + encDelay - minOverlapSamplesStart) / samplesPerFrame));
            int lastFrameExclusive = Math.min(musicFrameCount, (int) ((endSample + encDelay
                    + minOverlapSamplesEnd + samplesPerFrame - 1) / samplesPerFrame));
            int newEncDelay = encDelay
                    + (int) (startSample - firstFrameInclusive * samplesPerFrame);
            int newEncPadding = (int) ((long) (lastFrameExclusive - firstFrameInclusive)
                    * samplesPerFrame - newEncDelay - (endSample - startSample));
            accessFrameRecord(firstFrameInclusive);
            final int needBytesFromReservoir = getBitResPtr();
            int gotBytesFromReservoir = 0;
            int needPreFrames = 0;
            while (firstFrameInclusive - needPreFrames > 0
                    && needBytesFromReservoir > gotBytesFromReservoir && newEncDelay + 1152 <= 4095) {
                needPreFrames++;
                accessFrameRecord(firstFrameInclusive - needPreFrames);
                gotBytesFromReservoir += getMainDataSectionSize();
            }
            byte[] resFrame = null;
            int resFrameSize = 0;
            int firstFrameNum = firstFrameInclusive;
            if (needPreFrames == 0) {
                // force writing of PCUT tag frame
                needPreFrames = 1;
            }
            if (needPreFrames > 0) {
                firstFrameNum--;
                newEncDelay += samplesPerFrame;
                resFrame = new byte[MPAFrameParser.MAX_MPAFRAME_SIZE];
                long newAbsStartSample = startSample;
                if (this.startSample != UNKNOWN_START_SAMPLE) {
                    newAbsStartSample += this.startSample;
                }
                resFrameSize = constructReservoirFrame(resFrame, firstFrameHeader,
                        needBytesFromReservoir, newAbsStartSample);
            }
            byte[] seektable = new byte[100];
            float avgBytesPerFrame;
            float avgBytesPerSecnd;
            float avgkbps;
            int musiLen = 0;
            { // calculate seek table
                int ofs00, ofsXX;
                accessFrameRecord(firstFrameInclusive);
                ofs00 = getFrameFileOfs() - resFrameSize;
                accessFrameRecord(Math.max(0, lastFrameExclusive - 1));
                ofsXX = getFrameFileOfs() + getFrameSize();
                musiLen = ofsXX - ofs00;
                avgBytesPerFrame = (float) (ofsXX - ofs00)
                        / (lastFrameExclusive - firstFrameInclusive);
                avgBytesPerSecnd = avgBytesPerFrame * firstFrameHeader.getSamplingrateHZ()
                        / firstFrameHeader.getSamplesPerFrame();
                avgkbps = avgBytesPerSecnd / 125.f;
                for (int i = 0; i < 100; i++) {
                    int fidx = Math.round(firstFrameInclusive + (i + 1.f) / 101
                            * (lastFrameExclusive - firstFrameInclusive));
                    accessFrameRecord(Math.max(0, fidx));
                    seektable[i] = (byte) Math.round((getFrameFileOfs() - ofs00) * 255.f
                            / (ofsXX - ofs00));
                }
            }
            byte[] frameBuff = new byte[MPAFrameParser.MAX_MPAFRAME_SIZE];
            int fl = XingInfoLameTagFrame.createHeaderFrame(firstFrameHeader, isVBR, avgkbps,
                    lastFrameExclusive - firstFrameNum, musiLen, 50, seektable, newEncDelay,
                    newEncPadding, xiltFrame, frameBuff, 0, maskATH);
            ops.write(frameBuff, 0, fl);
            int filepos = 0;
            int sideInfoSize = firstFrameHeader.getSideInfoSize();
            int bitRes = 0;
            if (needPreFrames > 0) {
                byte[] reservoir = new byte[511];
                if (needBytesFromReservoir > 0) {
                    for (int fi = firstFrameInclusive - needPreFrames; fi < firstFrameInclusive; fi++) {
                        accessFrameRecord(fi);
                        int tmp = getFrameFileOfs();
                        ips.skip(tmp - filepos);
                        filepos = tmp;
                        fl = getFrameSize();
                        readFully(ips, frameBuff, 0, fl);
                        filepos += fl;
                        int mdss = getMainDataSectionSize();
                        if (mdss >= 511) {
                            System.arraycopy(frameBuff, fl - 511, reservoir, 0, 511);
                        }
                        else {
                            int move = 511 - mdss;
                            System.arraycopy(reservoir, 511 - move, reservoir, 0, move);
                            System.arraycopy(frameBuff, fl - mdss, reservoir, move, mdss);
                        }
                    }
                    System.arraycopy(reservoir, 511 - needBytesFromReservoir, resFrame,
                            resFrameSize - needBytesFromReservoir, needBytesFromReservoir);
                }
                ops.write(resFrame, 0, resFrameSize);
                bitRes = needBytesFromReservoir;
            }
            for (int fi = firstFrameInclusive; fi < lastFrameExclusive; fi++) {
                accessFrameRecord(fi);
                int tmp = getFrameFileOfs();
                ips.skip(tmp - filepos);
                filepos = tmp;
                fl = getFrameSize();
                readFully(ips, frameBuff, 0, fl);
                filepos += fl;
                tmp = getBitResPtr();
                if (tmp > bitRes) {
                    silenceFrame(frameBuff, 0, sideInfoSize);
                }
                ops.write(frameBuff, 0, fl);
                tmp = getMainDataSectionSize();
                bitRes = Math.min(bitRes + tmp, maxRes);
            }
        } finally {
            try {
                ips.close();
            }
            catch (IOException x) {
            }
        }
    }

    private static int constructReservoirFrame(byte[] dest, FrameHeader header, int minResSize,
            long absStartSample) {
        // increase for 10-byte-header inclusion
        minResSize += 10;
        int h32 = header.getHeader32() | 0x00010000; // switch off CRC usage
        FrameHeader fh2 = new FrameHeader();
        for (int bri = 1; bri <= 14; bri++) {
            h32 = (h32 & 0xFFFF0FFF) + (bri << 12);
            fh2.setHeader32(h32);
            final int frameSize = fh2.getFrameSize();
            final int sideInfoEnd = fh2.getSideInfoEnd();
            final int mainDataBlockSize = frameSize - sideInfoEnd;
            if (mainDataBlockSize >= minResSize) {
                dest[0] = (byte) (h32 >>> 24);
                dest[1] = (byte) (h32 >>> 16);
                dest[2] = (byte) (h32 >>> 8);
                dest[3] = (byte) h32;
                Arrays.fill(dest, 4, sideInfoEnd, (byte) 0);
                Arrays.fill(dest, sideInfoEnd, frameSize, (byte) 0x78);
                dest[sideInfoEnd] = 0x50; // P
                dest[sideInfoEnd + 1] = 0x43; // C
                dest[sideInfoEnd + 2] = 0x55; // U
                dest[sideInfoEnd + 3] = 0x54; // T
                dest[sideInfoEnd + 4] = 0; // revision 0
                dest[sideInfoEnd + 5] = (byte) (absStartSample >>> 32); // absolute
                                                                        // sample
                                                                        // start
                                                                        // pos
                dest[sideInfoEnd + 6] = (byte) (absStartSample >>> 24); // absolute
                                                                        // sample
                                                                        // start
                                                                        // pos
                dest[sideInfoEnd + 7] = (byte) (absStartSample >>> 16); // absolute
                                                                        // sample
                                                                        // start
                                                                        // pos
                dest[sideInfoEnd + 8] = (byte) (absStartSample >>> 8); // absolute
                                                                       // sample
                                                                       // start
                                                                       // pos
                dest[sideInfoEnd + 9] = (byte) absStartSample; // absolute
                                                               // sample start
                                                               // pos
                return frameSize;
            }
        }
        return -1;
    }

    private void silenceFrame(byte[] data, int ofs, int sisize) {
        int siend = 4 + sisize;
        boolean crcProtection = ((data[ofs + 1] & 1) == 0);
        if (crcProtection)
            siend += 2;
        Arrays.fill(data, ofs + 4, ofs + siend, (byte) 0);
        if (crcProtection) {
            int crc16 = 0xFFFF;
            crc16 = CRC16.updateMPEG(crc16, data[ofs + 2]);
            crc16 = CRC16.updateMPEG(crc16, data[ofs + 3]);
            for (int o2 = 6; o2 < siend;) {
                crc16 = CRC16.updateMPEG(crc16, data[ofs + o2]);
            }
            data[ofs + 4] = (byte) (crc16 >>> 8);
            data[ofs + 5] = (byte) crc16;
        }
    }

    private static void readFully(InputStream ips, byte[] dest, int dofs, int len)
            throws IOException {
        while (len > 0) {
            int res = ips.read(dest, dofs, len);
            if (res < 0)
                throw new EOFException();
            dofs += res;
            len -= res;
        }
    }

}
