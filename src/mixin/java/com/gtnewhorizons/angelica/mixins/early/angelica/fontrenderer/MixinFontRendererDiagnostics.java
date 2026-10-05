package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.LoadingFontDiagnostics;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes renderer creation and reloads only when loading-font diagnostics are enabled. */
@Mixin(FontRenderer.class)
public abstract class MixinFontRendererDiagnostics {
    @Shadow @Final private TextureManager renderEngine;
    @Shadow @Final protected ResourceLocation locationFontTexture;
    @Shadow protected int[] charWidth;
    @Unique private int angelica$lastLoadingListMode = Integer.MIN_VALUE;

    /** Logs switches between batching and display-list compatibility on the BLS thread. */
    @Inject(method = {"drawString(Ljava/lang/String;IIIZ)I", "renderString"}, at = @At("HEAD"))
    private void angelica$loadingFontRoute(String text, int x, int y, int color, boolean shadow,
        CallbackInfoReturnable<Integer> cir) {
        if (!Thread.currentThread().getName().equals("BLS Splash renderer")) return;
        final int listMode = GLStateManager.getListMode();
        if (listMode == angelica$lastLoadingListMode) return;
        angelica$lastLoadingListMode = listMode;
        LoadingFontDiagnostics.event("BLS_RENDER_ROUTE", "renderer=" + LoadingFontDiagnostics.identity(this)
            + " listMode=" + listMode + " batched=" + (listMode == 0));
    }

    /** Logs each renderer's construction, texture manager, and relevant startup caller. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void angelica$loadingFontCreated(CallbackInfo ci) {
        LoadingFontDiagnostics.event("FONT_CREATED", "renderer=" + LoadingFontDiagnostics.identity(this)
            + " textures=" + LoadingFontDiagnostics.identity(renderEngine) + " font=" + locationFontTexture
            + " caller=" + LoadingFontDiagnostics.caller());
    }

    /** Logs the start of a reload on the specific FontRenderer object. */
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void angelica$loadingFontReloadBegin(IResourceManager manager, CallbackInfo ci) {
        LoadingFontDiagnostics.event("FONT_RELOAD_BEGIN", "renderer=" + LoadingFontDiagnostics.identity(this)
            + " manager=" + LoadingFontDiagnostics.identity(manager));
    }

    /** Logs completion and the identity of the renderer's updated width table. */
    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void angelica$loadingFontReloadEnd(IResourceManager manager, CallbackInfo ci) {
        LoadingFontDiagnostics.event("FONT_RELOAD_END", "renderer=" + LoadingFontDiagnostics.identity(this)
            + " widths=" + LoadingFontDiagnostics.identity(charWidth)
            + " font=" + locationFontTexture
            + " textures=" + LoadingFontDiagnostics.identity(renderEngine));
    }
}
