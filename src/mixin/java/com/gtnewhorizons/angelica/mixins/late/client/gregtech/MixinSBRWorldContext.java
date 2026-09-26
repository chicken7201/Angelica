package com.gtnewhorizons.angelica.mixins.late.client.gregtech;

import com.gtnewhorizons.angelica.rendering.celeritas.ChunkRenderSettings;
import net.minecraft.client.settings.GameSettings;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "gregtech.common.render.SBRWorldContext", remap = false)
public abstract class MixinSBRWorldContext {
    /** Makes GregTech's direct AO field read agree with the chunk's vanilla lighting decision. */
    @Redirect(method = "populatesLightingCaches", require = 0,
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/settings/GameSettings;ambientOcclusion:I", remap = true))
    private int angelica$chunkAmbientOcclusion(GameSettings settings) {
        return ChunkRenderSettings.ambientOcclusion(settings.ambientOcclusion);
    }
}
