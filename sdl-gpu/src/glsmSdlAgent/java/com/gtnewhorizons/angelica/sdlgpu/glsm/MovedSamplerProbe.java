package com.gtnewhorizons.angelica.sdlgpu.glsm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MovedSamplerProbe implements AutoCloseable {
    private static final String COPIES = "com/gtnewhorizons/angelica/sdlgpu/sampler/MovedSamplerCopies";
    private static final String RESOURCES = "com/gtnewhorizons/angelica/sdlgpu/resource/ResourceManager";
    private static final String PROBE = "com/gtnewhorizons/angelica/sdlgpu/glsm/MovedSamplerProbe";
    private static volatile MovedSamplerProbe active;

    private final AtomicInteger refreshCalls = new AtomicInteger();
    private final ConcurrentHashMap<Long, AtomicInteger> writes = new ConcurrentHashMap<>();

    /** Initializes moved sampler probe dependencies. */
    public MovedSamplerProbe() {
        if (active != null) throw new IllegalStateException("A moved-sampler probe is already active");
        active = this;
    }

    /** Returns the observed number of moved-sampler refreshes. */
    public int refreshCalls() {
        return refreshCalls.get();
    }

    /** Starts counting writes to the selected texture. */
    public void watchWrites(long texture) {
        writes.putIfAbsent(texture, new AtomicInteger());
    }

    /** Returns the write count for a watched texture. */
    public int writesTo(long texture) {
        return writes.get(texture).get();
    }

    /** Records a refresh on the active sampler probe. */
    public static void onRefresh() {
        final MovedSamplerProbe probe = active;
        if (probe != null) probe.refreshCalls.incrementAndGet();
    }

    /** Records a write when the active probe watches this texture. */
    public static void onWrite(long texture) {
        final MovedSamplerProbe probe = active;
        if (probe == null) return;
        final AtomicInteger count = probe.writes.get(texture);
        if (count != null) count.incrementAndGet();
    }

    /** Disables this sampler probe after the test. */
    @Override
    public void close() {
        active = null;
    }

    /** Adds test probe callbacks to texture writes and sampler refreshes. */
    static byte[] instrument(String className, byte[] classfile) {
        final boolean writes = RESOURCES.equals(className);
        if (!writes && !COPIES.equals(className)) return null;
        final String methodName = writes ? "markTextureWritten" : "refresh";
        final ClassNode node = new ClassNode();
        new ClassReader(classfile).accept(node, 0);
        final var method = node.methods.stream().filter(m -> m.name.equals(methodName)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing observed method: " + className + "." + methodName));
        final InsnList hook = new InsnList();
        if (writes) hook.add(new VarInsnNode(Opcodes.LLOAD, 1));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, writes ? "onWrite" : "onRefresh", writes ? "(J)V" : "()V", false));
        method.instructions.insert(hook);
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
