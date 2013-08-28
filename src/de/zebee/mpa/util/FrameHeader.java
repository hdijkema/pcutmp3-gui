package de.zebee.mpa.util;

/**
 * @author Sebastian Gesemann
 */
public class FrameHeader {

    // -----[ static stuff ]-----

    public static final int       MAX_MPAFRAME_SIZE      = 0x700;

    public static final int       ILLEGAL_MPEG_ID        = 1;
    public static final int       MPEG1_ID               = 3;
    public static final int       MPEG2_ID               = 2;
    public static final int       MPEG25_ID              = 0;
    public static final int       ILLEGAL_LAYER_ID       = 0;
    public static final int       LAYER1_ID              = 3;
    public static final int       LAYER2_ID              = 2;
    public static final int       LAYER3_ID              = 1;
    public static final int       ILLEGAL_SR             = 3;
    public static final int       SR_32000HZ             = 2;
    public static final int       SR_44100HZ             = 0;
    public static final int       SR_48000HZ             = 1;
    public static final int       MODE_MONO              = 3;
    public static final int       MODE_DUAL              = 2;
    public static final int       MODE_JOINT             = 1;
    public static final int       MODE_STEREO            = 0;

    public static final String[]  MPEG_NAME              = { "MPEG2.5", null, "MPEG2", "MPEG1" };
    public static final String[]  LAYER_NAME             = { null, "Layer3", "Layer2", "Layer1" };
    public static final String[]  MODE_NAME              = { "Stereo", "J-Stereo", "Dual", "Mono" };
    public static final int[]     SAMPLING_RATES         = { 44100, 48000, 32000, 0 };

    private static final int[]    BITRATE_MPEG1_LAYER1   = { 0, 32, 64, 96, 128, 160, 192, 224,
            256, 288, 320, 352, 384, 416, 448           };
    private static final int[]    BITRATE_MPEG1_LAYER2   = { 0, 32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384                };
    private static final int[]    BITRATE_MPEG1_LAYER3   = { 0, 32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320                };
    private static final int[]    BITRATE_MPEG2_LAYER1   = { 0, 32, 48, 56, 64, 80, 96, 112, 128,
            144, 160, 176, 192, 224, 256                };
    private static final int[]    BITRATE_MPEG2_LAYER2A3 = { 0, 8, 16, 24, 32, 40, 48, 56, 64, 80,
            96, 112, 128, 144, 160                      };

    /**
     * [mpegID][layerID][bitrateIndex] -> bitrate in kbps, 0=free-format (no
     * support)
     */
    public static final int[][][] BITRATE_MAP            = { // 
                                                         // invalid, layer3,
                                                         // layer2, layer1
            { null, BITRATE_MPEG2_LAYER2A3, BITRATE_MPEG2_LAYER2A3, BITRATE_MPEG2_LAYER1 }, // MPEG2.5
            null, { null, BITRATE_MPEG2_LAYER2A3, BITRATE_MPEG2_LAYER2A3, BITRATE_MPEG2_LAYER1 }, // MPEG2
            { null, BITRATE_MPEG1_LAYER3, BITRATE_MPEG1_LAYER2, BITRATE_MPEG1_LAYER1 }, // MPEG1
                                                         };

    // -----[ object variables ]-----

    private int                   header32;
    private int                   mpegID;
    private int                   layerID;
    private boolean               crc16used;
    private int                   bitrateIndex;
    private int                   samplingrateIndex;
    private boolean               padding;
    private boolean               privateBitSet;
    private int                   mode;
    private int                   modeExtension;
    private boolean               copyrighted;
    private boolean               original;
    private int                   emphasis;

    private boolean               valid;

    private int                   samplingrateHz;
    private int                   channels;
    private int                   bitrateKBPS;
    private int                   samplesPerFrame;
    private int                   bytesPerSlot;
    private int                   frameSize;

    // -----[ object methods ]-----

    public FrameHeader() {
        this.header32 = 0;
        valid = false;
    }

    public FrameHeader(int header32) {
        this.header32 = header32;
        decode();
    }

    public void setHeader32(int header32) {
        this.header32 = header32;
        decode();
    }

