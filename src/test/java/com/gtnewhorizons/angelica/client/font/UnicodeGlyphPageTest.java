package com.gtnewhorizons.angelica.client.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class UnicodeGlyphPageTest {

    private static final int PAGE_SIZE = 256;
    private static final int CELL_SIZE = PAGE_SIZE / UnicodeGlyphPage.GRID_SIZE;
    private static final float EPSILON = 0.000001F;
    private static final String REQUIRED_GLYPHS = "0123456789OreABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        + "₀₁₂₃₄₅₆₇₈₉H₂SO₄U₂₃₈◀◁▶▷❄";

    /** Verifies actual resource-pack pixels replace narrower stale glyph_sizes.bin bounds. */
    @Test
    void derivesWideGlyphMetricsFromActualPixels() {
        final BufferedImage image = newPage(PAGE_SIZE);
        fillGlyph(image, '0', 0, 10, 2, 14, 0xFFFFFFFF);
        final UnicodeGlyphPage page = UnicodeGlyphPage.compose(Collections.singletonList(image));
        final int glyph = '0' & 255;

        assertTrue(page.isGlyphAvailable(glyph));
        assertEquals(10, page.getBitmapWidth(glyph));
        assertEquals(6.0F, page.getXAdvance(glyph), EPSILON);
        assertEquals(5.99F, page.getGlyphWidth(glyph), EPSILON);
        assertEquals(cellLeft(glyph) / (float) PAGE_SIZE, page.getUStart(glyph), EPSILON);
        assertTrue(page.getUStart(glyph) + page.getUSize(glyph)
            >= (cellLeft(glyph) + 9.5F) / PAGE_SIZE);
    }

    /** Verifies a transparent high-priority cell retains the visible lower-priority fallback glyph. */
    @Test
    void fallsBackWhenHighestPriorityGlyphCellIsEmpty() {
        final BufferedImage lower = newPage(PAGE_SIZE);
        final BufferedImage higher = newPage(PAGE_SIZE);
        fillGlyph(lower, '❄', 0, 15, 1, 14, 0xFFFFFFFF);
        final UnicodeGlyphPage page = UnicodeGlyphPage.compose(Arrays.asList(lower, higher));
        final int glyph = '❄' & 255;
        final BufferedImage composed = page.takeImage();

        assertTrue(page.isGlyphAvailable(glyph));
        assertEquals(15, page.getBitmapWidth(glyph));
        assertEquals(0xFFFFFFFF, composed.getRGB(cellLeft(glyph), cellTop(glyph) + 1));
    }

    /** Verifies a visible high-priority glyph fully replaces the lower glyph without leaking adjacent pixels. */
    @Test
    void replacesLowerGlyphWhenHighestPriorityCellIsVisible() {
        final BufferedImage lower = newPage(PAGE_SIZE);
        final BufferedImage higher = newPage(PAGE_SIZE);
        fillGlyph(lower, '₂', 0, 15, 1, 15, 0xFFFF0000);
        fillGlyph(higher, '₂', 0, 8, 3, 12, 0xFF0000FF);
        final UnicodeGlyphPage page = UnicodeGlyphPage.compose(Arrays.asList(lower, higher));
        final int glyph = '₂' & 255;
        final BufferedImage composed = page.takeImage();

        assertEquals(8, page.getBitmapWidth(glyph));
        assertEquals(0xFF0000FF, composed.getRGB(cellLeft(glyph), cellTop(glyph) + 3));
        assertEquals(0, composed.getRGB(cellLeft(glyph) + 10, cellTop(glyph) + 3));
    }

    /** Verifies lower-resolution fallback cells scale into the selected high-priority page without changing logical width. */
    @Test
    void preservesLogicalWidthAcrossPageResolutions() {
        final BufferedImage lower = newPage(256);
        final BufferedImage higher = newPage(512);
        fillGlyph(lower, '◀', 0, 14, 1, 15, 0xFFFFFFFF);
        final UnicodeGlyphPage page = UnicodeGlyphPage.compose(Arrays.asList(lower, higher));
        final int glyph = '◀' & 255;

        assertEquals(28, page.getBitmapWidth(glyph));
        assertEquals(8.0F, page.getXAdvance(glyph), EPSILON);
    }

    /** Verifies requested ordinary, subscript, formula, triangle, and snowflake cells include both edge texels. */
    @Test
    void keepsRequestedGlyphEdgesInsideUvAndSampleBounds() {
        final BufferedImage image = newPage(PAGE_SIZE);
        for (int i = 0; i < REQUIRED_GLYPHS.length(); i++) {
            fillGlyph(image, REQUIRED_GLYPHS.charAt(i), 0, CELL_SIZE, 0, CELL_SIZE, 0xFFFFFFFF);
        }
        final UnicodeGlyphPage page = UnicodeGlyphPage.compose(Collections.singletonList(image));

        for (int i = 0; i < REQUIRED_GLYPHS.length(); i++) {
            final char chr = REQUIRED_GLYPHS.charAt(i);
            final int glyph = chr & 255;
            final float firstTexelCenter = (cellLeft(glyph) + 0.5F) / PAGE_SIZE;
            final float lastTexelCenter = (cellLeft(glyph) + CELL_SIZE - 0.5F) / PAGE_SIZE;
            final float uEnd = page.getUStart(glyph) + page.getUSize(glyph);

            assertTrue(page.isGlyphAvailable(glyph), Character.toString(chr));
            assertTrue(page.getUStart(glyph) <= firstTexelCenter, Character.toString(chr));
            assertTrue(uEnd >= lastTexelCenter, Character.toString(chr));
            assertTrue(page.getSampleUStart(glyph) <= page.getUStart(glyph), Character.toString(chr));
            assertTrue(page.getSampleUEnd(glyph) >= uEnd, Character.toString(chr));
            assertEquals(9.0F, page.getXAdvance(glyph), EPSILON, Character.toString(chr));
        }
    }

    /** Verifies an atlas cell with no visible pixels is rejected as a missing glyph. */
    @Test
    void rejectsEmptyComposedGlyphs() {
        final UnicodeGlyphPage page = UnicodeGlyphPage.compose(Collections.singletonList(newPage(PAGE_SIZE)));

        assertFalse(page.isGlyphAvailable('❄' & 255));
        assertEquals(0.0F, page.getXAdvance('❄' & 255), EPSILON);
    }

    /** Creates a transparent square Unicode page at the requested resolution. */
    private static BufferedImage newPage(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }

    /** Fills a glyph-relative rectangle with one ARGB color. */
    private static void fillGlyph(BufferedImage image, char chr, int left, int right, int top, int bottom,
        int argb) {
        final int glyph = chr & 255;
        final int cellSize = image.getWidth() / UnicodeGlyphPage.GRID_SIZE;
        final int glyphLeft = glyph % UnicodeGlyphPage.GRID_SIZE * cellSize;
        final int glyphTop = glyph / UnicodeGlyphPage.GRID_SIZE * cellSize;

        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                image.setRGB(glyphLeft + x, glyphTop + y, argb);
            }
        }
    }

    /** Returns one glyph cell's left coordinate in the 256-pixel test page. */
    private static int cellLeft(int glyph) {
        return glyph % UnicodeGlyphPage.GRID_SIZE * CELL_SIZE;
    }

    /** Returns one glyph cell's top coordinate in the 256-pixel test page. */
    private static int cellTop(int glyph) {
        return glyph / UnicodeGlyphPage.GRID_SIZE * CELL_SIZE;
    }
}
