package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.celeritas.ChunkRenderSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_ChunkLighting {
    /** Uses captured AO during chunk meshing while keeping GUI reads tied to the live option. */
    @Redirect(method = "isAmbientOcclusionEnabled", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lnet/minecraft/client/settings/GameSettings;ambientOcclusion:I"))
    private static int angelica$chunkAmbientOcclusion(GameSettings settings) {
        return ChunkRenderSettings.ambientOcclusion(settings.ambientOcclusion);
    }
}