    private void decode() {
        mpegID = (header32 >> 19) & 3;
        layerID = (header32 >> 17) & 3;
        crc16used = (header32 & 0x00010000) == 0;
        bitrateIndex = (header32 >> 12) & 0xF;
        samplingrateIndex = (header32 >> 10) & 3;
        padding = (header32 & 0x00000200) != 0;
        privateBitSet = (header32 & 0x00000100) != 0;
        mode = (header32 >> 6) & 3;
        modeExtension = (header32 >> 4) & 3;
        copyrighted = (header32 & 0x00000008) != 0;
        original = (header32 & 0x00000004) == 0; // bit set -> copy
        emphasis = header32 & 3;
        valid = (mpegID != ILLEGAL_MPEG_ID) && (layerID != ILLEGAL_LAYER_ID) && (bitrateIndex != 0)
                && (bitrateIndex != 15) && (samplingrateIndex != ILLEGAL_SR);
        if (valid) {
            samplingrateHz = SAMPLING_RATES[samplingrateIndex];
            if (mpegID == MPEG2_ID)
                samplingrateHz >>= 1; // 16,22,48 kHz
            if (mpegID == MPEG25_ID)
                samplingrateHz >>= 2; // 8,11,24 kHz
            channels = (mode == MODE_MONO) ? 1 : 2;
            bitrateKBPS = BITRATE_MAP[mpegID][layerID][bitrateIndex];
            if (layerID == LAYER1_ID) {
                // layer 1: always 384 samples/frame and 4byte-slots
                samplesPerFrame = 384;
                bytesPerSlot = 4;
            }
            else {
                // layer 2: always 1152 samples/frame
                // layer 3: MPEG1: 1152 samples/frame, MPEG2/2.5: 576
                // samples/frame
                samplesPerFrame = ((mpegID == MPEG1_ID) || (layerID == LAYER2_ID)) ? 1152 : 576;
                bytesPerSlot = 1;
            }
            frameSize = ((bitrateKBPS * 125) * samplesPerFrame) / samplingrateHz;
            if (bytesPerSlot > 1)
                frameSize -= frameSize % bytesPerSlot;
            if (padding)
                frameSize += bytesPerSlot;
        }
    }

    public int getBitrateIndex() {
        return bitrateIndex;
    }

    public boolean isCopyrighted() {
        return copyrighted;
    }

    public boolean isCrc16used() {
        return crc16used;
    }

    public int getEmphasis() {
        return emphasis;
    }

    public int getHeader32() {
        return header32;
    }

    public int getLayerID() {
        return layerID;
    }

    public int getMode() {
        return mode;
    }

    public int getModeExtension() {
        return modeExtension;
    }

    public int getMpegID() {
        return mpegID;
    }

    public boolean isOriginal() {
        return original;
    }

    public boolean isPadding() {
        return padding;
    }

    public boolean isPrivateBitSet() {
        return privateBitSet;
    }

    public int getSamplingrateIndex() {
        return samplingrateIndex;
    }

    public boolean isValid() {
        return valid;
    }

    public int getBitrateKBPS() {
        return bitrateKBPS;
    }

    public int getChannels() {
        return channels;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getSamplesPerFrame() {
        return samplesPerFrame;
    }

    public int getSamplingrateHZ() {
        return samplingrateHz;
    }

    public int getSideInfoStart() {
        return isCrc16used() ? 6 : 4;
    }

    public int getSideInfoSize() {
        if (mpegID == MPEG1_ID) {
            return getChannels() == 2 ? 32 : 17;
        }
        else {
            return getChannels() == 2 ? 17 : 9;
        }
    }

    public int getSideInfoEnd() {
        return getSideInfoStart() + getSideInfoSize();
    }

    @Override
    public String toString() {
        if (!valid)
            return "invalid";
        StringBuffer sb = new StringBuffer();
        sb.append(MPEG_NAME[mpegID]);
        sb.append(' ');
        sb.append(LAYER_NAME[layerID]);
        sb.append(' ');
        sb.append(bitrateKBPS);
        sb.append("kbps ");
        sb.append(samplingrateHz);
        sb.append("Hz ");
        sb.append(MODE_NAME[mode]);
        return sb.toString();
    }

}
