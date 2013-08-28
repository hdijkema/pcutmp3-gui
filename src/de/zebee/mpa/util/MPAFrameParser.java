/*
 * Created on 20.02.2003
 *
 */
package de.zebee.mpa.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Sebastian Gesemann
 */
public class MPAFrameParser {

    public static final int MAX_MPAFRAME_SIZE = 2048;

    public static final int FILTER_MPEG1      = 0x0001;
    public static final int FILTER_MPEG2      = 0x0002;
    public static final int FILTER_MPEG25     = 0x0004;
    public static final int FILTER_LAYER1     = 0x0008;
    public static final int FILTER_LAYER2     = 0x0010;
    public static final int FILTER_LAYER3     = 0x0020;
    public static final int FILTER_32000HZ    = 0x0040;
    public static final int FILTER_44100HZ    = 0x0080;
    public static final int FILTER_48000HZ    = 0x0100;
    public static final int FILTER_MONO       = 0x0200;
    public static final int FILTER_STEREO     = 0x0400;

    public static int getMpegFilter(FrameHeader fh) {
        if (fh.isValid()) {
            switch (fh.getMpegID()) {
                case FrameHeader.MPEG1_ID:
                    return FILTER_MPEG1;
                case FrameHeader.MPEG2_ID:
                    return FILTER_MPEG2;
                case FrameHeader.MPEG25_ID:
                    return FILTER_MPEG25;
            }
        }
        return 0;
    }

    public static int getLayerFilter(FrameHeader fh) {
        if (fh.isValid()) {
            switch (fh.getLayerID()) {
                case FrameHeader.LAYER1_ID:
                    return FILTER_LAYER1;
                case FrameHeader.LAYER2_ID:
                    return FILTER_LAYER2;
                case FrameHeader.LAYER3_ID:
                    return FILTER_LAYER3;
            }
        }
        return 0;
    }

    public static int getSamplingrateFilter(FrameHeader fh) {
        if (fh.isValid()) {
            switch (fh.getSamplingrateIndex()) {
                case FrameHeader.SR_32000HZ:
                    return FILTER_32000HZ;
                case FrameHeader.SR_44100HZ:
                    return FILTER_44100HZ;
                case FrameHeader.SR_48000HZ:
                    return FILTER_48000HZ;
            }
        }
        return 0;
    }

    public static int getModeFilter(FrameHeader fh) {
        if (fh.isValid()) {
            if (fh.getChannels() == 1) {
                return FILTER_MONO;
            }
            else {
                return FILTER_STEREO;
            }
        }
        return 0;
    }

    public static int getFilterFor(FrameHeader fh) {
        return getMpegFilter(fh) | getModeFilter(fh) | getSamplingrateFilter(fh)
                | getLayerFilter(fh);
    }

    protected InputStream ips;
    protected JunkHandler junkh;
    protected int         masker;
    protected int         masked;
    protected int[]       headBuff = new int[4];

    public MPAFrameParser(InputStream ips) {
        this(ips, null);
    }

    public MPAFrameParser(InputStream ips, JunkHandler jh) {
        setInputStream(ips);
        setJunkHandler(jh);
    }

    public void setInputStream(InputStream ips) {
        this.ips = ips;
    }

    public void setJunkHandler(JunkHandler jh) {
        this.junkh = jh;
    }

    protected void setupFilter(int filter) {
        masker = 0xFFE00000;
        masked = 0xFFE00000;
        if ((filter & FILTER_MPEG1) != 0) {
            masker |= 0x00180000;
            masked |= 0x00180000;
        }
        else if ((filter & FILTER_MPEG2) != 0) {
            masker |= 0x00180000;
            masked |= 0x00100000;
        }
        if ((filter & FILTER_LAYER1) != 0) {
            masker |= 0x00060000;
            masked |= 0x00060000;
        }
        else if ((filter & FILTER_LAYER2) != 0) {
            masker |= 0x00060000;
            masked |= 0x00040000;
        }
        else if ((filter & FILTER_LAYER3) != 0) {
            masker |= 0x00060000;
            masked |= 0x00020000;
        }
        if ((filter & FILTER_32000HZ) != 0) {
            masker |= 0x00000C00;
            masked |= 0x00000800;
        }
        else if ((filter & FILTER_44100HZ) != 0) {
            masker |= 0x00000C00;
            masked |= 0x00000000;
        }
        else if ((filter & FILTER_48000HZ) != 0) {
            masker |= 0x00000C00;
            masked |= 0x00000400;
        }
        if ((filter & FILTER_MONO) != 0) {
            masker |= 0x000000C0;
            masked |= 0x000000C0;
        }
    }

