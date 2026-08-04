package com.gtnewhorizons.angelica.client.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class CustomGlyphMetricsTest {

    private static final float EPSILON = 0.000001F;

    /** Verifies atlas padding expands only the quad and never changes the original pen advance. */
    @Test
    void separatesAtlasPaddingFromAdvance() {
        final Font font = new Font(Font.DIALOG, Font.PLAIN, 30);
        final Graphics2D graphics = createGraphics(font);
        try {
            final int lineHeight = graphics.getFontMetrics().getHeight();
            final CustomGlyphMetrics narrowPadding = createMetrics(graphics, font, 'F', 1);
            final CustomGlyphMetrics widePadding = createMetrics(graphics, font, 'F', 4);

            assertEquals(narrowPadding.advanceX, widePadding.advanceX, EPSILON);
            assertEquals(6.0F * narrowPadding.pixelScale,
                widePadding.drawWidth - narrowPadding.drawWidth, EPSILON);
            assertEquals(-3.0F * narrowPadding.pixelScale,
                widePadding.drawOffsetX - narrowPadding.drawOffsetX, EPSILON);
            assertEquals(CustomGlyphMetrics.MINECRAFT_LINE_HEIGHT / lineHeight,
                narrowPadding.pixelScale, EPSILON);
        } finally {
            graphics.dispose();
        }
    }

    /** Verifies the padded quad maps the original Java2D x/y bearings back to the shared screen baseline. */
    @Test
    void preservesBearingAroundSharedBaseline() {
        final Font font = new Font(Font.DIALOG, Font.PLAIN, 30);
        final Graphics2D graphics = createGraphics(font);
        try {
            final CustomGlyphMetrics metrics = createMetrics(graphics, font, '(', 3);
            final float customScale = 1.5F;
            final float baselineY = 7.0F;
            final float paddingOnScreen = metrics.atlasPadding * metrics.pixelScale * customScale;
            final float quadX = metrics.drawOffsetX * customScale;
            final float quadY = baselineY + metrics.drawOffsetY * customScale;

            assertEquals(metrics.bitmapBounds.x * metrics.pixelScale * customScale,
                quadX + paddingOnScreen, EPSILON);
            assertEquals(baselineY + metrics.bitmapBounds.y * metrics.pixelScale * customScale,
                quadY + paddingOnScreen, EPSILON);
        } finally {
            graphics.dispose();
        }
    }

    /** Verifies Java2D's own subscript and superscript bearings survive without codepoint-specific offsets. */
    @Test
    void preservesScriptVerticalRelationship() {
        final Font font = new Font(Font.DIALOG, Font.PLAIN, 30);
        final Graphics2D graphics = createGraphics(font);
        try {
            assertTrue(font.canDisplay('3'));
            assertTrue(font.canDisplay('₃'));
            assertTrue(font.canDisplay('³'));
            final CustomGlyphMetrics ordinary = createMetrics(graphics, font, '3', 2);
            final CustomGlyphMetrics subscript = createMetrics(graphics, font, '₃', 2);
            final CustomGlyphMetrics superscript = createMetrics(graphics, font, '³', 2);
            final double ordinaryCenter = ordinary.bitmapBounds.getCenterY();

            assertTrue(subscript.bitmapBounds.getCenterY() > ordinaryCenter);
            assertTrue(superscript.bitmapBounds.getCenterY() < ordinaryCenter);
        } finally {
            graphics.dispose();
        }
    }

    /** Verifies actual raster pixels remain inside every side of the allocated padded atlas cell. */
    @Test
    void keepsRasterInsidePaddedAtlasCell() {
        final Font font = new Font(Font.DIALOG, Font.PLAIN, 30);
        final Graphics2D metricsGraphics = createGraphics(font);
        try {
            assertRasterInsideCell(createMetrics(metricsGraphics, font, 'F', 2), font);
            assertRasterInsideCell(createMetrics(metricsGraphics, font, '(', 2), font);
            assertRasterInsideCell(createMetrics(metricsGraphics, font, '₃', 2), font);
            assertRasterInsideCell(createMetrics(metricsGraphics, font, '³', 2), font);
        } finally {
            metricsGraphics.dispose();
        }
    }

    /** Draws one glyph with production positioning and checks that no visible pixel reaches the cell boundary. */
    private static void assertRasterInsideCell(CustomGlyphMetrics metrics, Font font) {
        final BufferedImage image = new BufferedImage(
            metrics.getAtlasWidth(),
            metrics.getAtlasHeight(),
            BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        graphics.drawGlyphVector(
            metrics.glyphVector,
            metrics.atlasPadding - metrics.bitmapBounds.x,
            metrics.atlasPadding - metrics.bitmapBounds.y);
        graphics.dispose();

        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        assertTrue(maxX >= minX);
        assertTrue(maxY >= minY);
        assertTrue(minX >= metrics.atlasPadding);
        assertTrue(minY >= metrics.atlasPadding);
        assertTrue(maxX < image.getWidth() - metrics.atlasPadding);
        assertTrue(maxY < image.getHeight() - metrics.atlasPadding);
    }

    /** Creates metrics with the same ascent, descent, and line height used by the production atlas. */
    private static CustomGlyphMetrics createMetrics(Graphics2D graphics, Font font, char chr, int padding) {
        return CustomGlyphMetrics.create(
            font,
            graphics.getFontRenderContext(),
            chr,
            graphics.getFontMetrics().getAscent(),
            graphics.getFontMetrics().getDescent(),
            graphics.getFontMetrics().getHeight(),
            padding);
    }

    /** Creates a deterministic Java2D context for glyph metric tests. */
    private static Graphics2D createGraphics(Font font) {
        final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        return graphics;
    }
}
