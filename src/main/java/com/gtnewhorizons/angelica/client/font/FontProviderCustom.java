package com.gtnewhorizons.angelica.client.font;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.util.RandomXoshiro256StarStar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.ColorModel;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Objects;

import static com.gtnewhorizons.angelica.client.font.FontStrategist.getFontName;

public final class FontProviderCustom implements FontProvider {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    static final int ATLAS_SIZE = 128;
    static final int ATLAS_COUNT = 512;
    private final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();
    private final FontAtlas[] fontAtlases = new FontAtlas[ATLAS_COUNT];
    private final int[] atlasTextures = new int[ATLAS_COUNT];
    private volatile Font font;

    /** Creates a CPU metric cache with textures deferred until drawing. */
    FontProviderCustom(Font font) {
        this.font = font;
    }

    /** Resolves the configured font at the requested raster quality. */
    private static Font configured(String fontName) {
        if (Objects.equals(fontName, "(none)")) {
            return null;
        }
        for (Font available : FontStrategist.getAvailableFonts()) {
            if (Objects.equals(fontName, getFontName(available))) {
                return available.deriveFont((float) FontConfig.customFontQuality);
            }
        }
        LOGGER.info("Could not find previously set font \"{}\". ", fontName);
        return null;
    }

    private static class InstLoader {
        static final FontProviderCustom instance0 = new FontProviderCustom(configured(FontConfig.customFontNamePrimary));
        static final FontProviderCustom instance1 = new FontProviderCustom(configured(FontConfig.customFontNameFallback));
    }
    /** Returns the lazily initialized primary font provider. */
    public static FontProviderCustom getPrimary() { return InstLoader.instance0; }
    /** Returns the lazily initialized fallback font provider. */
    public static FontProviderCustom getFallback() { return InstLoader.instance1; }

    /** Invalidates CPU metrics and retires textures when the selected font changes. */
    public void setFont(Font font) {
        synchronized (this) {
            this.font = font;
            Arrays.fill(this.fontAtlases, null);
        }

        for (int i = 0; i < ATLAS_COUNT; i++) {
            if (this.atlasTextures[i] != 0) {
                GLStateManager.glDeleteTextures(this.atlasTextures[i]);
                this.atlasTextures[i] = 0;
            }
        }
    }

    /** Reloads a selected font at the configured raster quality. */
    public void reloadFont(int fontID) {
        setFont(FontStrategist.getAvailableFonts()[fontID].deriveFont((float) FontConfig.customFontQuality));
    }

    static final class GlyphRenderInfo {

        final float uStart;
        final float vStart;
        final float xAdvance;
        final float drawOffsetX;
        final float drawOffsetY;
        final float drawWidth;
        final float drawHeight;
        final float uSize;
        final float vSize;
        final int texture;

        /** Captures one custom glyph's atlas UV, baseline-relative quad, and independent pen advance. */
        private GlyphRenderInfo(float uStart, float vStart, float xAdvance, float drawOffsetX, float drawOffsetY,
            float drawWidth, float drawHeight, float uSize, float vSize, int texture) {
            this.uStart = uStart;
            this.vStart = vStart;
            this.xAdvance = xAdvance;
            this.drawOffsetX = drawOffsetX;
            this.drawOffsetY = drawOffsetY;
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
            this.uSize = uSize;
            this.vSize = vSize;
            this.texture = texture;
        }
    }

    private static final class FontAtlas {
        static final int STRIDE = 9;
        static final int U_START = 0;
        static final int V_START = 1;
        static final int X_ADVANCE = 2;
        static final int GLYPH_W = 3;
        static final int U_SIZE = 4;
        static final int V_SIZE = 5;
        static final int OFFSET_X = 6;
        static final int OFFSET_Y = 7;
        static final int GLYPH_H = 8;

        final float[] metrics;
        final long availLo;
        final long availHi;

        /** Publishes immutable flat metrics and availability bits. */
        FontAtlas(float[] metrics, long availLo, long availHi) {
            this.metrics = metrics;
            this.availLo = availLo;
            this.availHi = availHi;
        }

        /** Checks whether this atlas can display a glyph slot. */
        boolean has(int slot) {
            return bit(this.availLo, this.availHi, slot);
        }