    /**
     * tries to find the next MPEG Audio Frame, loads it into the destination
     * buffer (including 32bit header) and returns a FrameHeader object. will
     * block until data is available. will throw EOFException of any other
     * IOException created by the InputStream object. set filter to 0 or to any
     * other value using the FILTER_xxx flags to force a specific frame type.
     * 
     * @param filter
     * @param destBuffer
     * @return FrameHeader
     * @throws IOException
     */
    public FrameHeader getNextFrame(int filter, byte[] destBuffer) throws IOException {
        return getNextFrame(filter, destBuffer, null);
    }

    /**
     * tries to find the next MPEG Audio Frame, loads it into the destination
     * buffer (including 32bit header) and returns a FrameHeader object. (If
     * destFH is non-null, that object will be used to store the header infos)
     * will block until data is available. will throw EOFException of any other
     * IOException created by the InputStream object. set filter to 0 or to any
     * other value using the FILTER_xxx flags to force a specific frame type.
     * 
     * @param filter
     * @param destBuffer
     * @param destFH
     * @return FrameHeader
     * @throws IOException
     */
    public FrameHeader getNextFrame(int filter, byte[] destBuffer, FrameHeader destFH)
            throws IOException {
        setupFilter(filter);
        for (int z = 0; z < 4; z++)
            headBuff[z] = 0;
        int hbPos = 0;
        FrameHeader fh = (destFH == null) ? new FrameHeader() : destFH;
        int tmp;
        int skipped = -4;
        while (true) {
            tmp = ips.read();
            if (tmp == -1) { // EOF ?
                if (junkh != null) {
                    for (int i = 0; i < 4; i++) { // flush headBuff
                        if (skipped >= 0)
                            junkh.write(headBuff[hbPos]);
                        skipped++;
                        hbPos = (hbPos + 1) & 3;
                    }
                    junkh.endOfJunkBlock();
                }
                throw new EOFException();
            }
            if ((junkh != null) && (skipped >= 0)) {
                junkh.write(headBuff[hbPos]);
            }
            headBuff[hbPos] = tmp;
            skipped++;
            hbPos = (hbPos + 1) & 3;
            if (headBuff[hbPos] == 0xFF) { // might be the beginning of a
                                           // sync-word
                int header32 = headBuff[hbPos];
                for (int z = 1; z < 4; z++) {
                    header32 <<= 8;
                    header32 |= headBuff[(hbPos + z) & 3];
                }
                if ((header32 & masker) == masked) { // seems to be a frame
                                                     // header
                    fh.setHeader32(header32);
                    if (fh.isValid()
                            && (((filter & FILTER_STEREO) == 0) || (fh.getChannels() == 2))) {
                        int offs;
                        for (offs = 0; offs < 4; offs++)
                            destBuffer[offs] = (byte) headBuff[(hbPos + offs) & 3];
                        tmp = fh.getFrameSize() - offs;
                        int result = ips.read(destBuffer, offs, tmp);
                        if (result == tmp) {
                            if (junkh != null) {
                                junkh.endOfJunkBlock();
                            }
                            return fh;
                        }
                        else {
                            if (junkh != null) {
                                result += 4; // inklusive header
                                for (int z = 0; z < result; z++) {
                                    junkh.write((destBuffer[z]) & 0xFF);
                                }
                                junkh.endOfJunkBlock();
                            }
                            throw new EOFException();
                        }
                    }
                }
            }
        }
    }

}
