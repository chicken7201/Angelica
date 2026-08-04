package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import lombok.Setter;
import lombok.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

public final class FontProviderCustom implements FontProvider {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    public static final String FONT_DIR = "fonts/custom/";
    static final int ATLAS_SIZE = 128;
    static final int ATLAS_COUNT = 512;
    private final byte id; // 0 - primary font, 1 - fallback font
    private FontAtlas[] fontAtlases = new FontAtlas[ATLAS_COUNT];
    private float currentFontQuality = FontConfig.customFontQuality;
    @Setter
    private Font font;

    private FontProviderCustom(byte id) {
        this.id = id;
        Font[] availableFonts = FontStrategist.getAvailableFonts();
        String myFontName = switch (this.id) {
            case 0 -> FontConfig.customFontNamePrimary;
            case 1 -> FontConfig.customFontNameFallback;
            default -> null;
        };
        if (Objects.equals(myFontName, "(none)")) {
            this.font = null;
            return;
        }
        int fontPos = -1;
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(myFontName, availableFonts[i].getFontName())) {
                fontPos = i;
                break;
            }
        }
        if (fontPos == -1) {
            LOGGER.info("Could not find previously set font \"{}\". ", myFontName);
            this.font = null;
            return;
        }
        this.font = availableFonts[fontPos].deriveFont(this.currentFontQuality);
    }
    private static class InstLoader {
        static final FontProviderCustom instance0 = new FontProviderCustom((byte)0);
        static final FontProviderCustom instance1 = new FontProviderCustom((byte)1);
    }
    public static FontProviderCustom getPrimary() { return InstLoader.instance0; }
    public static FontProviderCustom getFallback() { return InstLoader.instance1; }

    public void reloadFont(int fontID) {
        this.currentFontQuality = FontConfig.customFontQuality;
        this.font = FontStrategist.getAvailableFonts()[fontID].deriveFont(this.currentFontQuality);

        File[] files = new File(getFontDir()).listFiles();
        if (files != null) {
            for (File f : files) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    f.delete();
                }
            }
        }

        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        Map mapTextureObjects = tm.mapTextureObjects;
        for (int i = 0; i < ATLAS_COUNT; i++) {
            ResourceLocation key = new ResourceLocation(getAtlasResourceName(i));
            if (mapTextureObjects.containsKey(key)) {
                ITextureObject obj = (ITextureObject) mapTextureObjects.get(key);
                TextureUtil.deleteTexture(obj.getGlTextureId());
                mapTextureObjects.remove(key);
            }
        }

        this.fontAtlases = new FontAtlas[ATLAS_COUNT];
    }

    private String getFontDir() {
        return FONT_DIR + "f" + this.id + "/";
    }

    private String getAtlasFilename(int atlasId) {
        return "f" + this.id + "p" + atlasId;
    }

    String getAtlasResourceName(int atlasId) {
        return "minecraft:angelica_c" + getAtlasFilename(atlasId);
    }

    String getAtlasFullPath(int atlasId) {
        return getFontDir() + getAtlasFilename(atlasId) + ".png";
    }

    @Value
    private class GlyphData {
        float uStart;
        float vStart;
        float xAdvance;
        float drawOffsetX;
        float drawOffsetY;
        float drawWidth;
        float drawHeight;
        float uSz;
        float vSz;
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
        final ResourceLocation texture;

        /** Captures one custom glyph's atlas UV, baseline-relative quad, and independent pen advance. */
        private GlyphRenderInfo(float uStart, float vStart, float xAdvance, float drawOffsetX, float drawOffsetY,
            float drawWidth, float drawHeight, float uSize, float vSize, ResourceLocation texture) {
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

    private class FontAtlas {

        GlyphData[] glyphData = new GlyphData[ATLAS_SIZE];
        private ResourceLocation texture;
        private final int id;

        FontAtlas(int id) {
            this.id = id;
        }

        /** Rasterizes displayable glyphs into padded cells while retaining their original bearings and advances. */
        void construct(Font font) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            configureGraphics(g2d, font);
            final FontMetrics fm = g2d.getFontMetrics();
            final FontRenderContext fontRenderContext = g2d.getFontRenderContext();
            final int lineHeight = fm.getHeight();
            final int atlasPadding = Math.max(1, (int) Math.ceil(currentFontQuality / 16.0F));
            final int atlasGap = Math.max(atlasPadding, (int) Math.ceil(currentFontQuality / 3.0F));
            final CustomGlyphMetrics[] rasterMetrics = new CustomGlyphMetrics[ATLAS_SIZE];
            int atlasChars = 0;
            int maxCellHeight = 1;
            for (int i = 0; i < ATLAS_SIZE; i++) {
                final char ch = (char) (i + ATLAS_SIZE * this.id);
                if (!font.canDisplay(ch)) {
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
            if (atlasChars == 0) { return; }

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

            image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            g2d = image.createGraphics();
            configureGraphics(g2d, font);

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
                g2d.drawGlyphVector(metrics.glyphVector, baselineX, baselineY);
                this.glyphData[i] = new GlyphData(
                    (float) imgX / imageWidth,
                    (float) imgY / imageHeight,
                    metrics.advanceX,
                    metrics.drawOffsetX,
                    metrics.drawOffsetY,
                    metrics.drawWidth,
                    metrics.drawHeight,
                    (float) atlasWidth / imageWidth,
                    (float) atlasHeight / imageHeight);
                imgX += atlasWidth + atlasGap;
                tileX++;
            }
            g2d.dispose();
            try {
                Files.createDirectories(Paths.get(getFontDir()));
                ImageIO.write(image, "png", new File(getAtlasFullPath(this.id)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.texture = new ResourceLocation(getAtlasResourceName(this.id));
        }
    }

    /** Applies identical Java2D rasterization hints during metric extraction and final atlas drawing. */
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

    private FontAtlas getAtlas(char chr) {
        int id = chr / ATLAS_SIZE;
        FontAtlas fa = this.fontAtlases[id];
        if (fa == null) {
            fa = new FontAtlas(id);
            fa.construct(this.font);
            this.fontAtlases[id] = fa;
        }
        return fa;
    }

    /** Returns a custom glyph's cached atlas data, or null when this provider cannot display it. */
    private GlyphData getGlyphData(char chr) {
        if (this.font == null) {
            return null;
        }
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE];
    }

    /** Returns one immutable custom glyph snapshot with the current custom-font scale applied once. */
    GlyphRenderInfo getRenderInfo(char chr) {
        final GlyphData data = getGlyphData(chr);
        if (data == null) {
            return null;
        }
        final float scale = FontConfig.customFontScale;
        return new GlyphRenderInfo(
            data.uStart,
            data.vStart,
            data.xAdvance * scale,
            data.drawOffsetX * scale,
            data.drawOffsetY * scale,
            data.drawWidth * scale,
            data.drawHeight * scale,
            data.uSz,
            data.vSz,
            getAtlas(chr).texture);
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        return getGlyphData(chr) != null;
    }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    @Override
    public float getUStart(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].uStart;
    }

    @Override
    public float getVStart(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].vStart;
    }

    @Override
    public float getXAdvance(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].xAdvance * FontConfig.customFontScale;
    }

    @Override
    public float getGlyphW(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].drawWidth * FontConfig.customFontScale + 1.0F;
    }

    @Override
    public float getUSize(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].uSz;
    }

    @Override
    public float getVSize(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].vSz;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public ResourceLocation getTexture(char chr) {
        return getAtlas(chr).texture;
    }

    @Override
    public float getYScaleMultiplier() {
        return FontConfig.customFontScale;
    }
}
