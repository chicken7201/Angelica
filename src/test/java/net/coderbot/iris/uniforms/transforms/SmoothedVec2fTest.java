package net.coderbot.iris.uniforms.transforms;

import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SmoothedVec2fTest {

    /** Verifies the allocation-free overload fills and returns the caller-owned vector. */
    @Test
    void writesIntoProvidedVector() {
        final Vector2i input = new Vector2i(12, 34);
        final SmoothedVec2f smoothed = new SmoothedVec2f(1.0F, 1.0F, () -> input, new FrameUpdateNotifier());
        final Vector2f target = new Vector2f();

        assertSame(target, smoothed.get(target));
        assertEquals(new Vector2f(12.0F, 34.0F), target);
    }

    /** Verifies the existing Supplier contract still returns independent result objects. */
    @Test
    void supplierGetKeepsFreshResultSemantics() {
        final Vector2i input = new Vector2i(12, 34);
        final SmoothedVec2f smoothed = new SmoothedVec2f(1.0F, 1.0F, () -> input, new FrameUpdateNotifier());

        assertNotSame(smoothed.get(), smoothed.get());
    }
}
