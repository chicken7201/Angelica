package com.gtnewhorizons.angelica.client.font;

final class FontGlyphRanges {

    private static final char GTNH_PRIVATE_USE_START = '\uE000';
    private static final char GTNH_PRIVATE_USE_END = '\uE0FF';

    /** Prevents instantiation of this glyph range utility. */
    private FontGlyphRanges() {}

    /** Returns whether the character belongs to the GTNH resource-pack glyph range. */
    static boolean isGtnhPrivateUseGlyph(char chr) {
        return chr >= GTNH_PRIVATE_USE_START && chr <= GTNH_PRIVATE_USE_END;
    }
}
