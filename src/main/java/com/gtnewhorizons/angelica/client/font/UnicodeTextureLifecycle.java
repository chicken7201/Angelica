package com.gtnewhorizons.angelica.client.font;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Coordinates short dynamic Unicode texture operations with TextureManager resource reloads. */
public final class UnicodeTextureLifecycle {

    private static final ReentrantReadWriteLock RELOAD_LOCK = new ReentrantReadWriteLock();

    /** Prevents construction of the static texture lifecycle coordinator. */
    private UnicodeTextureLifecycle() {}

    /** Marks TextureManager reload as exclusive so new dynamic texture registrations are deferred. */
    public static void beginTextureManagerReload() {
        RELOAD_LOCK.writeLock().lock();
    }

    /** Ends the exclusive TextureManager reload section. */
    public static void endTextureManagerReload() {
        RELOAD_LOCK.writeLock().unlock();
    }

    /** Tries to enter a short dynamic texture registration or validation section without blocking rendering. */
    static boolean tryBeginTextureUse() {
        return RELOAD_LOCK.readLock().tryLock();
    }

    /** Leaves a dynamic texture registration or validation section. */
    static void endTextureUse() {
        RELOAD_LOCK.readLock().unlock();
    }

    /** Reports whether TextureManager is currently reloading registered textures. */
    static boolean isTextureManagerReloading() {
        return RELOAD_LOCK.isWriteLocked();
    }
}
