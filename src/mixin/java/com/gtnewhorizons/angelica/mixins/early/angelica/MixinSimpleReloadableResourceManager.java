package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.client.font.LoadingFontDiagnostics;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SimpleReloadableResourceManager.class)
public class MixinSimpleReloadableResourceManager {

    /** Logs the resource generation boundary before startup reload listeners run. */
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void angelica$loadingFontReloadBegin(List<IResourcePack> packs, CallbackInfo ci) {
        LoadingFontDiagnostics.resourceReload(this, true);
    }

    /** Logs reload completion and synchronizes the existing loading checkpoint. */
    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void angelica$fireSyncCheckpointOnReload(List<IResourcePack> packs, CallbackInfo ci) {
        LoadingFontDiagnostics.resourceReload(this, false);
        if (GLSMHooks.LOADING_CHECKPOINT.hasListeners()) {
            GLSMHooks.loadingCheckpointEvent.requiresSync = true;
            GLSMHooks.LOADING_CHECKPOINT.post(GLSMHooks.loadingCheckpointEvent);
            GLSMHooks.loadingCheckpointEvent.requiresSync = false;
        }
    }
}
