package com.gtnewhorizons.angelica.rendering.celeritas;


import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
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
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
    }

    /** Verifies terrain repairs a leaked depth state before it writes the occlusion buffer. */
    @Test
    void terrainSetupRestoresOccludingDepthState() {
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        GLSMDepthStencilAlpha.glDepthMask(false);

        AngelicaRenderPassConfiguration.applyTerrainDepthState();

        assertTrue(GLStateManager.ctx().depthTest.isEffectivelyEnabled(), "depth test");
        assertEquals(GL11.GL_LEQUAL, GLStateManager.ctx().depthState.getFunc(), "depth function");
        assertTrue(GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled(), "depth write mask");
    }
}
