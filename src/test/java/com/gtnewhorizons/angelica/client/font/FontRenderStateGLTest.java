package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FontRenderStateGLTest {

    /** Verifies upstream state scopes restore the caller after glyphfix replays multiple text segments. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fontScopeRestoresRasterStateAfterSegmentReplay(boolean pipeline) {
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glPolygonOffset(-10.0f, -10.0f);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        final BatchingFontRenderer.FontRenderState first = new BatchingFontRenderer.FontRenderState();
        first.capture();

        GLStateManager.glDepthFunc(GL11.GL_GREATER);
        GLStateManager.glPolygonOffset(-2.0f, -3.0f);
        final BatchingFontRenderer.FontRenderState second = new BatchingFontRenderer.FontRenderState();
        second.capture();

        GLStateManager.disableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(1.0f, 2.0f);
        final BatchingFontRenderer.FontRenderState caller = new BatchingFontRenderer.FontRenderState();
        caller.capture();

        final int depth = GLStateManager.pushState(pipeline ? StateSet.FONT_PIPELINE : StateSet.FONT);
        try {
            first.apply();
            final BatchingFontRenderer.FontRenderState replayed = new BatchingFontRenderer.FontRenderState();
            replayed.capture();
            assertTrue(first.sameAs(replayed), "first segment raster state");
            second.apply();
            replayed.capture();
            assertTrue(second.sameAs(replayed), "second segment raster state");
        } finally {
            GLStateManager.popStateTo(depth);
        }

        final BatchingFontRenderer.FontRenderState restored = new BatchingFontRenderer.FontRenderState();
        restored.capture();
        assertTrue(caller.sameAs(restored), "caller raster state after scope exit");
        assertFalse(GLStateManager.getDepthTest().isEffectivelyEnabled(), "caller depth test");
        assertFalse(GLStateManager.getPolygonOffsetFillState().isEffectivelyEnabled(), "caller polygon offset");
        assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth test");
        assertEquals(GL11.GL_ALWAYS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth function");
        assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth write mask");
        assertFalse(GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "driver polygon offset");
        assertEquals(1.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), "driver polygon offset factor");
        assertEquals(2.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS), "driver polygon offset units");
    }

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
