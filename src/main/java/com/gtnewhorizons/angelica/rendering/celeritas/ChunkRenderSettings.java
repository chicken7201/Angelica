package com.gtnewhorizons.angelica.rendering.celeritas;

/** Keeps chunk lighting independent of temporary GUI changes to the global graphics options. */
public final class ChunkRenderSettings {
    private static final ThreadLocal<Integer> AMBIENT_OCCLUSION = new ThreadLocal<>();

    /** Prevents construction of this thread-local settings utility. */
    private ChunkRenderSettings() {}

    /** Reads the chunk's captured AO level, or the live option outside a chunk build. */
    public static int ambientOcclusion(int liveValue) {
        final Integer captured = AMBIENT_OCCLUSION.get();
        return captured != null ? captured : liveValue;
    }

    /** Applies a task's AO snapshot until the returned scope is closed. */
    public static Scope withAmbientOcclusion(int level) {
        return new Scope(level);
    }

    public static final class Scope implements AutoCloseable {
        private final Integer previous;

        /** Saves the enclosing scope and installs the current task's lighting setting. */
        private Scope(int level) {
            this.previous = AMBIENT_OCCLUSION.get();
            AMBIENT_OCCLUSION.set(level);
        }

        /** Restores the enclosing setting, including after a failed or cancelled build. */
        @Override
        public void close() {
            if (this.previous == null) {
                AMBIENT_OCCLUSION.remove();
            } else {
                AMBIENT_OCCLUSION.set(this.previous);
            }
        }
    }
}
