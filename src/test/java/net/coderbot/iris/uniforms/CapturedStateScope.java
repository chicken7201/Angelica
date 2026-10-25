package net.coderbot.iris.uniforms;

public final class CapturedStateScope implements AutoCloseable {

    /** Initializes the state and dependencies used by CapturedStateScope. */
    public CapturedStateScope() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.pushCurrentEntityAndItem();
        state.pushCurrentBlockEntity();
        state.pushCurrentEntityColor();
    }

    /** Updates close state for CapturedStateScope. */
    @Override
    public void close() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.popCurrentEntityColor();
        state.popCurrentBlockEntity();
        state.popCurrentEntityAndItem();
    }
}
