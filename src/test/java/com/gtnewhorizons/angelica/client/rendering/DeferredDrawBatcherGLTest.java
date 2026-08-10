package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.*;

@GLCoreTest
class DeferredDrawBatcherGLTest {

    private final int[] textures = new int[4];

    // Deletes test textures and returns both tracked texture units to a neutral state.
    @AfterEach
    void tearDown() {
        for (int i = 0; i < 8 && DeferredDrawBatcher.isActive(); i++) {
            DeferredDrawBatcher.exitAndFlush();
        }
        for (int unit = 0; unit <= 1; unit++) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        for (int texture : textures) {
            if (texture != 0) GLStateManager.glDeleteTextures(texture);
        }
    }

    // Verifies each deferred key reselects its captured texture unit before binding its texture.
    @Test
    void applyStateRestoresCapturedTextureUnitAndBinding() {
        for (int i = 0; i < textures.length; i++) textures[i] = GLStateManager.glGenTextures();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[0]);
        final long unit0State = DeferredDrawBatcher.captureStateKey();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[1]);
        final long unit1State = DeferredDrawBatcher.captureStateKey();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[2]);
        DeferredDrawBatcher.applyStateKey(unit1State);
        assertEquals(1, GLStateManager.getActiveTextureUnit());
        assertEquals(GL13.GL_TEXTURE1, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(textures[1], GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(textures[1], GLStateManager.getTextures().getTextureUnitBindings(1).getBinding());
        assertEquals(textures[2], GLStateManager.getTextures().getTextureUnitBindings(0).getBinding());

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[3]);
        DeferredDrawBatcher.applyStateKey(unit0State);
        assertEquals(0, GLStateManager.getActiveTextureUnit());
        assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(textures[0], GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(textures[0], GLStateManager.getTextures().getTextureUnitBindings(0).getBinding());
        assertEquals(textures[3], GLStateManager.getTextures().getTextureUnitBindings(1).getBinding());
    }

    // Verifies exit restores the entry selector and every texture binding modified during batching.
    @Test
    void exitRestoresEntryActiveUnitAndBindings() {
        for (int i = 0; i < textures.length; i++) textures[i] = GLStateManager.glGenTextures();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[0]);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[1]);
        DeferredDrawBatcher.enter();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[2]);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[3]);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        DeferredDrawBatcher.exitAndFlush();

        assertEquals(1, GLStateManager.getActiveTextureUnit());
        assertEquals(GL13.GL_TEXTURE1, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(textures[1], GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(textures[0], GLStateManager.getTextures().getTextureUnitBindings(0).getBinding());
        assertEquals(textures[1], GLStateManager.getTextures().getTextureUnitBindings(1).getBinding());

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        assertEquals(textures[0], GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
    }

    // Verifies a nested bracket shares the capture and restores state only at the outer exit.
    @Test
    void nestedBracketRestoresAtOuterExit() {
        textures[0] = GLStateManager.glGenTextures();
        textures[1] = GLStateManager.glGenTextures();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[0]);
        DeferredDrawBatcher.enter();
        DeferredDrawBatcher.enter();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[1]);
        DeferredDrawBatcher.exitAndFlush();
        assertTrue(DeferredDrawBatcher.isActive());
        assertEquals(0, GLStateManager.getActiveTextureUnit());

        DeferredDrawBatcher.exitAndFlush();
        assertFalse(DeferredDrawBatcher.isActive());
        assertEquals(1, GLStateManager.getActiveTextureUnit());
        assertEquals(textures[0], GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(0, GLStateManager.getTextures().getTextureUnitBindings(0).getBinding());
    }
}