        /** Reads a glyph availability bit from the two-word mask. */
        static boolean bit(long lo, long hi, int slot) {
            return ((slot < 64 ? lo >>> slot : hi >>> (slot - 64)) & 1L) != 0L;
        }
    }

    private static final FontAtlas EMPTY = new FontAtlas(new float[ATLAS_SIZE * FontAtlas.STRIDE], 0L, 0L);

    /** Applies matching Java2D hints to measurement and rasterization. */
    private static void configureGraphics(Graphics2D graphics, Font font) {
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
    }

    /** Measures or rasterizes padded glyphs with identical bearings and pen advances. */
    private static BufferedImage layout(Font font, int id, long availLo, long availHi, float[] metricsOut) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        configureGraphics(g2d, font);
        final FontMetrics fm = g2d.getFontMetrics();
        final FontRenderContext fontRenderContext = g2d.getFontRenderContext();
        final int lineHeight = fm.getHeight();
        final int atlasPadding = Math.max(1, (int) Math.ceil(font.getSize2D() / 16.0F));
        final int atlasGap = Math.max(atlasPadding, (int) Math.ceil(font.getSize2D() / 3.0F));
        final CustomGlyphMetrics[] rasterMetrics = new CustomGlyphMetrics[ATLAS_SIZE];
        int atlasChars = 0;
        int maxCellHeight = 1;
        for (int i = 0; i < ATLAS_SIZE; i++) {
            final char ch = (char) (i + ATLAS_SIZE * id);
            if (!FontAtlas.bit(availLo, availHi, i)) {
                continue;
            }
            final CustomGlyphMetrics metrics = CustomGlyphMetrics.create(
                font,
                fontRenderContext,
                ch,
                fm.getAscent(),
                fm.getDescent(),
                lineHeight,
                atlasPadding);
            rasterMetrics[i] = metrics;
            maxCellHeight = Math.max(maxCellHeight, metrics.getAtlasHeight());
            atlasChars++;
        }
        g2d.dispose();
        if (atlasChars == 0) { return null; }

        final int atlasTilesX = (int) Math.ceil(Math.sqrt(atlasChars) * 1.5f);
        final int atlasTilesY = (int) Math.ceil((double) atlasChars / atlasTilesX);
        int rowWidth = atlasGap;
        int maxRowWidth = atlasGap;
        int charsInRow = 0;
        for (int i = 0; i < ATLAS_SIZE; i++) {
            final CustomGlyphMetrics metrics = rasterMetrics[i];
            if (metrics == null) {
                continue;
            }
            if (charsInRow >= atlasTilesX) {
                maxRowWidth = Math.max(maxRowWidth, rowWidth);
                rowWidth = atlasGap;
                charsInRow = 0;
            }
            rowWidth += metrics.getAtlasWidth() + atlasGap;
            charsInRow++;
        }
        maxRowWidth = Math.max(maxRowWidth, rowWidth);

        final int imageWidth = maxRowWidth;
        final int imageHeight = atlasGap + atlasTilesY * (maxCellHeight + atlasGap);

        final boolean rasterize = metricsOut == null;
        if (rasterize) {
            image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            g2d = image.createGraphics();
            configureGraphics(g2d, font);
        }

        int tileX = 0;
        int imgX = atlasGap;
        int imgY = atlasGap;

