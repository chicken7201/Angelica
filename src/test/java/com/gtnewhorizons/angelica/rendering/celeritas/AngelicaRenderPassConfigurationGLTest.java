package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class AngelicaRenderPassConfigurationGLTest {

    /** Restores the conventional world depth state after each test. */
    @AfterEach
    void restoreWorldDepthState() {
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
    }

    /** Verifies terrain repairs a leaked depth state before it writes the occlusion buffer. */
    @Test
    void terrainSetupRestoresOccludingDepthState() {
        GLStateManager.disableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
        GLStateManager.glDepthMask(false);

        AngelicaRenderPassConfiguration.applyTerrainDepthState();

        assertTrue(GLStateManager.getDepthTest().isEffectivelyEnabled(), "depth test");
        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "depth function");
        assertTrue(GLStateManager.isEffectiveDepthMaskEnabled(), "depth write mask");
    }
}
