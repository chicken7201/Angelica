package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.tileentity.TileEntitySign;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntitySignRenderer.class)
public abstract class MixinTileEntitySignRenderer {

    /** Draws the sign model through the cached mesh renderer. */
    @Redirect(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelSign;renderSign()V"))
    private void angelica$renderBoardCached(ModelSign model) {
        VanillaModelMeshes.renderSign(model);
    }

    /** Supplies the client font when a worldless sign preview has no dispatcher font. */
    @ModifyExpressionValue(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySignRenderer;func_147498_b()Lnet/minecraft/client/gui/FontRenderer;"))
    private FontRenderer angelica$signPreviewFont(FontRenderer fontRenderer) {
        return fontRenderer != null ? fontRenderer : Minecraft.getMinecraft().fontRenderer;
    }

    /** Begins deferred sign text rendering after its font has been resolved. */
    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V", ordinal = 0, remap = false, shift = At.Shift.AFTER))
    private void angelica$beginSignTextBatch(TileEntitySign sign, double x, double y, double z, float partialTicks, CallbackInfo ci, @Local(ordinal = 0) FontRenderer fontRenderer) {
        final BatchingFontRenderer batcher = ((FontRendererAccessor) fontRenderer).angelica$getBatcher();
        if (batcher != null) {
            batcher.beginDeferredBatch();
        }
    }

    /** Finishes deferred sign text rendering before the renderer restores depth writes. */
    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V", ordinal = 1, remap = false, shift = At.Shift.BEFORE))
    private void angelica$endSignTextBatch(TileEntitySign sign, double x, double y, double z, float partialTicks, CallbackInfo ci, @Local(ordinal = 0) FontRenderer fontRenderer) {
        final BatchingFontRenderer batcher = ((FontRendererAccessor) fontRenderer).angelica$getBatcher();
        if (batcher != null) {
            batcher.endDeferredBatch();
        }
    }
}
