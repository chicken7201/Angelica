package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.LoadingFontDiagnostics;
import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records the original startup failure through the factory shared by vanilla and BetterCrashes. */
@Mixin(CrashReport.class)
public abstract class MixinCrashReportStartupDiagnostics {
    /** Logs initialization failures before report creation, without injecting into BetterCrashes' overwritten run method. */
    @Inject(method = "makeCrashReport", at = @At("HEAD"))
    private static void angelica$logOriginalFailure(Throwable cause, String description, CallbackInfoReturnable<CrashReport> cir) {
        if ("Initializing game".equals(description)) {
            LoadingFontDiagnostics.startupFailure(description, cause);
        }
    }
}
