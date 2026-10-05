package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.crash.CrashReportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

/** Allows crash reports to match stack frames whose transformed classes have no source filename. */
@Mixin(CrashReportCategory.class)
public abstract class MixinCrashReportCategory_SourceFile {
    /** Compares nullable source filenames without masking the original game exception. */
    @Redirect(method = "firstTwoElementsOfStackTraceMatch", at = @At(value = "INVOKE",
        target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 1, remap = false))
    private boolean angelica$compareSourceFiles(String sourceFile, Object otherSourceFile) {
        return Objects.equals(sourceFile, otherSourceFile);
    }
}
