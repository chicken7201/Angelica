package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class FontProviderUnicode implements FontProvider, IResourceManagerReloadListener {

    private FontProviderUnicode() {
        onResourceManagerReload(Minecraft.getMinecraft().getResourceManager());
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
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

    private static class InstLoader { static final FontProviderUnicode instance = new FontProviderUnicode(); }
    public static FontProviderUnicode get() { return FontProviderUnicode.InstLoader.instance; }

    private static final ResourceLocation[] unicodePageLocations = new ResourceLocation[256];
    private final byte[] glyphWidth = new byte[65536];

    private ResourceLocation getUnicodePageLocation(int page) {
        final ResourceLocation lookup = unicodePageLocations[page];
        if (lookup == null) {
            final ResourceLocation rl = new ResourceLocation(String.format(
                "textures/font/unicode_page_%02x.png",
                page));
            unicodePageLocations[page] = rl;
            return rl;
        } else {
            return lookup;
        }
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        return UnicodeGlyphMetrics.isAvailable(this.glyphWidth[chr]);
    }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    @Override
    public float getUStart(char chr) {
        return UnicodeGlyphMetrics.getUStart(chr, this.glyphWidth[chr]);
    }

    @Override
    public float getVStart(char chr) {
        return UnicodeGlyphMetrics.getVStart(chr);
    }

    @Override
    public float getXAdvance(char chr) {
        return UnicodeGlyphMetrics.getXAdvance(this.glyphWidth[chr]);
    }

    @Override
    public float getGlyphW(char chr) {
        return UnicodeGlyphMetrics.getGlyphWidth(this.glyphWidth[chr]);
    }

    @Override
    public float getUSize(char chr) {
        return UnicodeGlyphMetrics.getUSize(this.glyphWidth[chr]);
    }

    @Override
    public float getVSize(char chr) {
        return UnicodeGlyphMetrics.getVSize();
    }

    /** Keeps anti-aliasing samples inside the glyph's fixed 16-pixel atlas cell. */
    @Override
    public float getSampleUStart(char chr) {
        return UnicodeGlyphMetrics.getSampleUStart(chr);
    }

    /** Keeps anti-aliasing samples away from the next horizontal atlas cell. */
    @Override
    public float getSampleUEnd(char chr) {
        return UnicodeGlyphMetrics.getSampleUEnd(chr);
    }

    /** Keeps anti-aliasing samples inside the glyph's fixed 16-pixel atlas cell. */
    @Override
    public float getSampleVStart(char chr) {
        return UnicodeGlyphMetrics.getSampleVStart(chr);
    }

    /** Keeps anti-aliasing samples away from the next vertical atlas cell. */
    @Override
    public float getSampleVEnd(char chr) {
        return UnicodeGlyphMetrics.getSampleVEnd(chr);
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset * FontConfig.fontShadowOffsetUC;
    }

    @Override
    public ResourceLocation getTexture(char chr) {
        final int uniPage = chr / 256;
        return getUnicodePageLocation(uniPage);
    }

    @Override
    public float getYScaleMultiplier() {
        return 1;
    }
}
