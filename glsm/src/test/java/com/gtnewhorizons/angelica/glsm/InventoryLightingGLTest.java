package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.streaming.InventoryLighting;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.recording.DisplayListVBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCompatTest
class InventoryLightingGLTest {
    private int texture;
    private int list;

    /** Installs a lightmap with distinguishable darkness and full brightness samples. */
    @BeforeEach
    void setUp() {
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMRasterizer.glViewport(0, 0, 800, 600);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMBlend.disableBlend();
        GLSMLighting.disableLighting();
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.enableTexture();
        texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        final ByteBuffer pixels = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int i = 0; i < 256; i++) {
            final byte value = (byte) (i == 255 ? 255 : 32);
            pixels.put(value).put(value).put(value).put((byte) 255);
        }
        pixels.flip();
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glScalef(1.0f / 256.0f, 1.0f / 256.0f, 1.0f);
        GLSMMatrices.glTranslatef(8.0f, 8.0f, 0.0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
    }

    /** Releases captured geometry and restores the lightmap unit after pixel checks. */
    @AfterEach
    void cleanup() {
        if (list != 0) GLSMDraws.glDeleteLists(list, 1);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glDeleteTextures(texture);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        ShaderManager.getInstance().deactivate();
        ShaderManager.disable();
    }

    /** Captures ordinary, emissive, and ordinary faces in GregTech's single tessellator pattern. */
    private void compileFaces() {
        list = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        final DirectTessellator tess = (DirectTessellator) TessellatorManager.get();
        tess.startDrawing(GL11.GL_QUADS);
        tess.setNormal(0, 0, 1);
        InventoryLighting.inheritCurrentBrightness(tess, false);
        tess.setColorRGBA(255, 255, 255, 255);
        quad(tess, -0.9f, -0.4f);
        InventoryLighting.inheritCurrentBrightness(tess, true);
        tess.setBrightness(0xF000F0);
        quad(tess, -0.2f, 0.2f);
        InventoryLighting.inheritCurrentBrightness(tess, false);
        quad(tess, 0.4f, 0.9f);
        tess.draw();
        GLSMDraws.glEndList();
        assertEquals(1, DisplayListManager.getDisplayList(list).getOwnedVbos().getVBOs().length,
            "ordinary and emissive faces must keep a single cached draw");
        configureUnredirectedVertexInputs();
    }

    /** Supplies the generic attributes that the game's bytecode redirector installs for GTNHLib. */
    private void configureUnredirectedVertexInputs() {
        for (DisplayListVBO.SubVBO draw : DisplayListManager.getDisplayList(list).getOwnedVbos().getVBOs()) {
            final var vao = draw.getVAO();
            final var vbo = vao.getVBO();
            final var format = vbo.getVertexFormat();
            vao.bind();
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo.getId());
            int offset = 0;
            for (VertexFormatElement element : format.elementsArray) {
                final var usage = element.getUsage();
                if (usage != VertexFormatElement.Usage.PADDING) {
                    final int location = usage.getAttributeLocation();
                    GLSMVertexInput.glEnableVertexAttribArray(location);
                    GLSMVertexInput.glVertexAttribPointer(location, element.getCount(), element.getType().getGlType(),
                        usage.isNormalized(), format.getVertexSize(), offset);
                }
                offset += element.getByteSize();
            }
            vao.unbind();
        }
    }

    /** Replays cached draws with the pre-draw hook normally supplied by the game redirector. */
    private void renderCachedFaces() {
        for (DisplayListVBO.SubVBO draw : DisplayListManager.getDisplayList(list).getOwnedVbos().getVBOs()) {
            final var vao = draw.getVAO();
            vao.bind();
            com.gtnewhorizons.angelica.glsm.ffp.VAOManager.setCurrentVertexFlags(vao.getVBO().getVertexFormat().getVertexFlags());
            ShaderManager.getInstance().preDraw(GLStateManager.ctx());
            vao.draw(draw.getDrawMode(), draw.getStart(), draw.getCount());
            vao.unbind();
        }
    }

    /** Emits one complete face before or after an emissive brightness change. */
    private static void quad(DirectTessellator tess, float left, float right) {
        tess.addVertex(left, -0.8f, 0);
        tess.addVertex(right, -0.8f, 0);
        tess.addVertex(right, 0.8f, 0);
        tess.addVertex(left, 0.8f, 0);
    }

    /** Reads a face's red channel after cached rendering. */
    private static int redAt(int x) {
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, 300, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return pixel.get(0) & 255;
    }

    /** Reuses a GUI-compiled cache in dark and bright surroundings while keeping glow full bright. */
    @Test
    void cachedOrdinaryFacesFollowCurrentLightWhileGlowStaysBright() {
        GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, 240, 240);
        compileFaces();
        for (int light : new int[] { 0, 240, 0 }) {
            GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, light, light);
            GLSMBlend.glClearColor(0, 0, 0, 1);
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
            renderCachedFaces();
            final int ordinary = light == 0 ? 32 : 255;
            assertEquals(ordinary, redAt(140), 1, "ordinary face before glow");
            assertEquals(255, redAt(400), 1, "emissive face");
            assertEquals(ordinary, redAt(660), 1, "ordinary face after glow");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        }
    }
}
