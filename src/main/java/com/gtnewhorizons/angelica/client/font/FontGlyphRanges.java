package com.gtnewhorizons.angelica.client.font;

final class FontGlyphRanges {

    private static final char GTNH_PRIVATE_USE_START = '\uE000';
    private static final char GTNH_PRIVATE_USE_END = '\uE0FF';
    static final char UNICODE_SUBSCRIPT_DIGIT_START = '\u2080';
    static final char UNICODE_SUBSCRIPT_DIGIT_END = '\u2089';
    static final char GTNH_SUBSCRIPT_ZERO = '\uE01A';
    static final String SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    /** Prevents instantiation of this glyph range utility. */
    private FontGlyphRanges() {}

    /** Returns whether the character belongs to the GTNH resource-pack glyph range. */
    static boolean isGtnhPrivateUseGlyph(char chr) {
        return chr >= GTNH_PRIVATE_USE_START && chr <= GTNH_PRIVATE_USE_END;
    }

    /** Recognizes the ten superscript digits split across the Latin-1 and U+20xx pages. */
    static boolean isSuperscriptDigit(char chr) {
        return chr == '⁰' || (chr >= '⁴' && chr <= '⁹') || chr == '¹' || chr == '²' || chr == '³';
    }
}
