package com.gtnewhorizons.angelica.glsm.hooks;

public interface DeferredAlphaHandler {
    boolean isAlphaTestLocked();
    /** Reports whether override held applies to DeferredAlphaHandler. */
    boolean isOverrideHeld();
    boolean deferAlphaTestToggle(boolean enabled);
    boolean deferAlphaFunc(int function, float reference);
}
