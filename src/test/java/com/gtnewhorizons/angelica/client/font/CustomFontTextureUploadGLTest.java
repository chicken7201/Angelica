package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class CustomFontTextureUploadGLTest {

    /** Verifies both upstream atlas pixel paths preserve channels and the caller's real GL texture binding. */
    @ParameterizedTest
    @CsvSource({"0, 2", "1, 2", "0, 6", "1, 6"})
    void uploadPreservesPixelsAndCallerBinding(int unit, int imageType) {
        final BufferedImage atlas = new BufferedImage(2, 2, imageType);
        final int[] pixels = {0xFFFF0000, 0x8000FF00, 0x400000FF, 0xFFFFFFFF};
        atlas.setRGB(0, 0, 2, 2, pixels, 0, 2);
        final int initialUnit = GLStateManager.getActiveTextureUnitForServerState();
        final int initialBinding = GLStateManager.getBoundTextureForServerState();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int previousBinding = GLStateManager.getBoundTextureForServerState();
        final int callerTexture = GLStateManager.glGenTextures();
        int uploadedTexture = 0;
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, callerTexture);
            uploadedTexture = FontProviderCustom.uploadAtlas(atlas);

            assertEquals(unit, GLStateManager.getActiveTextureUnitForServerState());
            assertEquals(callerTexture, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, uploadedTexture);
            assertEquals(2, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
            final ByteBuffer readback = ByteBuffer.allocateDirect(16);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readback);
            for (int i = 0; i < pixels.length; i++) {
                assertEquals((pixels[i] >>> 16) & 255, readback.get(i * 4) & 255);
                assertEquals((pixels[i] >>> 8) & 255, readback.get(i * 4 + 1) & 255);
                assertEquals(pixels[i] & 255, readback.get(i * 4 + 2) & 255);
                assertEquals((pixels[i] >>> 24) & 255, readback.get(i * 4 + 3) & 255);
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + initialUnit);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, initialBinding);
            GLStateManager.glDeleteTextures(callerTexture);
            if (uploadedTexture != 0) GLStateManager.glDeleteTextures(uploadedTexture);
        }
    }
}
