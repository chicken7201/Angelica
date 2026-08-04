package com.gtnewhorizons.angelica.client.font;

final class FontGlyphRanges {

    private static final char GTNH_PRIVATE_USE_START = '\uE000';
    private static final char GTNH_PRIVATE_USE_END = '\uE0FF';
    static final char UNICODE_SUBSCRIPT_DIGIT_START = '\u2080';
    static final char UNICODE_SUBSCRIPT_DIGIT_END = '\u2089';
    static final char GTNH_SUBSCRIPT_ZERO = '\uE01A';

    /** Prevents instantiation of this glyph range utility. */
    private FontGlyphRanges() {}

    /** Returns whether the character belongs to the GTNH resource-pack glyph range. */
    static boolean isGtnhPrivateUseGlyph(char chr) {
        return chr >= GTNH_PRIVATE_USE_START && chr <= GTNH_PRIVATE_USE_END;
    }
}
