package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendStartGuardTest {

    @TempDir
    Path dir;

    /** Starts a simulated SDL GPU boot using the requested requirements revision. */
    private boolean boot() {
        return boot(1);
    }

    /** Starts a simulated SDL GPU boot using the requested requirements revision. */
    private boolean boot(int revision) {
        return BackendStartGuard.begin(dir.toFile(), true, false, revision, "");
    }

    /** Returns the temporary startup-failure marker used by this test. */
    private File marker() {
        return dir.resolve("sdlgpu-starting").toFile();
    }

    /** Verifies crashes after device initialization do not count as startup failures. */
    @Test
    void bootsThatReachTheBackendNeverCountAsStrikes() {
        for (int i = 0; i < BackendStartGuard.STRIKE_LIMIT; i++) {
            assertFalse(boot());
            BackendStartGuard.arm();
            BackendStartGuard.disarm();
        }
        assertFalse(boot());
    }

    /** Verifies three consecutive device-start failures trigger the OpenGL fallback. */
    @Test
    void thirdDeathInTheDeviceWindowResets() {
        for (int i = 0; i < BackendStartGuard.STRIKE_LIMIT; i++) {
            assertFalse(boot());
            BackendStartGuard.arm();
        }
        assertTrue(boot());
        assertTrue(BackendStartGuard.tripped());
        assertFalse(marker().exists());
    }

    /** Verifies a successful boot disables the guard until device requirements change. */
    @Test
    void latchStopsArmingUntilTheRequirementsRevisionChanges() {
        assertFalse(boot());
        BackendStartGuard.arm();
        BackendStartGuard.disarm();
        BackendStartGuard.latch();
        assertFalse(boot());
        BackendStartGuard.arm();
        assertFalse(marker().exists());

        assertFalse(boot(2));
        BackendStartGuard.arm();
        assertTrue(marker().isFile());
    }
}
