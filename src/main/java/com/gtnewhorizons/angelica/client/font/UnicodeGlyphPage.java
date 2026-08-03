package com.gtnewhorizons.angelica.client.font;

import java.awt.image.BufferedImage;
import java.util.List;

final class UnicodeGlyphPage {

    static final int GRID_SIZE = 16;
    static final int PAGE_GLYPH_COUNT = GRID_SIZE * GRID_SIZE;
    static final int LOGICAL_CELL_SIZE = 16;
    static final float TEXEL_EDGE_GUARD = 0.02F;

    private BufferedImage image;
    private final int imageWidth;
    private final int imageHeight;
    private final int cellWidth;
    private final int cellHeight;
    private final short[] glyphLeft = new short[PAGE_GLYPH_COUNT];
    private final short[] glyphRight = new short[PAGE_GLYPH_COUNT];

    /** Creates metrics for one composed Unicode texture page. */
    private UnicodeGlyphPage(BufferedImage image) {
        validateDimensions(image);
        this.image = image;
        this.imageWidth = image.getWidth();
        this.imageHeight = image.getHeight();
        this.cellWidth = this.imageWidth / GRID_SIZE;
        this.cellHeight = this.imageHeight / GRID_SIZE;
        scanGlyphBounds();
    }

    /** Composes resource-pack pages from lowest to highest priority, preserving lower non-empty fallback cells. */
    static UnicodeGlyphPage compose(List<BufferedImage> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("A Unicode page requires at least one image layer");
        }

        for (BufferedImage layer : layers) {
            validateDimensions(layer);
        }

        final BufferedImage highestPriorityLayer = layers.get(layers.size() - 1);
        final int targetWidth = highestPriorityLayer.getWidth();
        final int targetHeight = highestPriorityLayer.getHeight();
        final int targetCellWidth = targetWidth / GRID_SIZE;
        final int targetCellHeight = targetHeight / GRID_SIZE;
        final int[] composedPixels = new int[targetWidth * targetHeight];

        for (BufferedImage layer : layers) {
            overlayNonEmptyGlyphs(layer, composedPixels, targetWidth, targetCellWidth, targetCellHeight);
        }

