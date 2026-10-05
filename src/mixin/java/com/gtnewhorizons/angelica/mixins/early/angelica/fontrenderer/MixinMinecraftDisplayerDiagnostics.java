package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.LoadingFontDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/** Observes BLS startup and cached fonts without linking to or modifying its library. */
@Pseudo
@Mixin(targets = "alexiil.mods.load.MinecraftDisplayer", remap = false)
public abstract class MixinMinecraftDisplayerDiagnostics {
    // BLS 1.7.18 changed textureManager's type and removed fontRenderer; only shadow shared fields.
    @Shadow private Minecraft mc;
    @Shadow @Final private boolean preview;
    @Unique private final Map<String, FontRenderer> angelica$observedFonts = new HashMap<>();
    @Unique private TextureManager angelica$lastMcTextures;
    @Unique private FontRenderer angelica$lastMcFont;

    /** Logs the beginning of BLS initialization and the installed jar location. */
    @Inject(method = "open", at = @At("HEAD"))
    private void angelica$loadingFontOpen(CallbackInfo ci) {
        LoadingFontDiagnostics.event("BLS_OPEN", "displayer=" + LoadingFontDiagnostics.identity(this)
            + " preview=" + preview + " source=" + getClass().getProtectionDomain().getCodeSource());
    }

    /** Logs changes to Minecraft's renderer and TextureManager while BLS remains visible. */
    @Inject(method = "preDisplayScreen", at = @At("RETURN"))
    private void angelica$loadingFontObjects(CallbackInfo ci) {
        if (mc == null || preview) return;
        if (angelica$lastMcFont == mc.fontRenderer && angelica$lastMcTextures == mc.renderEngine) return;
        LoadingFontDiagnostics.event("BLS_OBJECTS_CHANGED", "oldMcFont="
            + LoadingFontDiagnostics.identity(angelica$lastMcFont) + " mcFont="
            + LoadingFontDiagnostics.identity(mc.fontRenderer) + " oldMcTextures="
            + LoadingFontDiagnostics.identity(angelica$lastMcTextures) + " mcTextures="
            + LoadingFontDiagnostics.identity(mc.renderEngine));
        angelica$lastMcFont = mc.fontRenderer;
        angelica$lastMcTextures = mc.renderEngine;
    }

    /** Logs the exact cached renderer selected for each BLS font texture, once per identity change. */
    @Inject(method = "fontRenderer", at = @At("RETURN"))
    private void angelica$loadingFontSelection(String texture, CallbackInfoReturnable<FontRenderer> cir) {
        if (preview) return;
        final FontRenderer selected = cir.getReturnValue();
        final FontRenderer previous = angelica$observedFonts.put(texture, selected);
        if (previous == selected) return;
        LoadingFontDiagnostics.event("BLS_FONT_SELECTED", "texture=" + texture + " previous="
            + LoadingFontDiagnostics.identity(previous) + " selected=" + LoadingFontDiagnostics.identity(selected)
            + " mcFont=" + LoadingFontDiagnostics.identity(mc.fontRenderer)
            + " mcTextures=" + LoadingFontDiagnostics.identity(mc.renderEngine));
    }

    /** Marks the point at which BLS starts shutting down its render thread. */
    @Inject(method = "close", at = @At("HEAD"))
    private void angelica$loadingFontCloseBegin(CallbackInfo ci) {
        LoadingFontDiagnostics.event("BLS_CLOSE_BEGIN", "cachedFonts=" + angelica$observedFonts.size());
    }

    /** Marks completion of BLS shutdown and the return to the main context. */
    @Inject(method = "close", at = @At("RETURN"))
    private void angelica$loadingFontCloseEnd(CallbackInfo ci) {
        LoadingFontDiagnostics.event("BLS_CLOSE_END", "displayer=" + LoadingFontDiagnostics.identity(this));
    }
}
