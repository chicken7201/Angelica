package com.gtnewhorizons.angelica.client.font;

final class UnicodeGlyphMetrics {

    static final int GLYPH_COUNT = 65536;

    /** Prevents instantiation of this Unicode glyph metrics utility. */
    private UnicodeGlyphMetrics() {}

    /** Returns whether packed glyph bounds describe a non-empty bitmap. */
    static boolean isAvailable(byte packedBounds) {
        return (packedBounds & 0xFF) != 0 && getEndColumnExclusive(packedBounds) > getStartColumn(packedBounds);
    }

    /** Decodes the inclusive left bitmap column from glyph_sizes.bin. */
    static int getStartColumn(byte packedBounds) {
        return (packedBounds >>> 4) & 15;
    }

    /** Decodes the exclusive right bitmap column from glyph_sizes.bin. */
    static int getEndColumnExclusive(byte packedBounds) {
        return (packedBounds & 15) + 1;
    }

    /** Returns the horizontal bitmap width declared by glyph_sizes.bin. */
    static int getBitmapWidth(byte packedBounds) {
        return isAvailable(packedBounds) ? getEndColumnExclusive(packedBounds) - getStartColumn(packedBounds) : 0;
    }

}
