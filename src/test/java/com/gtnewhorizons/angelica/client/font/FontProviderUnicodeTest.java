package com.gtnewhorizons.angelica.client.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

class FontProviderUnicodeTest {

    private static final int PAGE_SIZE = 256;

    /** Verifies ordinary ASCII, Latin, and Hangul BMP glyphs still load from their bitmap pages. */
    @Test
    void loadsAsciiLatinAndHangulBmpGlyphs() throws IOException {
        final char ascii = 'A';
        final char latin = '\u00E9';
        final char hangul = '\uD55C';
        final byte[] glyphWidths = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        glyphWidths[ascii] = packBounds(1, 8);
        glyphWidths[latin] = packBounds(1, 8);
        glyphWidths[hangul] = packBounds(1, 8);

        final IResourceManager manager = mock(IResourceManager.class);
        final List<IResource> latinPage = singlePageWithGlyphs(ascii, latin);
        final List<IResource> hangulPage = singlePageWithGlyphs(hangul);
        when(manager.getAllResources(pageLocation(latin))).thenReturn(latinPage);
        when(manager.getAllResources(pageLocation(hangul))).thenReturn(hangulPage);
        final FontProviderUnicode provider = new FontProviderUnicode(manager, glyphWidths);

        assertTrue(provider.isGlyphAvailable(ascii));
        assertTrue(provider.isGlyphAvailable(latin));
        assertTrue(provider.isGlyphAvailable(hangul));
    }

    /** Verifies high and low surrogates never attempt Unicode bitmap page lookup. */
    @Test
    void rejectsSurrogatesBeforePageLookup() {
        final IResourceManager manager = mock(IResourceManager.class);
        final byte[] glyphWidths = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        final char highSurrogate = '\uD912';
        final char lowSurrogate = '\uDC34';
        glyphWidths[highSurrogate] = packBounds(1, 8);
        glyphWidths[lowSurrogate] = packBounds(1, 8);
        final FontProviderUnicode provider = new FontProviderUnicode(manager, glyphWidths);

        assertFalse(provider.isGlyphAvailable(highSurrogate));
        assertFalse(provider.isGlyphAvailable(lowSurrogate));
        assertNull(provider.getRenderInfo(highSurrogate));
        assertNull(provider.getTexture(lowSurrogate));
        assertEquals(0.0F, provider.getXAdvance(highSurrogate));
        assertEquals(0.0F, provider.getUSize(lowSurrogate));
        verifyNoInteractions(manager);
    }

    /** Verifies an absent bitmap page is cached as unavailable instead of becoming a fatal error. */
    @Test
    void treatsMissingPageAsUnavailable() throws IOException {
        final char chr = '\u0401';
        final byte[] glyphWidths = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        glyphWidths[chr] = packBounds(1, 8);
        final IResourceManager manager = mock(IResourceManager.class);
        final ResourceLocation location = pageLocation(chr);
        when(manager.getAllResources(location)).thenThrow(new FileNotFoundException(location.toString()));
        final FontProviderUnicode provider = new FontProviderUnicode(manager, glyphWidths);

        assertFalse(provider.isGlyphAvailable(chr));
        assertFalse(provider.isGlyphAvailable(chr));
        assertNull(provider.getRenderInfo(chr));
        assertNull(provider.getTexture(chr));
        verify(manager).getAllResources(location);
    }

    /** Verifies an existing page with undecodable PNG data remains a reported load failure. */
    @Test
    void reportsCorruptExistingPage() throws IOException {
        final char chr = '\u0501';
        final byte[] glyphWidths = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        glyphWidths[chr] = packBounds(1, 8);
        final IResourceManager manager = mock(IResourceManager.class);
        final IResource resource = mock(IResource.class);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }));
        when(manager.getAllResources(pageLocation(chr))).thenReturn(Collections.singletonList(resource));
        final FontProviderUnicode provider = new FontProviderUnicode(manager, glyphWidths);

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> provider.isGlyphAvailable(chr));

        assertTrue(exception.getCause() instanceof IOException);
    }

    /** Verifies a read failure after resource lookup is not mistaken for an absent page. */
    @Test
    void reportsReadFailureFromExistingPage() throws IOException {
        final char chr = '\u0601';
        final byte[] glyphWidths = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        glyphWidths[chr] = packBounds(1, 8);
        final IResourceManager manager = mock(IResourceManager.class);
        final IResource resource = mock(IResource.class);
        when(resource.getInputStream()).thenReturn(new FailingInputStream());
        when(manager.getAllResources(pageLocation(chr))).thenReturn(Collections.singletonList(resource));
        final FontProviderUnicode provider = new FontProviderUnicode(manager, glyphWidths);

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> provider.isGlyphAvailable(chr));

        assertTrue(exception.getCause() instanceof IOException);
    }

    /** Returns the vanilla bitmap location selected for the supplied BMP character. */
    private static ResourceLocation pageLocation(char chr) {
        return new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", chr >>> 8));
    }

    /** Creates one PNG resource whose requested glyph cells contain visible pixels. */
    private static List<IResource> singlePageWithGlyphs(char... characters) throws IOException {
        final BufferedImage image = new BufferedImage(PAGE_SIZE, PAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        final int cellSize = PAGE_SIZE / UnicodeGlyphPage.GRID_SIZE;
        for (char chr : characters) {
            final int glyph = chr & 255;
            final int glyphLeft = glyph % UnicodeGlyphPage.GRID_SIZE * cellSize;
            final int glyphTop = glyph / UnicodeGlyphPage.GRID_SIZE * cellSize;
            image.setRGB(glyphLeft + 1, glyphTop + 1, 0xFFFFFFFF);
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        final byte[] png = output.toByteArray();
        final IResource resource = mock(IResource.class);
        when(resource.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(png));
        return Collections.singletonList(resource);
    }

    /** Packs inclusive glyph columns into the glyph_sizes.bin representation. */
    private static byte packBounds(int startColumn, int endColumn) {
        return (byte) (startColumn << 4 | endColumn);
    }

    private static final class FailingInputStream extends InputStream {

        /** Simulates a resource that was found but fails while its PNG bytes are read. */
        @Override
        public int read() throws IOException {
            throw new IOException("Unicode page read failed");
        }
    }
}
