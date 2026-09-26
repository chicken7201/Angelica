package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkRenderSettingsTest {
    /** Reproduces a HUD disabling live AO while a worker continues a previously scheduled chunk. */
    @Test
    void hudChangesDoNotAffectWorkerLighting() throws Exception {
        final AtomicInteger liveOption = new AtomicInteger(2);
        final int captured = liveOption.get();
        final CountDownLatch meshing = new CountDownLatch(1);
        final CountDownLatch hudRendering = new CountDownLatch(1);
        final FutureTask<Integer> worker = new FutureTask<>(() -> {
            try (var lighting = ChunkRenderSettings.withAmbientOcclusion(captured)) {
                meshing.countDown();
                assertTrue(hudRendering.await(5, TimeUnit.SECONDS));
                return ChunkRenderSettings.ambientOcclusion(liveOption.get());
            }
        });
        new Thread(worker, "chunk-lighting-test").start();
        try {
            assertTrue(meshing.await(5, TimeUnit.SECONDS));
            liveOption.set(0);
            assertEquals(0, ChunkRenderSettings.ambientOcclusion(liveOption.get()));
            hudRendering.countDown();
            assertEquals(2, worker.get(5, TimeUnit.SECONDS));
            assertEquals(0, liveOption.get());
        } finally {
            hudRendering.countDown();
            worker.cancel(true);
        }
    }

    /** Keeps separate tasks' disabled and enabled AO snapshots isolated on simultaneous workers. */
    @Test
    void simultaneousWorkersHaveIndependentSnapshots() throws Exception {
        final CountDownLatch disabledReady = new CountDownLatch(1);
        final CountDownLatch enabledReady = new CountDownLatch(1);
        final FutureTask<Integer> disabled = new FutureTask<>(() -> {
            try (var lighting = ChunkRenderSettings.withAmbientOcclusion(0)) {
                disabledReady.countDown();
                assertTrue(enabledReady.await(5, TimeUnit.SECONDS));
                return ChunkRenderSettings.ambientOcclusion(2);
            }
        });
        new Thread(disabled, "disabled-lighting-test").start();
        try (var lighting = ChunkRenderSettings.withAmbientOcclusion(1)) {
            enabledReady.countDown();
            assertTrue(disabledReady.await(5, TimeUnit.SECONDS));
            assertEquals(1, ChunkRenderSettings.ambientOcclusion(0));
            assertEquals(0, disabled.get(5, TimeUnit.SECONDS));
        } finally {
            enabledReady.countDown();
            disabled.cancel(true);
        }
        assertEquals(2, ChunkRenderSettings.ambientOcclusion(2));
    }

    /** Restores the worker's enclosing snapshot after a nested main-thread completion scope. */
    @Test
    void nestedScopesRestoreTheirPreviousSettings() {
        try (var outer = ChunkRenderSettings.withAmbientOcclusion(2)) {
            try (var inner = ChunkRenderSettings.withAmbientOcclusion(0)) {
                assertEquals(0, ChunkRenderSettings.ambientOcclusion(1));
            }
            assertEquals(2, ChunkRenderSettings.ambientOcclusion(0));
        }
        assertEquals(1, ChunkRenderSettings.ambientOcclusion(1));
    }

    /** Prevents a failed build from leaking its AO setting into another task or GUI render. */
    @Test
    void failedBuildRestoresLiveSettings() {
        assertThrows(IllegalStateException.class, () -> {
            try (var lighting = ChunkRenderSettings.withAmbientOcclusion(0)) {
                throw new IllegalStateException("cancelled mesh");
            }
        });
        assertEquals(2, ChunkRenderSettings.ambientOcclusion(2));
    }
}
