package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FontRenderStateGLTest {

    /** Verifies compiled font scopes record raster state even when it matches the compilation context. */
    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void displayListReplaysCapturedStateAndRestoresCaller(boolean pipeline, boolean execute) {
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMRasterizer.glPolygonOffset(-10.0f, -10.0f);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        final TextDrawState captured = new TextDrawState();
        captured.captureLive(GLStateManager.ctx());
        final TextDrawState replayed = new TextDrawState();
        final int initialDepth = GLSMAttribStack.getAttribDepth();
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMDraws.glNewList(list, execute ? GL11.GL_COMPILE_AND_EXECUTE : GL11.GL_COMPILE);
            final int depth = GLSMAttribStack.pushState(pipeline ? StateSet.FONT_PIPELINE : StateSet.FONT);
            captured.apply();
            DisplayListManager.recordComplexCommand(() -> replayed.captureLive(GLStateManager.ctx()));
            GLSMAttribStack.popStateTo(depth);
            GLSMDraws.glEndList();
            assertEquals(initialDepth, GLSMAttribStack.getAttribDepth(), "balanced compilation scope");

            GLSMDepthStencilAlpha.disableDepthTest();
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
            GLSMDepthStencilAlpha.glDepthMask(false);
            GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GLSMRasterizer.glPolygonOffset(1.0f, 2.0f);
            final TextDrawState caller = new TextDrawState();
            caller.captureLive(GLStateManager.ctx());

            for (int replay = 0; replay < 2; replay++) {
                GLSMDraws.glCallList(list);
                assertTrue(captured.sameAs(replayed), "compiled segment raster state");
                final TextDrawState restored = new TextDrawState();
                restored.captureLive(GLStateManager.ctx());
                assertTrue(caller.sameAs(restored), "caller state after list replay");
                assertEquals(initialDepth, GLSMAttribStack.getAttribDepth(), "balanced replay scope");
                assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth test");
                assertEquals(GL11.GL_ALWAYS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth function");
                assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth write mask");
                assertFalse(GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "driver polygon offset");
                assertEquals(1.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), "driver offset factor");
                assertEquals(2.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS), "driver offset units");
            }
        } finally {
            DisplayListManager.abortCompilation();
            GLSMAttribStack.popStateTo(initialDepth);
            GLSMDraws.glDeleteLists(list, 1);
        }
    }

    /** Verifies upstream state scopes restore the caller after glyphfix replays multiple text segments. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fontScopeRestoresRasterStateAfterSegmentReplay(boolean pipeline) {
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMRasterizer.glPolygonOffset(-10.0f, -10.0f);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        final TextDrawState first = new TextDrawState();
        first.captureLive(GLStateManager.ctx());

        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_GREATER);
        GLSMRasterizer.glPolygonOffset(-2.0f, -3.0f);
        final TextDrawState second = new TextDrawState();
        second.captureLive(GLStateManager.ctx());

        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(1.0f, 2.0f);
        final TextDrawState caller = new TextDrawState();
        caller.captureLive(GLStateManager.ctx());

        final int depth = GLSMAttribStack.pushState(pipeline ? StateSet.FONT_PIPELINE : StateSet.FONT);
        try {
            first.apply();
            final TextDrawState replayed = new TextDrawState();
            replayed.captureLive(GLStateManager.ctx());
            assertTrue(first.sameAs(replayed), "first segment raster state");
            second.apply();
            replayed.captureLive(GLStateManager.ctx());
            assertTrue(second.sameAs(replayed), "second segment raster state");
        } finally {
            GLSMAttribStack.popStateTo(depth);
        }

        final TextDrawState restored = new TextDrawState();
        restored.captureLive(GLStateManager.ctx());
        assertTrue(caller.sameAs(restored), "caller raster state after scope exit");
        assertFalse(GLStateManager.ctx().depthTest.isEffectivelyEnabled(), "caller depth test");
        assertFalse(GLStateManager.ctx().polygonOffsetFillState.isEffectivelyEnabled(), "caller polygon offset");
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
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(0.0f, 0.0f);
    }

    /** Verifies deferred text replays its captured depth and polygon-offset semantics. */
    @Test
    void capturedStateSurvivesAChangedFlushContext() {
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMRasterizer.glPolygonOffset(-10.0f, -10.0f);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);

        final TextDrawState captured = new TextDrawState();
        captured.captureLive(GLStateManager.ctx());

        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(1.0f, 1.0f);

        captured.apply();

        assertTrue(GLStateManager.ctx().depthTest.isEffectivelyEnabled(), "depth test");
        assertEquals(GL11.GL_LEQUAL, GLStateManager.ctx().depthState.getFunc(), "depth function");
        assertTrue(GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled(), "depth write mask");
        assertTrue(GLStateManager.ctx().polygonOffsetFillState.isEffectivelyEnabled(), "polygon offset fill");
        assertEquals(-10.0f, GLStateManager.ctx().polygonState.getOffsetFactor(), "polygon offset factor");
        assertEquals(-10.0f, GLStateManager.ctx().polygonState.getOffsetUnits(), "polygon offset units");
    }
}
