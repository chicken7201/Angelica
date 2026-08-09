package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.sync.GlFence;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DrawCommandSink;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.MultiDrawEmitter;

/** Rotates fenced indirect buffers so later passes cannot overwrite commands still consumed by the GPU. */
public final class BufferedIndirectMultiDrawEmitter implements MultiDrawEmitter {

    private static final int BUFFER_COUNT = 3;

    private final IndirectMultiDrawEmitter[] emitters = new IndirectMultiDrawEmitter[BUFFER_COUNT];
    private final GlFence[] fences = new GlFence[BUFFER_COUNT];
    private final DrawCommandSink sink = new DrawCommandSink() {

        /** Clears the command sink of the active indirect buffer. */
        @Override
        public void clear() {
            current.getCommandSink().clear();
        }

        /** Returns the command count of the active indirect buffer. */
        @Override
        public int size() {
            return current.getCommandSink().size();
        }

        /** Returns the largest index count queued in the active indirect buffer. */
        @Override
        public int getIndexBufferSize() {
            return current.getCommandSink().getIndexBufferSize();
        }

        /** Appends one draw command to the active indirect buffer. */
        @Override
        public void push(int baseVertex, int elementCount, long indexOffset) {
            current.getCommandSink().push(baseVertex, elementCount, indexOffset);
        }
    };

    private IndirectMultiDrawEmitter current;
    private int currentIndex = -1;

    /** Allocates a small ring of independent indirect command buffers. */
    public BufferedIndirectMultiDrawEmitter() {
        for (int i = 0; i < emitters.length; i++) {
            emitters[i] = new IndirectMultiDrawEmitter();
        }
        current = emitters[0];
    }

    /** Selects the next buffer after waiting for its previous GPU use to finish. */
    private void acquireNextBuffer() {
        currentIndex = (currentIndex + 1) % emitters.length;
        final GlFence fence = fences[currentIndex];
        if (fence != null) {
            try {
                if (!fence.sync()) {
                    throw new IllegalStateException("Timed out waiting to reuse an indirect draw buffer");
                }
            } finally {
                fence.delete();
                fences[currentIndex] = null;
            }
        }
        current = emitters[currentIndex];
    }

    /** Returns a stable sink that forwards to the buffer selected for the current pass. */
    @Override
    public DrawCommandSink getCommandSink() {
        return sink;
    }

    /** Reports that commands are assembled once for the entire terrain pass. */
    @Override
    public boolean batchesWholePass() {
        return true;
    }

    /** Acquires a safe buffer and begins assembling one terrain pass. */
    @Override
    public void beginPass(CommandList commandList, int sectionCount) {
        acquireNextBuffer();
        current.beginPass(commandList, sectionCount);
    }

    /** Uploads the assembled commands for the current pass. */
    @Override
    public void finishAssembly(CommandList commandList) {
        current.finishAssembly(commandList);
    }

    /** Selects the command range belonging to one render region. */
    @Override
    public void selectDrawRange(int firstCommand, int commandCount) {
        current.selectDrawRange(firstCommand, commandCount);
    }

    /** Returns the command count selected for the next draw. */
    @Override
    public int getPendingCommandCount() {
        return current.getPendingCommandCount();
    }

    /** Executes the selected indirect command range. */
    @Override
    public void executeBatch(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        current.executeBatch(commandList, tessellation, primitiveType);
    }

    /** Ends the pass and fences its buffer against premature reuse. */
    @Override
    public void onPassFinished(CommandList commandList) {
        current.onPassFinished(commandList);
        fences[currentIndex] = commandList.createFence();
    }

    /** Releases all fences and indirect buffer allocations. */
    @Override
    public void delete() {
        for (int i = 0; i < fences.length; i++) {
            if (fences[i] != null) {
                fences[i].delete();
                fences[i] = null;
            }
            emitters[i].delete();
        }
    }
}
