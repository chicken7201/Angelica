package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
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
        final int initialUnit = GLSMTextures.getActiveTextureUnitForServerState();
        final int initialBinding = GLSMTextures.getBoundTextureForServerState();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int previousBinding = GLSMTextures.getBoundTextureForServerState();
        final int callerTexture = GLSMTextures.glGenTextures();
        final int uploadTexture = GLSMTextures.glGenTextures();
        try {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, callerTexture);
            final int result = FontProviderUnicode.withPageTextureBindingPreserved(() -> {
                GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, uploadTexture);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + (unit == 0 ? 1 : 0));
                return 42;
            });

            assertEquals(42, result);
            assertEquals(unit, GLSMTextures.getActiveTextureUnitForServerState());
            assertEquals(callerTexture, GLSMTextures.getBoundTextureForServerState());
            assertEquals(callerTexture, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + initialUnit);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, initialBinding);
            GLSMTextures.glDeleteTextures(callerTexture);
            GLSMTextures.glDeleteTextures(uploadTexture);
        }
    }
}
