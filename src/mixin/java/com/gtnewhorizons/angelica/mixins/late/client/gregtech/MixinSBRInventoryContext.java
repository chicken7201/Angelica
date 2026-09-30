package com.gtnewhorizons.angelica.mixins.late.client.gregtech;

import com.gtnewhorizons.angelica.glsm.streaming.InventoryLighting;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "gregtech.common.render.SBRInventoryContext", remap = false)
public abstract class MixinSBRInventoryContext {
    /** Keeps cached non-emissive machine faces responsive to the held item's current lightmap. */
    @Inject(method = "setupColor(Lnet/minecraftforge/common/util/ForgeDirection;I)Lgregtech/api/render/ISBRInventoryContext;",
        at = @At("HEAD"), require = 0)
    private void angelica$separateInventoryBrightness(ForgeDirection side, int color, CallbackInfoReturnable<Object> cir) {
        InventoryLighting.separateBrightnessRuns(TessellatorManager.get(),
            ((MixinSBRContextBaseAccessor) (Object) this).angelica$hasBrightnessOverride());
    }
}
