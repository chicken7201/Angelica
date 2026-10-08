package com.gtnewhorizons.angelica.client.font;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FontGlyphRangesTest {

    /** Recognizes every superscript digit while leaving ordinary digits, subscripts and signs alone. */
    @Test
    void recognizesOnlySuperscriptDigits() {
        for (char chr : "⁰¹²³⁴⁵⁶⁷⁸⁹".toCharArray()) {
            assertTrue(FontGlyphRanges.isSuperscriptDigit(chr));
        }
        for (char chr : "0123456789₀₁₂₃₄₅₆₇₈₉⁺⁻⁼⁽⁾ⁿⁱ".toCharArray()) {
            assertFalse(FontGlyphRanges.isSuperscriptDigit(chr));
        }
    }

    /** Verifies the GTNH subscript question mark uses the resource-pack glyph path. */
    @Test
    void recognizesGtnhSubscriptQuestionMark() {
        assertTrue(FontGlyphRanges.isGtnhPrivateUseGlyph('\uE020'));
    }

    /** Verifies both ends of the GTNH private-use range are included. */
    @Test
    void includesGtnhPrivateUseBoundaries() {
        assertTrue(FontGlyphRanges.isGtnhPrivateUseGlyph('\uE000'));
        assertTrue(FontGlyphRanges.isGtnhPrivateUseGlyph('\uE0FF'));
    }

    /** Verifies characters outside the GTNH private-use range keep normal font selection. */
    @Test
    void excludesCharactersOutsideGtnhPrivateUseRange() {
        assertFalse(FontGlyphRanges.isGtnhPrivateUseGlyph('\uDFFF'));
        assertFalse(FontGlyphRanges.isGtnhPrivateUseGlyph('\uE100'));
        assertFalse(FontGlyphRanges.isGtnhPrivateUseGlyph('\u00B2'));
    }
}
