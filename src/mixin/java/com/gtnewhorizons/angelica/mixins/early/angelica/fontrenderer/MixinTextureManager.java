package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.UnicodeTextureLifecycle;
import com.gtnewhorizons.angelica.client.font.LoadingFontDiagnostics;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureManager.class)
public abstract class MixinTextureManager {
    /** Looks up an existing texture object without loading or allocating it. */
    @Shadow public abstract ITextureObject getTexture(ResourceLocation location);

    /** Logs non-null font texture registration without forcing lazy GL texture allocation. */
    @Inject(method = "loadTexture", at = @At("RETURN"))
    private void angelica$loadingFontTextureLoaded(ResourceLocation location, ITextureObject texture,
        CallbackInfoReturnable<Boolean> cir) {
        if (!LoadingFontDiagnostics.ENABLED || location == null || !location.getResourcePath().contains("font/")
            && !location.getResourcePath().contains("angelica_unicode_page")) return;
        LoadingFontDiagnostics.event("FONT_TEXTURE_LOADED", "textures=" + LoadingFontDiagnostics.identity(this)
            + " location=" + location + " object=" + LoadingFontDiagnostics.identity(texture)
            + " texture=" + (texture instanceof AbstractTexture ? ((AbstractTexture) texture).glTextureId : "unknown")
            + " success=" + cir.getReturnValue());
    }

    /** Logs font invalidation while accepting Forge's null logo location during loading-screen cleanup. */
    @Inject(method = "deleteTexture", at = @At("HEAD"))
    private void angelica$loadingFontTextureDeleted(ResourceLocation location, CallbackInfo ci) {
        if (!LoadingFontDiagnostics.ENABLED || location == null || !location.getResourcePath().contains("font/")
            && !location.getResourcePath().contains("angelica_unicode_page")) return;
        final ITextureObject texture = getTexture(location);
        LoadingFontDiagnostics.event("FONT_TEXTURE_DELETE", "textures=" + LoadingFontDiagnostics.identity(this)
            + " location=" + location + " object=" + LoadingFontDiagnostics.identity(texture)
            + " texture=" + (texture instanceof AbstractTexture ? ((AbstractTexture) texture).glTextureId : "unknown"));
    }

    /** Prevents dynamic Unicode texture registration while TextureManager iterates its texture map. */
    @WrapMethod(method = "onResourceManagerReload")
    private void angelica$coordinateUnicodeTextures(IResourceManager resourceManager, Operation<Void> original) {
        UnicodeTextureLifecycle.beginTextureManagerReload();
        try {
            if (LoadingFontDiagnostics.ENABLED) LoadingFontDiagnostics.event("TEXTURE_MANAGER_RELOAD_BEGIN",
                "textures=" + LoadingFontDiagnostics.identity(this) + " manager=" + LoadingFontDiagnostics.identity(resourceManager));
            original.call(resourceManager);
        } finally {
            UnicodeTextureLifecycle.endTextureManagerReload();
            if (LoadingFontDiagnostics.ENABLED) LoadingFontDiagnostics.event("TEXTURE_MANAGER_RELOAD_END",
                "textures=" + LoadingFontDiagnostics.identity(this));
        }
    }
}
