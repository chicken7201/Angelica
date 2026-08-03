package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class FontProviderUnicode implements FontProvider, IResourceManagerReloadListener {

    private static final int PAGE_COUNT = 256;
    private static final ResourceLocation[] unicodePageLocations = new ResourceLocation[PAGE_COUNT];

    private final byte[] glyphWidth = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
    private final LoadedPage[] unicodePages = new LoadedPage[PAGE_COUNT];
    private IResourceManager resourceManager;

    /** Loads Unicode font resources and registers this provider for later resource-pack reloads. */
    private FontProviderUnicode() {
        onResourceManagerReload(Minecraft.getMinecraft().getResourceManager());
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
    }

    /** Invalidates composed pages and reloads the complete glyph availability table. */
    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        clearCachedPages();
        this.resourceManager = resourceManager;

        try (InputStream inputstream = resourceManager.getResource(new ResourceLocation("font/glyph_sizes.bin")).getInputStream()) {
            final byte[] loadedGlyphWidth = readGlyphWidths(inputstream);
            System.arraycopy(loadedGlyphWidth, 0, this.glyphWidth, 0, this.glyphWidth.length);
        }
        catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        }
    }

    /** Reads the complete glyph_sizes.bin table even when the stream returns short chunks. */
    static byte[] readGlyphWidths(InputStream inputstream) throws IOException {
        final byte[] loadedGlyphWidth = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        new DataInputStream(inputstream).readFully(loadedGlyphWidth);
        return loadedGlyphWidth;
    }

    /** Returns the lazily initialized singleton Unicode font provider. */
    public static FontProviderUnicode get() {
        return InstLoader.instance;
    }

    /** Returns and caches the vanilla resource location for one Unicode texture page. */
    private ResourceLocation getUnicodePageLocation(int page) {
        final ResourceLocation lookup = unicodePageLocations[page];
        if (lookup != null) {
            return lookup;
        }

        final ResourceLocation location = new ResourceLocation(String.format(
            "textures/font/unicode_page_%02x.png",
            page));
        unicodePageLocations[page] = location;
        return location;
    }

    /** Lazily composes and uploads a Unicode page using glyph-cell resource-pack fallback. */
    private synchronized LoadedPage getPage(char chr) {
        final int pageIndex = chr >>> 8;
        LoadedPage page = this.unicodePages[pageIndex];
        if (page == null) {
            page = loadPage(pageIndex);
            this.unicodePages[pageIndex] = page;
        }
        return page;
    }

    /** Loads all resource-pack layers for one page and registers its composed dynamic texture. */
    private LoadedPage loadPage(int pageIndex) {
        final ResourceLocation sourceLocation = getUnicodePageLocation(pageIndex);
        final List<BufferedImage> layers = new ArrayList<>();

        try {
            final List<IResource> resources = this.resourceManager.getAllResources(sourceLocation);
            for (IResource resource : resources) {
                try (InputStream inputstream = resource.getInputStream()) {
                    final BufferedImage image = ImageIO.read(inputstream);
                    if (image == null) {
                        throw new IOException("Unsupported Unicode font page image: " + sourceLocation);
                    }
                    layers.add(image);
                }
            }

            final UnicodeGlyphPage glyphPage = UnicodeGlyphPage.compose(layers);
            final TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
            final ResourceLocation texture = textureManager.getDynamicTextureLocation(
                String.format("angelica_unicode_page_%02x", pageIndex),
                new DynamicTexture(glyphPage.takeImage()));
            return new LoadedPage(glyphPage, texture);
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new RuntimeException("Failed to compose Unicode font page " + sourceLocation, exception);
        }
    }

    /** Deletes uploaded Unicode pages and their cached per-glyph metrics. */
    private void clearCachedPages() {
        TextureManager textureManager = null;
        for (int i = 0; i < this.unicodePages.length; i++) {
            final LoadedPage page = this.unicodePages[i];
            if (page != null) {
                if (textureManager == null) {
                    textureManager = Minecraft.getMinecraft().getTextureManager();
                }
                textureManager.deleteTexture(page.texture);
                textureManager.mapTextureObjects.remove(page.texture);
                this.unicodePages[i] = null;
            }
        }
    }

    /** Returns whether both glyph_sizes.bin and the composed texture contain this glyph. */
    @Override
    public boolean isGlyphAvailable(char chr) {
        return UnicodeGlyphMetrics.isAvailable(this.glyphWidth[chr])
            && getPage(chr).metrics.isGlyphAvailable(chr & 255);
    }

    /** Keeps Unicode glyphs unchanged for random-format replacement. */
    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    /** Returns the composed bitmap UV, preserving declared GTNH custom-glyph bearings. */
    @Override
    public float getUStart(char chr) {
        final UnicodeGlyphPage metrics = getPage(chr).metrics;
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredUStart(chr & 255, this.glyphWidth[chr])
            : metrics.getUStart(chr & 255);
    }

    /** Returns the composed glyph cell's top UV. */
    @Override
    public float getVStart(char chr) {
        return getPage(chr).metrics.getVStart(chr & 255);
    }

    /** Returns cursor advance separately from atlas bounds and padding. */
    @Override
    public float getXAdvance(char chr) {
        final UnicodeGlyphPage metrics = getPage(chr).metrics;
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredXAdvance(this.glyphWidth[chr])
            : metrics.getXAdvance(chr & 255, this.glyphWidth[chr]);
    }

    /** Returns the screen quad width from the selected bitmap bounds. */
    @Override
    public float getGlyphW(char chr) {
        final UnicodeGlyphPage metrics = getPage(chr).metrics;
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredGlyphWidth(this.glyphWidth[chr])
            : metrics.getGlyphWidth(chr & 255);
    }

    /** Returns the composed bitmap UV span, preserving GTNH custom-glyph bounds. */
    @Override
    public float getUSize(char chr) {
        final UnicodeGlyphPage metrics = getPage(chr).metrics;
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredUSize(this.glyphWidth[chr])
            : metrics.getUSize(chr & 255);
    }

    /** Returns one composed Unicode cell's vertical UV span. */
    @Override
    public float getVSize(char chr) {
        return getPage(chr).metrics.getVSize();
    }

    /** Keeps anti-aliasing samples inside the glyph's atlas cell. */
    @Override
    public float getSampleUStart(char chr) {
        return getPage(chr).metrics.getSampleUStart(chr & 255);
    }

    /** Keeps anti-aliasing samples away from the next horizontal atlas cell. */
    @Override
    public float getSampleUEnd(char chr) {
        return getPage(chr).metrics.getSampleUEnd(chr & 255);
    }

    /** Keeps anti-aliasing samples inside the glyph's atlas cell. */
    @Override
    public float getSampleVStart(char chr) {
        return getPage(chr).metrics.getSampleVStart(chr & 255);
    }

    /** Keeps anti-aliasing samples away from the next vertical atlas cell. */
    @Override
    public float getSampleVEnd(char chr) {
        return getPage(chr).metrics.getSampleVEnd(chr & 255);
    }

    /** Returns the configured shadow offset for Unicode glyphs. */
    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset * FontConfig.fontShadowOffsetUC;
    }

    /** Returns the dynamic texture containing the composed Unicode glyph page. */
    @Override
    public ResourceLocation getTexture(char chr) {
        return getPage(chr).texture;
    }

    /** Keeps Unicode glyphs at the renderer's standard vertical scale. */
    @Override
    public float getYScaleMultiplier() {
        return 1;
    }

    private static final class InstLoader {

        private static final FontProviderUnicode instance = new FontProviderUnicode();

        /** Prevents construction of the singleton holder. */
        private InstLoader() {}
    }

    private static final class LoadedPage {

        private final UnicodeGlyphPage metrics;
        private final ResourceLocation texture;

        /** Keeps one page's metrics and uploaded texture location together. */
        private LoadedPage(UnicodeGlyphPage metrics, ResourceLocation texture) {
            this.metrics = metrics;
            this.texture = texture;
        }
    }
}
