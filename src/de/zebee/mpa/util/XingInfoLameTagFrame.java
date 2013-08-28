/*
 * Created on 08.07.2005
 */
package de.zebee.mpa.util;

import java.util.Arrays;

/**
 * @author Sebastian Gesemann
 */
public class XingInfoLameTagFrame {

    private int     frameSize  = 0;
    private byte[]  bb         = new byte[MPAFrameParser.MAX_MPAFRAME_SIZE];
    private boolean xingTag    = false;
    private boolean infoTag    = false;
    private boolean lameTag    = false;
    private int     lameTagOfs = 0;                                         // starting
                                                                             // at
                                                                             // VBR
                                                                             // scale
                                                                             // of
                                                                             // XingTag
                                                                             // !
    private int     encDelay;
    private int     encPadding;

    public boolean isValid() {
        return frameSize > 0;
    }

    public void clear() {
        frameSize = 0;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public boolean xingTagPresent() {
        return xingTag;
    }

    public boolean infoTagPresent() {
        return infoTag;
    }

    public boolean lameTagPresent() {
        return lameTag;
    }

    public int getEncDelay() {
        return encDelay;
    }

    public int getEncPadding() {
        return encPadding;
    }

    public void export(byte[] dest, int dofs) {
        System.arraycopy(bb, 0, dest, dofs, frameSize);
    }

    @Override
    public String toString() {
        if (frameSize == 0)
            return "";
        StringBuffer sb = new StringBuffer();
        sb.append(xingTag ? "Xing" : "Info");
        if (lameTag) {
            sb.append("+LAME");
        }
        sb.append(" tag");
        return sb.toString();
    }

    public boolean parse(byte[] data, int ofs) {
        final int origOfs = ofs;
        int fh32 = (data[ofs] << 24) | ((data[ofs + 1] & 0xFF) << 16)
                | ((data[ofs + 2] & 0xFF) << 8) | (data[ofs + 3] & 0xFF);
        FrameHeader fh = new FrameHeader(fh32);
        ofs += 4 + fh.getSideInfoSize();
        boolean xingTag = (data[ofs] == 0x58) // 'Xing'
                && (data[ofs + 1] == 0x69) && (data[ofs + 2] == 0x6E) && (data[ofs + 3] == 0x67);
        boolean infoTag = (data[ofs] == 0x49) // 'Info'
                && (data[ofs + 1] == 0x6E) && (data[ofs + 2] == 0x66) && (data[ofs + 3] == 0x6F);
        if (!xingTag && !infoTag) {
            return false;
        }
        ofs += 4;
        this.xingTag = xingTag;
        this.infoTag = infoTag;
        this.frameSize = fh.getFrameSize();
        System.arraycopy(data, origOfs, bb, 0, frameSize);
        int flags = data[ofs + 3] & 0xFF;
        ofs += 4;
        if ((flags & 0x01) != 0)
            ofs += 4; // skip frame count
        if ((flags & 0x02) != 0)
            ofs += 4; // skip byte count
        if ((flags & 0x04) != 0)
            ofs += 100; // skip seek table
        if ((flags & 0x08) != 0)
            ofs += 4; // skip VBR scale
        int tagEndOfs = ofs + 0x24;
        int crc = 0;
        for (int i = origOfs; i < tagEndOfs - 2; i++) {
            crc = CRC16.updateLAME(crc, data[i] & 0xFF);
        }
        this.lameTag = (data[ofs] == 0x4C) && (data[ofs + 1] == 0x41) && (data[ofs + 2] == 0x4D)
                && (data[ofs + 3] == 0x45);
        if (!this.lameTag) {
            this.lameTag = (data[ofs] == 0x47) && (data[ofs + 1] == 0x4F)
                    && (data[ofs + 2] == 0x47) && (data[ofs + 3] == 0x4F);
        }
        this.lameTag |= ((((data[tagEndOfs - 2] << 8) | (data[tagEndOfs - 1] & 0xFF)) ^ crc) & 0xFFFF) == 0;
        if (this.lameTag) {
            lameTagOfs = ofs - origOfs - 4;
        }
        ofs += 0x15;
        int t = data[ofs + 1] & 0xFF;
        encDelay = ((data[ofs] & 0xFF) << 4) | (t >>> 4);
        encPadding = ((t & 0x0F) << 8) | (data[ofs + 2] & 0xFF);
        if (!this.lameTag) {
            if (encDelay > 2880 || encPadding > 2304) {
                encDelay = 576;
                encPadding = 0;
            }
        }
        return true;
    }

    public static int MASK_ATH_KILL_NO_GAP_START = 0x7F;
    public static int MASK_ATH_KILL_NO_GAP_END   = 0xBF;

    public static int createHeaderFrame(FrameHeader toBeSimilar, boolean vbr, float kbps,
            int frameCount, int musicBytes, int vbrScale, byte[] seektable, int encDelay,
            int encPadding, XingInfoLameTagFrame srcTag, byte[] dest, final int dofs,
            final int maskATH) {
        int fh32 = toBeSimilar.getHeader32() | 0x00010000; // disable CRC if any
        int frameSize = 0;
        int tagOffset = 0;
        { // calculate optimal header frame size
            FrameHeader tmp = new FrameHeader();
            float minDist = 9999;
            for (int i = 1; i < 15; i++) {
                int th32 = (fh32 & 0xFFFF0FFF) | (i << 12);
                tmp.setHeader32(th32);
                if (tmp.getFrameSize() >= 0xC0) {
                    int ikbps = tmp.getBitrateKBPS();
                    float dist = Math.abs(kbps - ikbps);
                    if (dist < minDist) {
                        minDist = dist;
                        fh32 = th32;
                        frameSize = tmp.getFrameSize();
                        tagOffset = tmp.getSideInfoSize() + 4;
                    }
                }
            }
        }
        tagOffset += dofs;
        Arrays.fill(dest, dofs, dofs + frameSize, (byte) 0);
        dest[dofs] = (byte) (fh32 >>> 24);
        dest[dofs + 1] = (byte) (fh32 >>> 16);
        dest[dofs + 2] = (byte) (fh32 >>> 8);
        dest[dofs + 3] = (byte) fh32;
        if (vbr) {
            dest[tagOffset++] = (byte) 0x58; // X
            dest[tagOffset++] = (byte) 0x69; // i
            dest[tagOffset++] = (byte) 0x6E; // n
            dest[tagOffset++] = (byte) 0x67; // g
        }
        else {
            dest[tagOffset++] = (byte) 0x49; // I
            dest[tagOffset++] = (byte) 0x6E; // n
            dest[tagOffset++] = (byte) 0x66; // f
            dest[tagOffset++] = (byte) 0x6F; // o
        }
        dest[tagOffset++] = 0;
        dest[tagOffset++] = 0;
        dest[tagOffset++] = 0;
        dest[tagOffset++] = 0x0F;
        dest[tagOffset++] = (byte) (frameCount >>> 24);
        dest[tagOffset++] = (byte) (frameCount >>> 16);
        dest[tagOffset++] = (byte) (frameCount >>> 8);
        dest[tagOffset++] = (byte) frameCount;
        int temp = frameSize + musicBytes;
        dest[tagOffset++] = (byte) (temp >>> 24);
        dest[tagOffset++] = (byte) (temp >>> 16);
        dest[tagOffset++] = (byte) (temp >>> 8);
        dest[tagOffset++] = (byte) temp;
        System.arraycopy(seektable, 0, dest, tagOffset, 100);
        tagOffset += 100;
        dest[tagOffset++] = (byte) (vbrScale >>> 24);
        dest[tagOffset++] = (byte) (vbrScale >>> 16);
        dest[tagOffset++] = (byte) (vbrScale >>> 8);
        dest[tagOffset++] = (byte) vbrScale;
        if (srcTag != null && srcTag.isValid() && srcTag.lameTagPresent()) {
            System.arraycopy(srcTag.bb, srcTag.lameTagOfs, dest, tagOffset - 4, 40);
            tagOffset += 4;
            // delete LAME's replaygain tag
            for (int i = 0; i < 8; i++) {
                dest[tagOffset + 0x07 + i] = 0;
            }
            // deleting no-gap flags ...
            dest[tagOffset + 0x0F] &= maskATH;
        }
        else {
            dest[tagOffset++] = (byte) 0x4C; // L
            dest[tagOffset++] = (byte) 0x41; // A
            dest[tagOffset++] = (byte) 0x4D; // M
            dest[tagOffset++] = (byte) 0x45; // E
        }
        encDelay = Math.max(0, Math.min(encDelay, 4095));
        encPadding = Math.max(0, Math.min(encPadding, 4095));
        // write encDelay / encPadding ...
        dest[tagOffset + 0x11] = (byte) (encDelay >>> 4);
        dest[tagOffset + 0x12] = (byte) ((encDelay & 0xF) << 4 | (encPadding >>> 8));
        dest[tagOffset + 0x13] = (byte) encPadding;
        temp = frameSize + musicBytes;
        dest[tagOffset + 0x18] = (byte) (temp >>> 24);
        dest[tagOffset + 0x19] = (byte) (temp >>> 16);
        dest[tagOffset + 0x1A] = (byte) (temp >>> 8);
        dest[tagOffset + 0x1B] = (byte) temp;
        int crc = 0;
        for (int i = 0; i < 190; i++) {
            crc = CRC16.updateLAME(crc, dest[dofs + i] & 0xFF);
        }
        dest[tagOffset + 0x1E] = (byte) (crc >>> 8);
        dest[tagOffset + 0x1F] = (byte) crc;
        return frameSize;
    }

}
