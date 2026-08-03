package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.UnicodeTextureLifecycle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextureManager.class)
public class MixinTextureManager {

    /** Prevents dynamic Unicode texture registration while TextureManager iterates its texture map. */
    @WrapMethod(method = "onResourceManagerReload")
    private void angelica$coordinateUnicodeTextures(IResourceManager resourceManager, Operation<Void> original) {
        UnicodeTextureLifecycle.beginTextureManagerReload();
        try {
            original.call(resourceManager);
        } finally {
            UnicodeTextureLifecycle.endTextureManagerReload();
        }
    }
}
