package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class UnicodeTextureBindingGLTest {

    /** Verifies lazy Unicode uploads restore the caller's active texture unit and real GL binding. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void pageUploadPreservesCallerBinding(int unit) {
        final int initialUnit = GLStateManager.getActiveTextureUnitForServerState();
        final int initialBinding = GLStateManager.getBoundTextureForServerState();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int previousBinding = GLStateManager.getBoundTextureForServerState();
        final int callerTexture = GLStateManager.glGenTextures();
        final int uploadTexture = GLStateManager.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, callerTexture);
            final int result = FontProviderUnicode.withPageTextureBindingPreserved(() -> {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, uploadTexture);
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + (unit == 0 ? 1 : 0));
                return 42;
            });

            assertEquals(42, result);
            assertEquals(unit, GLStateManager.getActiveTextureUnitForServerState());
            assertEquals(callerTexture, GLStateManager.getBoundTextureForServerState());
            assertEquals(callerTexture, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + initialUnit);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, initialBinding);
            GLStateManager.glDeleteTextures(callerTexture);
            GLStateManager.glDeleteTextures(uploadTexture);
        }
    }
}
