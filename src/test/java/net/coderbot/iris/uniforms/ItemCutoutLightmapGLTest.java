package net.coderbot.iris.uniforms;

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
        savedDepth = GLStateManager.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        savedBrightnessX = GLStateManager.ctx().lastBrightnessX;
        savedBrightnessY = GLStateManager.ctx().lastBrightnessY;
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        GLStateManager.disableBlend();
        GLStateManager.disableLighting();
        GLStateManager.glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        atlas = createTexture(255, 255, 255);
        GLStateManager.enableTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        lightmap = createTexture(255, 255, 255);
        GLStateManager.disableTexture();
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glScalef(1.0f / 256, 1.0f / 256, 1.0f / 256);
        GLStateManager.glTranslatef(8, 8, 8);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, 144, 16);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer positions = BufferUtils.createFloatBuffer(9);
        positions.put(new float[] { -0.9f, -0.9f, 0, 0.9f, -0.9f, 0, 0, 0.9f, 0 }).flip();
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, positions, GL15.GL_STATIC_DRAW);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        VAOManager.setCurrentVertexFlags(0);
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
    }

    /** Restores the caller's state and deletes only resources allocated by this test. */
    @AfterEach
    void cleanup() {
        ShaderManager.getInstance().deactivate();
        ShaderManager.disable();
        GLStateManager.glBindVertexArray(0);
        if (vbo != 0) GLStateManager.glDeleteBuffers(vbo);
        if (vao != 0) GLStateManager.glDeleteVertexArrays(vao);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        if (atlas != 0) GLStateManager.glDeleteTextures(atlas);
        if (lightmap != 0) GLStateManager.glDeleteTextures(lightmap);
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, savedBrightnessX, savedBrightnessY);
        GLStateManager.popStateTo(savedDepth);
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
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
        GLStateManager.disableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_NOTEQUAL, 0.35f);
        GLStateManager.enableLighting();
        int outer = ItemIdManager.beginCutout(null);
        try {
            assertFalse(FragmentKey.fromState().lightmapEnabled());
            assertEquals(GL11.GL_GREATER, GLStateManager.getAlphaState().getFunction());
            assertEquals(0.1f, GLStateManager.getAlphaState().getReference());
            assertThrows(IllegalStateException.class, () -> {
                int inner = ItemIdManager.beginCutout(null);
                try {
                    GLStateManager.getTextures().getTextureUnitStates(1).enable();
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
        assertFalse(GLStateManager.getAlphaTest().isEnabled());
        assertEquals(GL11.GL_NOTEQUAL, GLStateManager.getAlphaState().getFunction());
        assertEquals(0.35f, GLStateManager.getAlphaState().getReference());
        assertEquals(savedDepth + 1, GLStateManager.getAttribDepth());
        verifyInheritedState();
    }

    /** Retains the explicit lightmap setup performed by the world hand-rendering pipeline. */
    @Test
    void explicitHandDefaultsStillEnableLightmap() {
        GbufferPrograms.setCutoutDefaults();
        assertTrue(FragmentKey.fromState().lightmapEnabled());
        assertTrue(GLStateManager.getAlphaTest().isEnabled());
    }

    /** Allocates a small complete texture on the currently selected unit. */
    private static int createTexture(int red, int green, int blue) {
        int texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        upload(red, green, blue);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
    }

    /** Uploads a uniform 16x16 map to isolate texture multiplication from coordinate selection. */
    private static void upload(int red, int green, int blue) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int i = 0; i < 16 * 16; i++) pixels.put((byte) red).put((byte) green).put((byte) blue).put((byte) 255);
        pixels.flip();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }

    /** Changes only the lightmap contents while preserving its binding and enable state. */
    private static void replaceLightmap(int red, int green, int blue) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        upload(red, green, blue);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /** Renders through the same cutout scope injected into RenderItem and reads an actual framebuffer pixel. */
    private static int[] renderCutoutPixel(boolean lightmapEnabled) {
        GLStateManager.getTextures().getTextureUnitStates(1).setEnabled(lightmapEnabled);
        int depth = ItemIdManager.beginCutout(null);
        try {
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
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
        assertEquals(GL13.GL_TEXTURE3, GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(0.8f, GLStateManager.getColor().getRed());
        assertEquals(0.8f, GLStateManager.getColor().getGreen());
        assertEquals(0.8f, GLStateManager.getColor().getBlue());
        assertTrue(GLStateManager.getLightingState().isEnabled());
        assertEquals(GL11.glIsEnabled(GL11.GL_BLEND), GLStateManager.getBlendMode().isEnabled());
        for (int unit = 0; unit < 2; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            assertEquals(GLStateManager.getTextures().getTextureUnitBindings(unit).getBinding(), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
    }
}
