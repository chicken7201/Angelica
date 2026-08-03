package com.gtnewhorizons.angelica.client.font;

final class UnicodeGlyphMetrics {

    static final int GLYPH_COUNT = 65536;
    static final int ATLAS_SIZE = 256;
    static final int CELL_SIZE = 16;
    static final float TEXEL_EDGE_GUARD = 0.02F;

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

    /** Returns the number of bitmap columns occupied by the glyph. */
    static float getBitmapWidth(byte packedBounds) {
        if (!isAvailable(packedBounds)) return 0.0F;
        return getEndColumnExclusive(packedBounds) - getStartColumn(packedBounds);
    }

    /** Returns the glyph bitmap's left UV without removing edge texels. */
    static float getUStart(char chr, byte packedBounds) {
        return (getCellLeft(chr) + getStartColumn(packedBounds)) / (float) ATLAS_SIZE;
    }

    /** Returns the glyph bitmap's top UV without removing edge texels. */
    static float getVStart(char chr) {
        return getCellTop(chr) / (float) ATLAS_SIZE;
    }

    /** Returns the glyph bitmap's UV width while avoiding the next texel boundary. */
    static float getUSize(byte packedBounds) {
        final float bitmapWidth = getBitmapWidth(packedBounds);
        return bitmapWidth == 0.0F ? 0.0F : (bitmapWidth - TEXEL_EDGE_GUARD) / ATLAS_SIZE;
    }

    /** Returns the glyph cell's UV height while avoiding the next cell boundary. */
    static float getVSize() {
        return (CELL_SIZE - TEXEL_EDGE_GUARD) / ATLAS_SIZE;
    }

    /** Returns the on-screen glyph quad width independently of its advance. */
    static float getGlyphWidth(byte packedBounds) {
        final float bitmapWidth = getBitmapWidth(packedBounds);
        return bitmapWidth == 0.0F ? 1.0F : (bitmapWidth - TEXEL_EDGE_GUARD) / 2.0F + 1.0F;
    }

    /** Returns the cursor advance encoded by glyph_sizes.bin. */
    static float getXAdvance(byte packedBounds) {
        final float bitmapWidth = getBitmapWidth(packedBounds);
        return bitmapWidth == 0.0F ? 0.0F : bitmapWidth / 2.0F + 1.0F;
    }

    /** Returns the left edge of the atlas cell allowed for shader samples. */
    static float getSampleUStart(char chr) {
        return getCellLeft(chr) / (float) ATLAS_SIZE;
    }

    /** Returns the right edge of the atlas cell allowed for shader samples. */
    static float getSampleUEnd(char chr) {
        return (getCellLeft(chr) + CELL_SIZE - TEXEL_EDGE_GUARD) / ATLAS_SIZE;
    }

    /** Returns the top edge of the atlas cell allowed for shader samples. */
    static float getSampleVStart(char chr) {
        return getCellTop(chr) / (float) ATLAS_SIZE;
    }

    /** Returns the bottom edge of the atlas cell allowed for shader samples. */
    static float getSampleVEnd(char chr) {
        return (getCellTop(chr) + CELL_SIZE - TEXEL_EDGE_GUARD) / ATLAS_SIZE;
    }

    /** Returns the left pixel coordinate of the glyph's fixed atlas cell. */
    private static int getCellLeft(char chr) {
        return chr % 16 * CELL_SIZE;
    }

    /** Returns the top pixel coordinate of the glyph's fixed atlas cell. */
    private static int getCellTop(char chr) {
        return (chr & 255) / 16 * CELL_SIZE;
    }
}
