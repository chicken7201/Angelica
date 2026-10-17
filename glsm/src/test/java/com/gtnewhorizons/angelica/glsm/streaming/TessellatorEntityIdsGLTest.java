package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.EntityIdsOnlyHandler;
import com.gtnewhorizons.angelica.glsm.GLCompatTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@GLCompatTest
class TessellatorEntityIdsGLTest {

    private static final int[] GREEN = { 0, 255, 0, 255 };
    private static final int[] BLUE = { 0, 0, 255, 255 };

    private ImmediateExtendedAttribHandler previousHandler;
    private int program;

    /** Prepares the rendering state required by the material ID tests. */
    @BeforeEach
    void setUp() {
        previousHandler = EntityIdsOnlyHandler.install();
        program = GlTestSupport.program(TessellatorEntityIdsFixture.VERTEX, TessellatorEntityIdsFixture.FRAGMENT);
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        GLStateManager.glViewport(0, 0, 8, 8);
        GLStateManager.glUseProgram(program);
        GLStateManager.glClearColor(0, 0, 0, 1);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    /** Restores the GL state and material IDs after each test. */
    @AfterEach
    void tearDown() {
        GLStateManager.glUseProgram(0);
        GLStateManager.glDeleteProgram(program);
        GLStateManager.glPopAttrib();
        GLSMHooks.immediateExtendedHandler = previousHandler;
    }

    /** Verifies material ID changes affect only subsequently added vertices. */
    @Test
    void idsChangedMidBatchApplyToTheVerticesAddedAfterTheChange() {
        TessellatorEntityIdsFixture.drawIdsChangedMidBatch();

        assertColor(1, GREEN, "left quad was added under item 7");
        assertColor(6, BLUE, "right quad was added under item 50020");
    }

    /** Verifies an ID change after the last vertex preserves the completed batch. */
    @Test
    void idsChangedAfterTheLastVertexLeaveTheBatchAlone() {
        TessellatorEntityIdsFixture.drawIdsChangedAfterTheLastVertex();

        assertColor(1, GREEN, "every vertex was added under item 7");
        assertColor(6, GREEN, "every vertex was added under item 7");
    }

    /** Checks that the rendered pixel matches the expected material ID color. */
    private static void assertColor(int x, int[] expected, String message) {
        final int[] pixel = FfpFixture.readPixel(x, 4);
        assertArrayEquals(expected, pixel, message + ", got " + Arrays.toString(pixel));
    }
}