        for (int i = 0; i < ATLAS_SIZE; i++) {
            final CustomGlyphMetrics metrics = rasterMetrics[i];
            if (metrics == null) { continue; }

            if (tileX >= atlasTilesX) {
                tileX = 0;
                imgX = atlasGap;
                imgY += maxCellHeight + atlasGap;
            }

            final int atlasWidth = metrics.getAtlasWidth();
            final int atlasHeight = metrics.getAtlasHeight();
            final float baselineX = imgX + metrics.atlasPadding - metrics.bitmapBounds.x;
            final float baselineY = imgY + metrics.atlasPadding - metrics.bitmapBounds.y;
            if (rasterize) {
                g2d.drawGlyphVector(metrics.glyphVector, baselineX, baselineY);
            } else {
                final int o = i * FontAtlas.STRIDE;
                metricsOut[o + FontAtlas.U_START] = (float) imgX / imageWidth;
                metricsOut[o + FontAtlas.V_START] = (float) imgY / imageHeight;
                metricsOut[o + FontAtlas.X_ADVANCE] = metrics.advanceX;
                metricsOut[o + FontAtlas.GLYPH_W] = metrics.drawWidth;
                metricsOut[o + FontAtlas.U_SIZE] = (float) atlasWidth / imageWidth;
                metricsOut[o + FontAtlas.V_SIZE] = (float) atlasHeight / imageHeight;
                metricsOut[o + FontAtlas.OFFSET_X] = metrics.drawOffsetX;
                metricsOut[o + FontAtlas.OFFSET_Y] = metrics.drawOffsetY;
                metricsOut[o + FontAtlas.GLYPH_H] = metrics.drawHeight;
            }
            imgX += atlasWidth + atlasGap;
            tileX++;
        }
        if (rasterize) g2d.dispose();
        return rasterize ? image : null;
    }

    /** Returns published CPU metrics or builds the first-use atlas. */
    private FontAtlas getAtlas(char chr) {
        final FontAtlas fa = this.fontAtlases[chr / ATLAS_SIZE];
        return fa != null ? fa : buildAtlas(chr / ATLAS_SIZE);
    }

    /** Serializes first-use measurement without requiring a GL context. */
    private synchronized FontAtlas buildAtlas(int id) {
        FontAtlas fa = this.fontAtlases[id];
        if (fa != null) { return fa; }
        final Font f = this.font;
        if (f == null) { return EMPTY; }
        long availLo = 0L;
        long availHi = 0L;
        for (int i = 0; i < ATLAS_SIZE; i++) {
            if (f.canDisplay((char) (i + ATLAS_SIZE * id))) {
                if (i < 64) { availLo |= 1L << i; } else { availHi |= 1L << (i - 64); }
            }
        }
        if ((availLo | availHi) == 0L) {
            fa = EMPTY;
        } else {
            final float[] metrics = new float[ATLAS_SIZE * FontAtlas.STRIDE];
            layout(f, id, availLo, availHi, metrics);
            fa = new FontAtlas(metrics, availLo, availHi);
        }
        this.fontAtlases[id] = fa;
        return fa;
    }

    /** Rasterizes the measured atlas only when drawing needs its texture. */
    private synchronized BufferedImage rasterize(int id) {
        final Font f = this.font;
        final FontAtlas fa = this.fontAtlases[id];
        if (f == null || fa == null || fa == EMPTY) { return null; }
        return layout(f, id, fa.availLo, fa.availHi, null);
    }

    /** Uploads only on the draw path after CPU metrics have been published. */
    private int uploadAtlas(int id) {
        final BufferedImage image = rasterize(id);
        if (image == null) return 0;
        final int texture = uploadAtlas(image);
        this.atlasTextures[id] = texture;
        return texture;
    }

    /** Preserves texture binding and releases native staging memory after upload. */
    static int uploadAtlas(BufferedImage image) {
        return FontProviderUnicode.withPageTextureBindingPreserved(() -> {
            int id = GLStateManager.glGenTextures();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            final int width = image.getWidth();
            final int height = image.getHeight();
            final IntBuffer pixelBuffer = memAllocInt(width * height);
            try {
                long ptr = memAddress0(pixelBuffer);

                if (image.getRaster().getDataBuffer() instanceof DataBufferInt dataBufferInt) {
                    final int[] pixelValues = dataBufferInt.getData();

                    for (int i : pixelValues) {
                        memPutInt(ptr, i);
                        ptr += 4;
                    }

                    pixelBuffer.limit(pixelValues.length);
                } else {
                    Raster raster = image.getRaster();
                    ColorModel colorModel = image.getColorModel();

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            Object pixel = raster.getDataElements(x, y, null);
                            memPutInt(ptr, colorModel.getRGB(pixel));
                            ptr += 4;
                        }
                    }
                    pixelBuffer.limit(image.getHeight() * image.getWidth());
                }

                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, image.getWidth(), image.getHeight(), 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
                return id;
            } finally {
                memFree(pixelBuffer);
            }
        });
    }

    /** Checks font coverage using only the CPU metric cache. */
    @Override
    public boolean isGlyphAvailable(char chr) {
        if (this.font == null) { return false; }
        return getAtlas(chr).has(chr % ATLAS_SIZE);
    }

    /**
     * A random glyph of the same width, for {@code §k}. Kept inside the character's own
     * atlas: reaching outside one would measure and rasterize another atlas mid-frame.
     * Slots the font has no glyph for are skipped.
     */
    @Override
    public char getRandomReplacement(char chr) {
        if (this.font == null) {
            return chr;
        }
        final FontAtlas atlas = getAtlas(chr);
        if (!atlas.has(chr % ATLAS_SIZE)) {
            return chr;
        }
        final float targetAdvance = atlas.metrics[(chr % ATLAS_SIZE) * FontAtlas.STRIDE + FontAtlas.X_ADVANCE];
        final int atlasStart = (chr / ATLAS_SIZE) * ATLAS_SIZE;
        for (int attempt = 0; attempt < RANDOM_GLYPH_TRIES; attempt++) {
            final int slot = fontRandom.nextInt(ATLAS_SIZE);
            if (atlas.has(slot) && atlas.metrics[slot * FontAtlas.STRIDE + FontAtlas.X_ADVANCE] == targetAdvance) {
                return (char) (atlasStart + slot);
            }
        }
        return chr;
    }

    /** Returns the fork glyph quad from flat CPU metrics and lazily resolves its texture. */
    GlyphRenderInfo getRenderInfo(char chr) {
        final FontAtlas atlas = getAtlas(chr);
        if (!atlas.has(chr % ATLAS_SIZE)) return null;
        final float[] data = atlas.metrics;
        final int o = (chr % ATLAS_SIZE) * FontAtlas.STRIDE;
        final float scale = FontConfig.customFontScale;
        return new GlyphRenderInfo(data[o + FontAtlas.U_START], data[o + FontAtlas.V_START],
            data[o + FontAtlas.X_ADVANCE] * scale, data[o + FontAtlas.OFFSET_X] * scale,
            data[o + FontAtlas.OFFSET_Y] * scale, data[o + FontAtlas.GLYPH_W] * scale,
            data[o + FontAtlas.GLYPH_H] * scale, data[o + FontAtlas.U_SIZE], data[o + FontAtlas.V_SIZE],
            getTexture(chr));
    }

    /** Reads one field from the flat glyph metrics. */
    private float metric(char chr, int field) {
        return getAtlas(chr).metrics[(chr % ATLAS_SIZE) * FontAtlas.STRIDE + field];
    }

    /** Returns the glyph's horizontal atlas origin. */
    @Override
    public float getUStart(char chr) {
        return metric(chr, FontAtlas.U_START);
    }

    /** Returns the glyph's vertical atlas origin. */
    @Override
    public float getVStart(char chr) {
        return metric(chr, FontAtlas.V_START);
    }

    /** Returns the scaled pen advance independently of padding. */
    @Override
    public float getXAdvance(char chr) {
        return metric(chr, FontAtlas.X_ADVANCE) * FontConfig.customFontScale;
    }

    /** Returns the padded draw width with the legacy extra pixel. */
    @Override
    public float getGlyphW(char chr) {
        return metric(chr, FontAtlas.GLYPH_W) * FontConfig.customFontScale + 1.0f;
    }

    /** Returns the padded glyph's horizontal atlas extent. */
    @Override
    public float getUSize(char chr) {
        return metric(chr, FontAtlas.U_SIZE);
    }

    /** Returns the padded glyph's vertical atlas extent. */
    @Override
    public float getVSize(char chr) {
        return metric(chr, FontAtlas.V_SIZE);
    }

    /** Returns the configured custom-font shadow offset. */
    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    /** Resolves the render-thread texture without uploading during measurement. */
    @Override
    public int getTexture(char chr) {
        if (!getAtlas(chr).has(chr % ATLAS_SIZE)) return 0;
        final int id = chr / ATLAS_SIZE;
        final int texture = this.atlasTextures[id];
        return texture != 0 ? texture : uploadAtlas(id);
    }

    /** Returns the configured vertical custom-font scale. */
    @Override
    public float getYScaleMultiplier() {
        return FontConfig.customFontScale;
    }
}
