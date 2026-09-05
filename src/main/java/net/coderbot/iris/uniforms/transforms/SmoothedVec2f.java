package net.coderbot.iris.uniforms.transforms;

import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import org.joml.Vector2f;
import org.joml.Vector2ic;

import java.util.function.Supplier;

public class SmoothedVec2f implements Supplier<Vector2f> {
	private final SmoothedFloat x;
	private final SmoothedFloat y;

	/** Creates a two-component exponential smoother driven by the frame notifier. */
	public SmoothedVec2f(float halfLifeUp, float halfLifeDown, Supplier<Vector2ic> unsmoothed, FrameUpdateNotifier updateNotifier) {
		x = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> unsmoothed.get().x(), updateNotifier);
		y = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> unsmoothed.get().y(), updateNotifier);
	}

	/** Returns the current smoothed components in a reusable result vector. */
	public Vector2f get(Vector2f result) {
		return result.set(x.getAsFloat(), y.getAsFloat());
	}

	/** Returns the current smoothed components in a new result vector. */
	@Override
	public Vector2f get() {
		return get(new Vector2f());
	}
}