        final BufferedImage composed = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        composed.setRGB(0, 0, targetWidth, targetHeight, composedPixels, 0, targetWidth);
        return new UnicodeGlyphPage(composed);
    }

    /** Validates that an image can be divided into the fixed 16-by-16 Unicode glyph grid. */
    private static void validateDimensions(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
            || image.getWidth() % GRID_SIZE != 0 || image.getHeight() % GRID_SIZE != 0) {
            throw new IllegalArgumentException("Unicode font pages must have positive dimensions divisible by 16");
        }
    }

    /** Replaces each target cell whose source cell contains at least one visible pixel. */
    private static void overlayNonEmptyGlyphs(BufferedImage source, int[] targetPixels, int targetWidth,
        int targetCellWidth, int targetCellHeight) {
        final int sourceWidth = source.getWidth();
        final int sourceHeight = source.getHeight();
        final int sourceCellWidth = sourceWidth / GRID_SIZE;
        final int sourceCellHeight = sourceHeight / GRID_SIZE;
        final int[] sourcePixels = source.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);

        for (int glyph = 0; glyph < PAGE_GLYPH_COUNT; glyph++) {
            if (hasVisiblePixel(sourcePixels, sourceWidth, sourceCellWidth, sourceCellHeight, glyph)) {
                copyGlyphCell(sourcePixels, sourceWidth, sourceCellWidth, sourceCellHeight, targetPixels,
                    targetWidth, targetCellWidth, targetCellHeight, glyph);
            }
        }
    }

    /** Returns whether a source glyph cell contains any non-transparent pixel. */
    private static boolean hasVisiblePixel(int[] pixels, int imageWidth, int cellWidth, int cellHeight, int glyph) {
        final int cellLeft = glyph % GRID_SIZE * cellWidth;
        final int cellTop = glyph / GRID_SIZE * cellHeight;

        for (int y = 0; y < cellHeight; y++) {
            final int rowOffset = (cellTop + y) * imageWidth + cellLeft;
            for (int x = 0; x < cellWidth; x++) {
                if ((pixels[rowOffset + x] >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Clears and copies one glyph cell, scaling nearest-neighbor when page resolutions differ. */
    private static void copyGlyphCell(int[] sourcePixels, int sourceWidth, int sourceCellWidth,
        int sourceCellHeight, int[] targetPixels, int targetWidth, int targetCellWidth, int targetCellHeight,
        int glyph) {
        final int sourceLeft = glyph % GRID_SIZE * sourceCellWidth;
        final int sourceTop = glyph / GRID_SIZE * sourceCellHeight;
        final int targetLeft = glyph % GRID_SIZE * targetCellWidth;
        final int targetTop = glyph / GRID_SIZE * targetCellHeight;

        for (int y = 0; y < targetCellHeight; y++) {
            final int sourceY = sourceTop + y * sourceCellHeight / targetCellHeight;
            final int targetRow = (targetTop + y) * targetWidth + targetLeft;
            for (int x = 0; x < targetCellWidth; x++) {
                final int sourceX = sourceLeft + x * sourceCellWidth / targetCellWidth;
                targetPixels[targetRow + x] = sourcePixels[sourceY * sourceWidth + sourceX];
            }
        }
    }

    /** Scans the composed alpha channel to cache each glyph's actual horizontal bitmap bounds. */
    private void scanGlyphBounds() {
        final int[] pixels = this.image.getRGB(0, 0, this.imageWidth, this.imageHeight, null, 0, this.imageWidth);

        for (int glyph = 0; glyph < PAGE_GLYPH_COUNT; glyph++) {
            final int cellLeft = glyph % GRID_SIZE * this.cellWidth;
            final int cellTop = glyph / GRID_SIZE * this.cellHeight;
            int left = this.cellWidth;
            int right = 0;

            for (int y = 0; y < this.cellHeight; y++) {
                final int rowOffset = (cellTop + y) * this.imageWidth + cellLeft;
                for (int x = 0; x < this.cellWidth; x++) {
                    if ((pixels[rowOffset + x] >>> 24) != 0) {
                        left = Math.min(left, x);
                        right = Math.max(right, x + 1);
                    }
                }
            }

            this.glyphLeft[glyph] = (short) left;
            this.glyphRight[glyph] = (short) right;
        }
    }

    /** Transfers the composed bitmap to the texture uploader without retaining a duplicate image in the metrics cache. */
    BufferedImage takeImage() {
        final BufferedImage result = this.image;
        this.image = null;
        return result;
    }

    /** Returns whether the composed glyph cell contains visible pixels. */
    boolean isGlyphAvailable(int glyph) {
        return this.glyphRight[glyph] > this.glyphLeft[glyph];
    }

    /** Returns the left UV of the actual visible glyph bitmap. */
    float getUStart(int glyph) {
        return (getCellLeft(glyph) + this.glyphLeft[glyph]) / (float) this.imageWidth;
    }

    /** Returns the top UV of the glyph's fixed atlas cell. */
    float getVStart(int glyph) {
        return getCellTop(glyph) / (float) this.imageHeight;
    }

    /** Returns the UV width of the actual glyph bitmap while retaining its last texel center. */
    float getUSize(int glyph) {
        final int bitmapWidth = getBitmapWidth(glyph);
        return bitmapWidth == 0 ? 0.0F : (bitmapWidth - TEXEL_EDGE_GUARD) / this.imageWidth;
    }

    /** Returns the UV height of one Unicode cell without sampling the next cell. */
    float getVSize() {
        return (this.cellHeight - TEXEL_EDGE_GUARD) / this.imageHeight;
    }

    /** Returns the screen quad width independently of cursor advance and atlas sample padding. */
    float getGlyphWidth(int glyph) {
        final float logicalBitmapWidth = getLogicalBitmapWidth(glyph);
        final float logicalGuard = TEXEL_EDGE_GUARD * LOGICAL_CELL_SIZE / this.cellWidth;
        return logicalBitmapWidth == 0.0F ? 1.0F : (logicalBitmapWidth - logicalGuard) / 2.0F + 1.0F;
    }

    /** Returns cursor advance derived from the composed bitmap rather than stale glyph_sizes.bin bounds. */
    float getXAdvance(int glyph) {
        final float logicalBitmapWidth = getLogicalBitmapWidth(glyph);
        return logicalBitmapWidth == 0.0F ? 0.0F : logicalBitmapWidth / 2.0F + 1.0F;
    }

    /** Returns the left boundary allowed for shader samples within this glyph cell. */
    float getSampleUStart(int glyph) {
        return getCellLeft(glyph) / (float) this.imageWidth;
    }

    /** Returns the right boundary allowed for shader samples without entering the adjacent cell. */
    float getSampleUEnd(int glyph) {
        return (getCellLeft(glyph) + this.cellWidth - TEXEL_EDGE_GUARD) / this.imageWidth;
    }

    /** Returns the top boundary allowed for shader samples within this glyph cell. */
    float getSampleVStart(int glyph) {
        return getCellTop(glyph) / (float) this.imageHeight;
    }

    /** Returns the bottom boundary allowed for shader samples without entering the adjacent cell. */
    float getSampleVEnd(int glyph) {
        return (getCellTop(glyph) + this.cellHeight - TEXEL_EDGE_GUARD) / this.imageHeight;
    }

    /** Returns the glyph's visible width in physical texture pixels. */
    int getBitmapWidth(int glyph) {
        return Math.max(0, this.glyphRight[glyph] - this.glyphLeft[glyph]);
    }

    /** Returns the glyph's visible width in the logical 16-pixel Unicode cell coordinate system. */
    private float getLogicalBitmapWidth(int glyph) {
        return getBitmapWidth(glyph) * (float) LOGICAL_CELL_SIZE / this.cellWidth;
    }

    /** Returns the glyph cell's left coordinate in physical texture pixels. */
    private int getCellLeft(int glyph) {
        return glyph % GRID_SIZE * this.cellWidth;
    }

    /** Returns the glyph cell's top coordinate in physical texture pixels. */
    private int getCellTop(int glyph) {
        return glyph / GRID_SIZE * this.cellHeight;
    }
}
