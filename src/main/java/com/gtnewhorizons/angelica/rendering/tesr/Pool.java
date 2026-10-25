package com.gtnewhorizons.angelica.rendering.tesr;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reuses per-frame objects. */
final class Pool<T> {
    private final ObjectArrayList<T> entries = new ObjectArrayList<>();
    private final Supplier<T> factory;
    private final Consumer<T> reset;
    private int used;

    /** Initializes the state and dependencies used by Pool. */
    Pool(Supplier<T> factory, Consumer<T> reset) {
        this.factory = factory;
        this.reset = reset;
    }

    /** Returns the acquire result for Pool. */
    T acquire() {
        if (used == entries.size()) entries.add(factory.get());
        return entries.get(used++);
    }

    /** Returns the size result for Pool. */
    int size() { return used; }

    /** Clears Pool state for Pool. */
    void clear() {
        for (int i = 0; i < used; i++) reset.accept(entries.get(i));
        used = 0;
    }

    /** Releases Pool state for Pool. */
    void release() {
        clear();
        entries.clear();
    }
}
