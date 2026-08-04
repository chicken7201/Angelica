package com.gtnewhorizons.angelica.client.font;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class UnicodeTextureLifecycleTest {

    /** Verifies ordinary dynamic texture validation enters and leaves the shared lifecycle section. */
    @Test
    void permitsTextureUseOutsideReload() {
        assertTrue(UnicodeTextureLifecycle.tryBeginTextureUse());
        UnicodeTextureLifecycle.endTextureUse();
    }

    /** Verifies render threads defer instead of blocking while TextureManager owns the reload section. */
    @Test
    void defersTextureUseDuringReload() throws InterruptedException {
        final CountDownLatch reloadStarted = new CountDownLatch(1);
        final CountDownLatch finishReload = new CountDownLatch(1);
        final Thread reloadThread = new Thread(() -> {
            UnicodeTextureLifecycle.beginTextureManagerReload();
            try {
                reloadStarted.countDown();
                try {
                    finishReload.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                UnicodeTextureLifecycle.endTextureManagerReload();
            }
        }, "Unicode texture lifecycle test reload");

        reloadThread.start();
        assertTrue(reloadStarted.await(5, TimeUnit.SECONDS));
        try {
            assertTrue(UnicodeTextureLifecycle.isTextureManagerReloading());
            assertFalse(UnicodeTextureLifecycle.tryBeginTextureUse());
        } finally {
            finishReload.countDown();
            reloadThread.join(5000);
        }

        assertFalse(reloadThread.isAlive());
        assertFalse(UnicodeTextureLifecycle.isTextureManagerReloading());
        assertTrue(UnicodeTextureLifecycle.tryBeginTextureUse());
        UnicodeTextureLifecycle.endTextureUse();
    }

    /** Verifies a reload cannot invalidate a texture between Unicode batch validation and binding. */
    @Test
    void holdsTextureUseAcrossValidationAndBind() throws InterruptedException {
        final CountDownLatch reloadAttempting = new CountDownLatch(1);
        final CountDownLatch reloadEntered = new CountDownLatch(1);
        assertTrue(UnicodeTextureLifecycle.tryBeginTextureUse());
        final Thread reloadThread = new Thread(() -> {
            reloadAttempting.countDown();
            UnicodeTextureLifecycle.beginTextureManagerReload();
            try {
                reloadEntered.countDown();
            } finally {
                UnicodeTextureLifecycle.endTextureManagerReload();
            }
        }, "Unicode texture lifecycle queued reload");

        reloadThread.start();
        try {
            assertTrue(reloadAttempting.await(5, TimeUnit.SECONDS));
            assertFalse(reloadEntered.await(250, TimeUnit.MILLISECONDS));
        } finally {
            UnicodeTextureLifecycle.endTextureUse();
        }

        assertTrue(reloadEntered.await(5, TimeUnit.SECONDS));
        reloadThread.join(5000);
        assertFalse(reloadThread.isAlive());
    }
}
