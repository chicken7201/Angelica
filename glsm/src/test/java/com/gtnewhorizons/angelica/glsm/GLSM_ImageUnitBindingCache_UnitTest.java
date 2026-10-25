package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_ImageUnitBindingCache_UnitTest {

    private static void unbind(int unit) {
        GLStateManager.glBindImageTexture(unit, 0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R8);
    }

    /** Allocates complete array textures so tuple queries also exercise valid driver bindings. */
    private static int imageTexture(int internalFormat) {
        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            GLStateManager.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, texture);
            final int format = internalFormat == GL30.GL_RGBA32UI ? GL30.GL_RGBA_INTEGER
                : internalFormat == GL30.GL_R32UI ? GL30.GL_RED_INTEGER : GL11.GL_RED;
            final int type = internalFormat == GL30.GL_R8 ? GL11.GL_UNSIGNED_BYTE : GL11.GL_UNSIGNED_INT;
            for (int level = 0; level < 3; level++) {
                GLStateManager.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, level, internalFormat,
                    16 >> level, 16 >> level, 4, 0, format, type, (ByteBuffer) null);
            }
            GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL12.GL_TEXTURE_MAX_LEVEL, 2);
            return texture;
        } finally {
            GLStateManager.glPopAttrib();
        }
    }

    /** Verifies bind then query returns full tuple. */
    @Test
    void bindThenQueryReturnsFullTuple() {
        final int tex = imageTexture(GL30.GL_RGBA32UI);
        try {
            GLStateManager.glBindImageTexture(2, tex, 1, false, 3, GL15.GL_READ_ONLY, GL30.GL_RGBA32UI);

            assertEquals(tex, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 2));
            assertEquals(1, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 2));
            assertEquals(GL11.GL_FALSE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
            assertEquals(3, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            assertEquals(GL15.GL_READ_ONLY, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 2));
            assertEquals(GL30.GL_RGBA32UI, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 2));
        } finally {
            unbind(2);
            GLStateManager.glDeleteTextures(tex);
        }
    }

    /** Verifies save restore round trips the whole tuple. */
    @Test
    void saveRestoreRoundTripsTheWholeTuple() {
        final int a = imageTexture(GL30.GL_R32UI);
        final int b = imageTexture(GL30.GL_R8);
        try {
            GLStateManager.glBindImageTexture(0, a, 2, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            final ImageUnitBinding live = GLStateManager.getImageUnitBinding(0);
            assertNotNull(live);
            final ImageUnitBinding saved = live.copy();

            GLStateManager.glBindImageTexture(0, b, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R8);
            assertEquals(b, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));

            GLStateManager.glBindImageTexture(0, saved.getTexture(), saved.getLevel(), saved.isLayered(), saved.getLayer(), saved.getAccess(), saved.getFormat());

            assertTrue(saved.sameAs(GLStateManager.getImageUnitBinding(0)), "restore must reproduce every field");
            assertEquals(a, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(2, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 0));
            assertEquals(GL15.GL_READ_WRITE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 0));
        } finally {
            unbind(0);
            GLStateManager.glDeleteTextures(a);
            GLStateManager.glDeleteTextures(b);
        }
    }

    /** Verifies reset to zero is not a restore. */
    @Test
    void resetToZeroIsNotARestore() {
        final int tex = imageTexture(GL30.GL_R32UI);
        try {
            GLStateManager.glBindImageTexture(0, tex, 1, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            GLStateManager.glBindImageTexture(0, 0, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R8);

            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 0));
        } finally {
            unbind(0);
            GLStateManager.glDeleteTextures(tex);
        }
    }

    /** Verifies deleting the bound texture clears the image unit. */
    @Test
    void deletingTheBoundTextureClearsTheImageUnit() {
        final boolean driverHasImages = GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42;
        final int tex = GL11.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        try {
            GLStateManager.glBindImageTexture(1, tex, 0, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            assertEquals(tex, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));
            if (driverHasImages) assertEquals(tex, GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));

            GLStateManager.glDeleteTextures(tex);
            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), "GL unbinds a deleted texture from every image unit");
            if (driverHasImages) assertEquals(0, GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), "driver unbinds a deleted texture from every image unit");
            assertEquals(GL15.GL_READ_ONLY, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
            assertEquals(GL30.GL_R8, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));
            if (driverHasImages) {
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));
            }
        } finally {
            unbind(1);
        }
    }

    /** Verifies units are independent and unit zero is not special. */
    @Test
    void unitsAreIndependentAndUnitZeroIsNotSpecial() {
        final int a = imageTexture(GL30.GL_R32UI);
        final int b = imageTexture(GL30.GL_RGBA32UI);
        try {
            GLStateManager.glBindImageTexture(0, a, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            GLStateManager.glBindImageTexture(3, b, 1, false, 2, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32UI);

            assertEquals(a, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(b, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3));
            assertEquals(GL11.GL_TRUE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 0));
            assertEquals(GL11.GL_FALSE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 3));
        } finally {
            unbind(0);
            unbind(3);
            GLStateManager.glDeleteTextures(a);
            GLStateManager.glDeleteTextures(b);
        }
    }

    /** Verifies out of range unit does not corrupt neighbours. */
    @Test
    void outOfRangeUnitDoesNotCorruptNeighbours() {
        final int tex = imageTexture(GL30.GL_R32UI);
        try {
            GLStateManager.glBindImageTexture(0, tex, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            final int beyond = GLStateManager.getImageUnitBinding(0) == null ? 0 : Integer.MAX_VALUE - 1;
            assertFalse(beyond == 0, "unit 0 must resolve for this test to mean anything");

            GLStateManager.glBindImageTexture(beyond, tex, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            // Without image load/store the backend drops the call, so the driver never sees the unit.
            final ContextCapabilities caps = GLStateManager.capabilities;
            final boolean reachesDriver = caps.OpenGL42 || caps.GL_ARB_shader_image_load_store || caps.GL_EXT_shader_image_load_store;
            assertEquals(reachesDriver ? GL11.GL_INVALID_VALUE : GL11.GL_NO_ERROR, GL11.glGetError(), "driver must reject the out-of-range unit");
            assertEquals(tex, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
        } finally {
            unbind(0);
            GLStateManager.glDeleteTextures(tex);
        }
    }

    /** Verifies driver agrees when available. */
    @Test
    void driverAgreesWhenAvailable() {
        assumeTrue(GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42, "no GL 4.2 image load/store on this context");
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GLStateManager.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_R32UI, 4, 4);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GLStateManager.glBindImageTexture(1, tex, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            drainErrors();

            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));

            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 3), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 3));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 3), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 3));
        } finally {
            unbind(1);
            GLStateManager.glDeleteTextures(tex);
        }
    }

    /** Verifies binding texture zero clears layered and layer. */
    @Test
    void bindingTextureZeroClearsLayeredAndLayer() {
        try {
            GLStateManager.glBindImageTexture(2, 0, 0, true, 3, GL15.GL_READ_WRITE, GL11.GL_RGBA8);

            assertEquals(GL11.GL_FALSE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            if (GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42) {
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            }
        } finally {
            unbind(2);
        }
    }
}
