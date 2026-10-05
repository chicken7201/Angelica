package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@GLCoreTest
class UnicodeLoadingTextureGLTest {
    private static final char GLYPH = '\uD55C';
    private final AtomicReference<TextureManager> globalTextures = new AtomicReference<>();
    private Minecraft previousMinecraft;
    private IResourceManager resources;
    private FontProviderUnicode provider;

    /** Installs startup resources with no global TextureManager and a visible Hangul bitmap glyph. */
    @BeforeEach
    void setUp() throws IOException {
        previousMinecraft = Reflect.getStatic(Minecraft.class, "theMinecraft");
        final Minecraft minecraft = mock(Minecraft.class);
        minecraft.gameSettings = mock(GameSettings.class);
        when(minecraft.getTextureManager()).thenAnswer(invocation -> globalTextures.get());
        Reflect.setStatic(Minecraft.class, "theMinecraft", minecraft);

        final byte[] widths = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        widths[GLYPH] = 0x18;
        final BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        final int glyph = GLYPH & 255;
        image.setRGB(glyph % 16 * 16 + 1, glyph / 16 * 16 + 1, 0xFFFFFFFF);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        final byte[] png = output.toByteArray();
        resources = mock(IResourceManager.class);
        final IResource pageResource = mock(IResource.class);
        when(pageResource.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(png));
        when(resources.getAllResources(new ResourceLocation("textures/font/unicode_page_d5.png")))
            .thenReturn(Collections.singletonList(pageResource));
        final IResource sizesResource = mock(IResource.class);
        when(sizesResource.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(widths));
        when(resources.getResource(new ResourceLocation("font/glyph_sizes.bin"))).thenReturn(sizesResource);
        provider = new FontProviderUnicode(resources, widths);
    }

    /** Retires test textures through the production cleanup path and restores the singleton. */
    @AfterEach
    void tearDown() {
        try {
            if (provider != null) {
                provider.onResourceManagerReload(resources);
                provider.prepareTextureForBind(new ResourceLocation("test:retired"));
            }
        } finally {
            Reflect.setStatic(Minecraft.class, "theMinecraft", previousMinecraft);
        }
    }

    /** Reproduces early BLS rendering and verifies handoff keeps the GL texture while removing the old registration. */
    @Test
    void uploadsBeforeGlobalManagerAndMigratesWithoutDeletingTexture() {
        final TextureManager splashTextures = new CoreTextureManager(resources);
        final FontProviderUnicode.GlyphRenderInfo glyph = provider.getRenderInfo(GLYPH, splashTextures);
        assertNotNull(glyph.texture);
        final DynamicTexture texture = (DynamicTexture) splashTextures.getTexture(glyph.texture);
        assertNotNull(texture);
        assertTrue(GL11.glIsTexture(glyph.textureId));
        assertEquals(glyph.textureId, provider.prepareTextureForBind(glyph.texture, splashTextures));

        final TextureManager minecraftTextures = new CoreTextureManager(resources);
        globalTextures.set(minecraftTextures);
        assertEquals(glyph.textureId, provider.prepareTextureForBind(glyph.texture, splashTextures));
        assertSame(texture, minecraftTextures.getTexture(glyph.texture));
        assertNull(splashTextures.getTexture(glyph.texture));
        assertTrue(GL11.glIsTexture(glyph.textureId));

        provider.onResourceManagerReload(resources);
        assertEquals(-1, provider.prepareTextureForBind(glyph.texture, splashTextures));
        assertEquals(-1, texture.glTextureId);
        assertNull(minecraftTextures.getTexture(glyph.texture));
        final FontProviderUnicode.GlyphRenderInfo reloaded = provider.getRenderInfo(GLYPH, splashTextures);
        assertNotNull(reloaded.texture);
        assertNotEquals(glyph.texture, reloaded.texture);
        assertEquals(-1, provider.prepareTextureForBind(glyph.texture, splashTextures));
        assertNotNull(minecraftTextures.getTexture(reloaded.texture));
        assertNull(splashTextures.getTexture(reloaded.texture));
    }

    /** Defers GPU allocation when no manager exists and recovers when the private manager becomes available. */
    @Test
    void missingManagersDoNotAllocateOrThrow() {
        final FontProviderUnicode.GlyphRenderInfo deferred = provider.getRenderInfo(GLYPH);
        assertNotNull(deferred);
        assertNull(deferred.texture);
        assertEquals(0, deferred.textureId);
        assertNull(pageTexture());
        assertNotNull(provider.getRenderInfo(GLYPH, new CoreTextureManager(resources)).texture);
    }

    /** Reuses a texture allocated before a registration failure instead of leaking a new texture on every retry. */
    @Test
    void failedRegistrationRetriesTheSameTexture() {
        final TextureManager textures = new FailingOnceTextureManager(resources);
        assertThrows(IllegalStateException.class, () -> provider.getRenderInfo(GLYPH, textures));
        final DynamicTexture allocated = pageTexture();
        assertNotNull(allocated);
        final int textureId = allocated.glTextureId;
        final FontProviderUnicode.GlyphRenderInfo recovered = provider.getRenderInfo(GLYPH, textures);
        assertSame(allocated, textures.getTexture(recovered.texture));
        assertEquals(textureId, recovered.textureId);
    }

    /** Reads the page's allocated texture to distinguish deferred allocation from a hidden GPU leak. */
    private DynamicTexture pageTexture() {
        final Object[] pages = Reflect.get(provider, "unicodePages");
        return Reflect.get(pages[GLYPH >>> 8], "dynamicTexture");
    }

    private static class CoreTextureManager extends TextureManager {

        /** Creates a real registry for the fixture, which runs without Minecraft's TextureUtil mixin. */
        private CoreTextureManager(IResourceManager resources) {
            super(resources);
        }

        /** Supplies a live bitmap texture where untransformed vanilla allocation deleted its generated core-profile ID. */
        @Override
        public ResourceLocation getDynamicTextureLocation(String name, DynamicTexture texture) {
            prepareLiveTexture(texture);
            return super.getDynamicTextureLocation(name, texture);
        }

        /** Applies the fixture's upload adapter before either successful registration or its simulated failure. */
        protected void prepareLiveTexture(DynamicTexture texture) {
            if (!GL11.glIsTexture(texture.glTextureId)) {
                FontProviderUnicode.withPageTextureBindingPreserved(() -> {
                    texture.glTextureId = GLStateManager.glGenTextures();
                    GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture.glTextureId);
                    final int[] pixels = texture.getTextureData();
                    final IntBuffer data = BufferUtils.createIntBuffer(pixels.length);
                    data.put(pixels).flip();
                    final int side = (int) Math.sqrt(pixels.length);
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, side, side, 0,
                        GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, data);
                    return null;
                });
            }
        }
    }

    private static final class FailingOnceTextureManager extends CoreTextureManager {
        private boolean fail = true;

        /** Creates a private manager that simulates a transient first-registration failure. */
        private FailingOnceTextureManager(IResourceManager resources) {
            super(resources);
        }

        /** Fails once after allocation, then delegates subsequent registrations to Minecraft's manager. */
        @Override
        public ResourceLocation getDynamicTextureLocation(String name, DynamicTexture texture) {
            if (fail) {
                prepareLiveTexture(texture);
                fail = false;
                throw new IllegalStateException("Simulated registration failure");
            }
            return super.getDynamicTextureLocation(name, texture);
        }
    }
}
