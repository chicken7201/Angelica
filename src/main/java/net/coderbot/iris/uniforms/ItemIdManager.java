package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * Helper class to manage the currentRenderedItem ID uniform.
 */
public class ItemIdManager {
    /** Saves the caller's item identity before a nested render. */
    public static void pushItemId() {
        CapturedRenderingState.INSTANCE.pushCurrentRenderedItem();
    }

    /** Restores the item identity saved by the enclosing render. */
    public static void popItemId() {
        CapturedRenderingState.INSTANCE.popCurrentRenderedItem();
    }

    /** Applies item alpha testing while preserving the caller's world or HUD lightmap state. */
    public static int beginCutout(ItemStack stack) {
        pushItemId();
        final int depth = GLStateManager.pushState(StateSet.CUTOUT);
        try {
            GLStateManager.enableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            setItemId(stack);
        } catch (Throwable t) {
            endCutout(depth);
            throw t;
        }
        return depth;
    }

    /** Restores the item identity and GL state at the saved cutout depth. */
    public static void endCutout(int depth) {
        popItemId();
        GLStateManager.popStateTo(depth);
    }

    /**
     * Set the item ID for an armor piece or held item.
     * If the ItemStack is null/empty, resets to 0.
     * Otherwise, looks up the material ID and sets it.
     *
     * @param itemStack The armor or item being rendered
     */
    public static void setItemId(ItemStack itemStack) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemStack);
    }

    /**
     * Set the item ID for a Block rendered outside of terrain.
     */
    public static void setBlockId(Block block, int metadata) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedBlockItem(block, metadata);
    }

    /**
     * Reset the item ID to 0.
     * Used when entering/exiting render sections or before glint rendering.
     */
    public static void resetItemId() {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
    }

    /**
     * The currently bound item ID.
     */
    public static int getItemId() {
        return CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
    }

    /**
     * Set an already-resolved item ID without performing a lookup.
     */
    public static void setItemIdRaw(int id) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /** Reports whether an Iris shader pipeline is currently drawing world geometry. */
    public static boolean isWorldRenderActive() {
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline instanceof DeferredWorldRenderingPipeline drp && drp.isRenderingLevelGeometry();
    }
}
