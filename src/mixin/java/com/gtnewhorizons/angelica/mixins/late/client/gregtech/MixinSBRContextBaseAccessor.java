package com.gtnewhorizons.angelica.mixins.late.client.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "gregtech.common.render.SBRContextBase", remap = false)
public interface MixinSBRContextBaseAccessor {
    /** Reads the inventory texture's override from the class that declares it. */
    @Accessor("hasBrightnessOverride")
    boolean angelica$hasBrightnessOverride();
}
