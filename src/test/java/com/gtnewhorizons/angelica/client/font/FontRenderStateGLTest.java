package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FontRenderStateGLTest {

    /** Restores conventional world raster state after each replay test. */
    @AfterEach
    void restoreWorldState() {
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(0.0f, 0.0f);
    }

    /** Verifies deferred text replays its captured depth and polygon-offset semantics. */
    @Test
    void capturedStateSurvivesAChangedFlushContext() {
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glPolygonOffset(-10.0f, -10.0f);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);

        final BatchingFontRenderer.FontRenderState captured = new BatchingFontRenderer.FontRenderState();
        captured.capture();

        GLStateManager.disableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(1.0f, 1.0f);

        captured.apply();

        assertTrue(GLStateManager.getDepthTest().isEffectivelyEnabled(), "depth test");
        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "depth function");
        assertTrue(GLStateManager.isEffectiveDepthMaskEnabled(), "depth write mask");
        assertTrue(GLStateManager.getPolygonOffsetFillState().isEffectivelyEnabled(), "polygon offset fill");
        assertEquals(-10.0f, GLStateManager.getPolygonState().getOffsetFactor(), "polygon offset factor");
        assertEquals(-10.0f, GLStateManager.getPolygonState().getOffsetUnits(), "polygon offset units");
    }
}
