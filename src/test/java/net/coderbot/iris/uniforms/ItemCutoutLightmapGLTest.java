package net.coderbot.iris.uniforms;


import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.FragmentKey;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import net.coderbot.iris.layer.GbufferPrograms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class ItemCutoutLightmapGLTest {
    private int savedDepth;
    private float savedBrightnessX;
    private float savedBrightnessY;
    private int atlas;
    private int lightmap;
    private int vao;
    private int vbo;

    /** Creates a core-profile draw with an inherited, disabled world lightmap and non-white item color. */
    @BeforeEach
    void setUp() {
        savedDepth = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        savedBrightnessX = GLStateManager.ctx().lastBrightnessX;
        savedBrightnessY = GLStateManager.ctx().lastBrightnessY;
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMRasterizer.glViewport(0, 0, 800, 600);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMBlend.disableBlend();
        GLSMLighting.disableLighting();
        GLSMCurrentAttribs.glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        atlas = createTexture(255, 255, 255);
        GLSMTextures.enableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        lightmap = createTexture(255, 255, 255);
        GLSMTextures.disableTexture();
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glScalef(1.0f / 256, 1.0f / 256, 1.0f / 256);
        GLSMMatrices.glTranslatef(8, 8, 8);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, 144, 16);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        vbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer positions = BufferUtils.createFloatBuffer(9);
        positions.put(new float[] { -0.9f, -0.9f, 0, 0.9f, -0.9f, 0, 0, 0.9f, 0 }).flip();
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, positions, GL15.GL_STATIC_DRAW);
        GLSMVertexInput.glEnableVertexAttribArray(0);
        GLSMVertexInput.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        VAOManager.setCurrentVertexFlags(0);
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
    }

    /** Restores caller state and owned resources while leaving FFP enabled for the shared core fixture. */
    @AfterEach
    void cleanup() {
        ShaderManager.getInstance().deactivate();
        GLSMVertexInput.glBindVertexArray(0);
        if (vbo != 0) GLSMBuffers.glDeleteBuffers(vbo);
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        if (atlas != 0) GLSMTextures.glDeleteTextures(atlas);
        if (lightmap != 0) GLSMTextures.glDeleteTextures(lightmap);
        GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, savedBrightnessX, savedBrightnessY);
        GLSMAttribStack.popStateTo(savedDepth);
    }

    /** Ensures changing a bound Night Vision lightmap cannot tint a HUD item while that unit is disabled. */
    @Test
    void hudItemIgnoresBothNeutralAndBlueNightVisionLightmaps() {
        int[] off = renderCutoutPixel(false);
        replaceLightmap(64, 128, 255);
        int[] on = renderCutoutPixel(false);
        System.out.println("HUD neutral=" + Arrays.toString(off) + " blue=" + Arrays.toString(on));
        assertArrayEquals(off, on, "HUD item must not sample the inherited Night Vision lightmap");
        assertArrayEquals(new int[] { 204, 204, 204 }, on);
    }

    /** Keeps world and held-item lighting when their caller already enabled the lightmap. */
    @Test
    void worldItemStillSamplesItsEnabledLightmap() {
        replaceLightmap(64, 128, 255);
        int[] pixel = renderCutoutPixel(true);
        assertArrayEquals(new int[] { 51, 102, 204 }, pixel);
    }

    /** Checks nested cutout scopes restore alpha and lightmap state even when a custom renderer fails. */
    @Test
    void nestedRendererFailurePreservesBindingsColorLightingAndAlpha() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE3);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_NOTEQUAL, 0.35f);
        GLSMLighting.enableLighting();
        int outer = ItemIdManager.beginCutout(null);
        try {
            assertFalse(FragmentKey.fromState().lightmapEnabled());
            assertEquals(GL11.GL_GREATER, GLStateManager.ctx().alphaState.getFunction());
            assertEquals(0.1f, GLStateManager.ctx().alphaState.getReference());
            assertThrows(IllegalStateException.class, () -> {
                int inner = ItemIdManager.beginCutout(null);
                try {
                    GLSMTextures.getTextures().getTextureUnitStates(1).enable();
                    throw new IllegalStateException("custom item renderer failed");
                } finally {
                    ItemIdManager.endCutout(inner);
                }
            });
            assertFalse(FragmentKey.fromState().lightmapEnabled());
            verifyInheritedState();
        } finally {
            ItemIdManager.endCutout(outer);
        }
        assertFalse(GLStateManager.ctx().alphaTest.isEnabled());
        assertEquals(GL11.GL_NOTEQUAL, GLStateManager.ctx().alphaState.getFunction());
        assertEquals(0.35f, GLStateManager.ctx().alphaState.getReference());
        assertEquals(savedDepth + 1, GLSMAttribStack.getAttribDepth());
        verifyInheritedState();
    }

    /** Retains the explicit lightmap setup performed by the world hand-rendering pipeline. */
    @Test
    void explicitHandDefaultsStillEnableLightmap() {
        GbufferPrograms.setCutoutDefaults();
        assertTrue(FragmentKey.fromState().lightmapEnabled());
        assertTrue(GLStateManager.ctx().alphaTest.isEnabled());
    }

    /** Allocates a small complete texture on the currently selected unit. */
    private static int createTexture(int red, int green, int blue) {
        int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        upload(red, green, blue);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
    }

    /** Uploads a uniform 16x16 map to isolate texture multiplication from coordinate selection. */
    private static void upload(int red, int green, int blue) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int i = 0; i < 16 * 16; i++) pixels.put((byte) red).put((byte) green).put((byte) blue).put((byte) 255);
        pixels.flip();
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }

    /** Changes only the lightmap contents while preserving its binding and enable state. */
    private static void replaceLightmap(int red, int green, int blue) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        upload(red, green, blue);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /** Renders through the same cutout scope injected into RenderItem and reads an actual framebuffer pixel. */
    private static int[] renderCutoutPixel(boolean lightmapEnabled) {
        GLSMTextures.getTextures().getTextureUnitStates(1).setEnabled(lightmapEnabled);
        int depth = ItemIdManager.beginCutout(null);
        try {
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(400, 200, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return new int[] { pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255 };
        } finally {
            ItemIdManager.endCutout(depth);
            assertEquals(lightmapEnabled, FragmentKey.fromState().lightmapEnabled());
        }
    }

    /** Compares native binding/active-unit queries with GLSM and checks the untouched FFP state. */
    private void verifyInheritedState() {
        assertEquals(GL13.GL_TEXTURE3, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(GL13.GL_TEXTURE3, GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(0.8f, GLSMCurrentAttribs.getColor().getRed());
        assertEquals(0.8f, GLSMCurrentAttribs.getColor().getGreen());
        assertEquals(0.8f, GLSMCurrentAttribs.getColor().getBlue());
        assertTrue(GLSMLighting.getLightingState().isEnabled());
        assertEquals(GL11.glIsEnabled(GL11.GL_BLEND), GLSMBlend.getBlendMode().isEnabled());
        for (int unit = 0; unit < 2; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            assertEquals(GLSMTextures.getTextures().getTextureUnitBindings(unit).getBinding(), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
    }
}
