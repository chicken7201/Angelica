package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Keeps inventory geometry with inherited light separate from emissive vertex light. */
public final class InventoryLighting {
    /** Prevents a later brightness override from being baked into earlier inventory faces. */
    public static void separateBrightnessRuns(Tessellator tess, boolean hasOverride) {
        if (TessellatorManager.getDirectCaptureDepth() > 0 && !GLStateManager.isRecordingDisplayList()) return;
        if (!tess.isDrawing || tess.drawMode != GL11.GL_QUADS || tess.hasBrightness == hasOverride) return;

        if (tess.vertexCount != 0) {
            final boolean hasTexture = tess.hasTexture;
            final boolean hasColor = tess.hasColor;
            final boolean hasNormals = tess.hasNormals;
            final boolean colorDisabled = tess.isColorDisabled;
            final int color = tess.color;
            final int normal = tess.normal;
            final int brightness = tess.brightness;
            final double u = tess.textureU, v = tess.textureV;

            tess.draw();
            tess.startDrawing(GL11.GL_QUADS);

            // Restore already transformed attributes directly, without baking the normal twice.
            tess.hasTexture = hasTexture;
            tess.hasColor = hasColor;
            tess.hasNormals = hasNormals;
            tess.isColorDisabled = colorDisabled;
            tess.color = color;
            tess.normal = normal;
            tess.brightness = brightness;
            tess.textureU = u;
            tess.textureV = v;
        }
        tess.hasBrightness = hasOverride;
    }

    /** Inventory lighting is accessed through its static helper. */
    private InventoryLighting() {}
}
