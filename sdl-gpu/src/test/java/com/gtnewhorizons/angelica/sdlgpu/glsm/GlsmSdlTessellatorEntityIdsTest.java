package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.EntityIdsOnlyHandler;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorEntityIdsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.BLUE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.GREEN;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlTessellatorEntityIdsTest {

    private ImmediateExtendedAttribHandler previousHandler;
    private int program;

    /** Prepares the rendering state required by the material ID tests. */
    @BeforeEach
    void setUp() {
        previousHandler = EntityIdsOnlyHandler.install();
        program = GlTestSupport.program(TessellatorEntityIdsFixture.VERTEX, TessellatorEntityIdsFixture.FRAGMENT);
    }

    /** Restores the GL state and material IDs after each test. */
    @AfterEach
    void tearDown() {
        GLStateManager.glUseProgram(0);
        GLStateManager.glDeleteProgram(program);
        GLSMHooks.immediateExtendedHandler = previousHandler;
    }

    /** Verifies material ID changes affect only subsequently added vertices. */
    @Test
    void idsChangedMidBatchApplyToTheVerticesAddedAfterTheChange() {
        GlsmSdlHeadlessRig.beginFrameAndClear();
        GLStateManager.glUseProgram(program);
        TessellatorEntityIdsFixture.drawIdsChangedMidBatch();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 4, SIZE / 2, GREEN, "left quad was added under item 7");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 3 * SIZE / 4, SIZE / 2, BLUE, "right quad was added under item 50020");
    }

    /** Verifies an ID change after the last vertex preserves the completed batch. */
    @Test
    void idsChangedAfterTheLastVertexLeaveTheBatchAlone() {
        GlsmSdlHeadlessRig.beginFrameAndClear();
        GLStateManager.glUseProgram(program);
        TessellatorEntityIdsFixture.drawIdsChangedAfterTheLastVertex();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 4, SIZE / 2, GREEN, "every vertex was added under item 7");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 3 * SIZE / 4, SIZE / 2, GREEN, "every vertex was added under item 7");
    }
}
