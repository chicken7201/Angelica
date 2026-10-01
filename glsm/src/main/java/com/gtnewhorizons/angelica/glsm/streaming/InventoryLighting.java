package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Keeps ordinary and emissive inventory faces in one cached draw with dynamic inherited light. */
public final class InventoryLighting {
    /** Marks ordinary faces so a later brightness override cannot freeze their replay lighting. */
    public static void inheritCurrentBrightness(Tessellator tess, boolean hasOverride) {
        if (TessellatorManager.getDirectCaptureDepth() > 0 && !GLStateManager.isRecordingDisplayList()) return;
        if (!tess.isDrawing || tess.drawMode != GL11.GL_QUADS || hasOverride) return;
        // DirectTessellator promotes earlier vertices using this value when the first glow is added.
        // Both signed and unsigned short attributes decode this reserved value in the vertex shader.
        tess.brightness = -1;
    }

    /** Inventory lighting is accessed through its static helper. */
    private InventoryLighting() {}
}
