package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.immediateQuad;
import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.rawQuad;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class GLSM_DisplayList_RawTessellatorTransform_Test {

    private int testList = -1;

    @AfterEach
    void cleanup() {
        if (testList > 0) {
            GLStateManager.glDeleteLists(testList, 1);
            testList = -1;
        }
        GLStateManager.glLoadIdentity();
    }

    /** Compiles half scaled for GLSM_DisplayList_RawTessellatorTransform_Test. */
    private CompiledDisplayList compileHalfScaled(Runnable body) {
        testList = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.glScalef(0.5f, 0.5f, 0.5f);
        body.run();
        GLStateManager.glEndList();

        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);
        return compiled;
    }

    /** Verifies pending transform is emitted before a raw draw. */
    @Test
    void pendingTransformIsEmittedBeforeARawDraw() {
        final CompiledDisplayList compiled = compileHalfScaled(() -> rawQuad(1.0f));

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(scaleIndex >= 0, "The pending scale must be recorded");
        assertTrue(drawIndex >= 0, "The raw draw must be recorded");
        assertTrue(scaleIndex < drawIndex, "Scale must be emitted before the draw it applies to");
        assertEquals(1, compiled.getCommandCounts().getOrDefault(GLCommand.SCALE, 0), "Scale emitted once, not again by the end-of-list flush");
    }

    /** Verifies pending transform is not emitted before a baked draw. */
    @Test
    void pendingTransformIsNotEmittedBeforeABakedDraw() {
        final CompiledDisplayList compiled = compileHalfScaled(() -> immediateQuad(1.0f));

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(drawIndex >= 0, "The immediate mode draw must be recorded");
        assertTrue(scaleIndex < 0 || drawIndex < scaleIndex, "Baked geometry must draw before the delta reaches the stream");
    }

    /** Verifies a raw draw after a baked draw still gets the transform. */
    @Test
    void aRawDrawAfterABakedDrawStillGetsTheTransform() {
        final CompiledDisplayList compiled = compileHalfScaled(() -> {
            immediateQuad(1.0f);
            rawQuad(1.0f);
        });

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int lastDrawIndex = opcodes.lastIndexOf(GLCommand.DRAW_RANGE);
        final int firstDrawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(scaleIndex >= 0, "The pending scale must be recorded");
        assertTrue(firstDrawIndex < scaleIndex, "The baked draw stays ahead of the transform");
        assertTrue(scaleIndex < lastDrawIndex, "The raw draw still gets the transform");
    }

    /** Verifies a raw draw does not flush while baked geometry is pending. */
    @Test
    void aRawDrawDoesNotFlushWhileBakedGeometryIsPending() {
        final CompiledDisplayList compiled = compileHalfScaled(() -> {
            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glVertex3f(0.0f, 0.0f, 0.0f);
            GLStateManager.glVertex3f(1.0f, 0.0f, 0.0f);
            GLStateManager.glVertex3f(1.0f, 1.0f, 0.0f);
            GLStateManager.glVertex3f(0.0f, 1.0f, 0.0f);

            rawQuad(1.0f);

            GLStateManager.glEnd();
        });

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(drawIndex >= 0, "Draws must be recorded");
        assertTrue(scaleIndex < 0 || drawIndex < scaleIndex, "No flush while baked geometry is pending");
    }
}
