package com.gtnewhorizons.angelica.client.font;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;

/** Separates Java2D glyph ink bounds, bearings, advance, and atlas padding. */
final class CustomGlyphMetrics {

    static final float MINECRAFT_LINE_HEIGHT = 8.0F;

    final GlyphVector glyphVector;
    final Rectangle bitmapBounds;
    final int ascent;
    final int descent;
    final int lineHeight;
    final int baselineFromLineTop;
    final int atlasPadding;
    final float pixelScale;
    final float advanceX;
    final float drawOffsetX;
    final float drawOffsetY;
    final float drawWidth;
    final float drawHeight;

    /** Captures immutable source and screen-space metrics for one Java2D glyph. */
    private CustomGlyphMetrics(GlyphVector glyphVector, Rectangle bitmapBounds, int ascent, int descent,
        int lineHeight, int atlasPadding, float advanceX) {
        this.glyphVector = glyphVector;
        this.bitmapBounds = bitmapBounds;
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
        this.baselineFromLineTop = lineHeight - descent;
        this.atlasPadding = atlasPadding;
        this.pixelScale = MINECRAFT_LINE_HEIGHT / lineHeight;
        this.advanceX = advanceX * this.pixelScale;
        this.drawOffsetX = (bitmapBounds.x - atlasPadding) * this.pixelScale;
        this.drawOffsetY = (bitmapBounds.y - atlasPadding) * this.pixelScale;
        this.drawWidth = getAtlasWidth() * this.pixelScale;
        this.drawHeight = getAtlasHeight() * this.pixelScale;
    }

    /** Reads one glyph's baseline-relative pixel bounds and independent horizontal advance from Java2D. */
    static CustomGlyphMetrics create(Font font, FontRenderContext context, char chr, int ascent, int descent,
        int lineHeight, int atlasPadding) {
        if (lineHeight <= 0) {
            throw new IllegalArgumentException("Custom font line height must be positive");
        }
        if (atlasPadding < 0) {
            throw new IllegalArgumentException("Custom font atlas padding must not be negative");
        }

        final GlyphVector glyphVector = font.createGlyphVector(context, new char[] { chr });
        final GlyphMetrics glyphMetrics = glyphVector.getGlyphMetrics(0);
        final Rectangle bitmapBounds = glyphVector.getGlyphPixelBounds(0, context, 0.0F, 0.0F);
        return new CustomGlyphMetrics(
            glyphVector,
            bitmapBounds,
            ascent,
            descent,
            lineHeight,
            atlasPadding,
            glyphMetrics.getAdvanceX());
    }

    /** Returns the padded atlas width without changing the glyph's advance. */
    int getAtlasWidth() {
        return Math.max(1, this.bitmapBounds.width + 2 * this.atlasPadding);
    }

    /** Returns the padded atlas height without changing the glyph's baseline offset. */
    int getAtlasHeight() {
        return Math.max(1, this.bitmapBounds.height + 2 * this.atlasPadding);
    }
}
