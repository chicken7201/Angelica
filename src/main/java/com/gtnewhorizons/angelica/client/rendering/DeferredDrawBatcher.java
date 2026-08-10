package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import lombok.Getter;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;

/**
 * Deferred draw batching coordinator. During rendering regions (e.g. particles),
 * intercepts tessellator.draw() calls via DirectTessellator, captures pre-packed vertex data
 * + GL state, then flushes all captured batches sorted by state key.
 * <p>
 * Vertices are pre-transformed at capture time.
 */
public class DeferredDrawBatcher {

    private static final int INITIAL_BUFFER_SIZE = 64 * 1024;

    @Getter private static boolean active = false;
    private static DeferredBatchTessellator batchTessellator;
    private static long entryStateKey;
    private static int nestingDepth;
    private static boolean textureStatePushed;
    private static boolean[] entryTexture2DEnabled;
    private static boolean[] touchedTextureUnits;

    /** Prevents construction of the static batching coordinator. */
    private DeferredDrawBatcher() {}

    /**
     * Begin deferred capture mode. Called after startDrawingQuads().
     * Pushes a DeferredBatchTessellator onto the DirectTessellator stack so that
     * subsequent tessellator.draw() calls are intercepted and accumulated.
     */
    public static void enter() {
        if (active) {
            nestingDepth++;
            return;
        }

        if (batchTessellator == null) {
            batchTessellator = new DeferredBatchTessellator(memAlloc(INITIAL_BUFFER_SIZE));
        }
        batchTessellator.clearRanges();
        batchTessellator.setParentTessellator(Tessellator.instance);
        batchTessellator.snapshotDefaultModelview();
        entryStateKey = captureStateKey();
        snapshotTextureEnableState();
        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        textureStatePushed = true;

        try {
            TessellatorManager.startCapturingDirect(batchTessellator);
            active = true;
            nestingDepth = 1;
        } catch (RuntimeException | Error e) {
            restoreEntryTextureState();
            throw e;
        }
    }

    /**
     * Exit deferred mode and flush all captured batches. Groups entries by state key
     * and vertex format, issues one draw per unique group. Restores the bracket's entry
     * state so the trailing vanilla Tessellator.draw() renders with the layer atlas.
     */
    public static void exitAndFlush() {
        if (!active) return;
        if (--nestingDepth > 0) return;

        active = false;
        final List<DeferredBatchTessellator.DrawRange> ranges = batchTessellator.getRanges();

        try {
            if (!ranges.isEmpty()) {
                ranges.sort(Comparator.comparingLong(DeferredBatchTessellator.DrawRange::stateKey));
                flushSorted(ranges);
            }
        } finally {
            try {
                TessellatorManager.stopCapturingDirect();
            } finally {
                restoreEntryState();
            }
        }
    }

    /** Flushes sorted ranges one complete deferred state at a time. */
    private static void flushSorted(List<DeferredBatchTessellator.DrawRange> ranges) {
        long currentKey = ranges.get(0).stateKey();
        int groupStart = 0;

        for (int i = 0; i <= ranges.size(); i++) {
            final boolean endOfList = (i == ranges.size());
            final boolean keyChanged = !endOfList && ranges.get(i).stateKey() != currentKey;

            if (endOfList || keyChanged) {
                applyStateKey(currentKey);
                flushGroup(ranges, groupStart, i);

                if (!endOfList) {
                    currentKey = ranges.get(i).stateKey();
                    groupStart = i;
                }
            }
        }
    }

    /**
     * Flush a group of ranges sharing the same state key.
     * Subgroups by (drawMode, format flags) since those must match for merged draws.
     */
    private static void flushGroup(List<DeferredBatchTessellator.DrawRange> ranges, int from, int to) {
        int i = from;
        while (i < to) {
            final DeferredBatchTessellator.DrawRange first = ranges.get(i);
            final int drawMode = first.drawMode();
            final int flags = first.flags();
            final int subEnd = findMergeEnd(ranges, i, to);

            int totalBytes = 0;
            int totalVertices = 0;
            for (int j = i; j < subEnd; j++) {
                final DeferredBatchTessellator.DrawRange e = ranges.get(j);
                totalBytes += e.byteLength();
                totalVertices += e.vertexCount();
            }

            drawPackedBatch(batchTessellator, ranges, i, subEnd, totalBytes, totalVertices, drawMode, flags);

            i = subEnd;
        }
    }

    /** Finds the exclusive end of a merge that preserves every source draw's primitive boundary. */
    static int findMergeEnd(List<DeferredBatchTessellator.DrawRange> ranges, int from, int to) {
        final DeferredBatchTessellator.DrawRange first = ranges.get(from);
        if (!hasCompleteIndependentPrimitives(first.drawMode(), first.vertexCount())) return from + 1;

        int end = from + 1;
        while (end < to) {
            final DeferredBatchTessellator.DrawRange candidate = ranges.get(end);
            if (candidate.drawMode() != first.drawMode() || candidate.flags() != first.flags()) break;
            if (!hasCompleteIndependentPrimitives(candidate.drawMode(), candidate.vertexCount())) break;
            end++;
        }
        return end;
    }

    /** Returns whether concatenation can preserve this draw call's complete independent primitives. */
    private static boolean hasCompleteIndependentPrimitives(int drawMode, int vertexCount) {
        return switch (drawMode) {
            case GL11.GL_POINTS -> true;
            case GL11.GL_LINES -> vertexCount % 2 == 0;
            case GL11.GL_TRIANGLES -> vertexCount % 3 == 0;
            case GL11.GL_QUADS -> vertexCount % 4 == 0;
            default -> false;
        };
    }

    /**
     * Pack ranges from a DeferredBatchTessellator into the streaming drawer's repack buffer
     * and issue a single draw call for the merged batch.
     */
    private static void drawPackedBatch(DeferredBatchTessellator source, List<DeferredBatchTessellator.DrawRange> ranges, int from, int to, int totalBytes, int totalVertices, int drawMode, int flags) {
        if (totalVertices == 0) return;

        TessellatorStreamingDrawer.ensureRepackCapacity(totalBytes);
        long writePos = TessellatorStreamingDrawer.getRepackAddress();
        for (int j = from; j < to; j++) {
            final DeferredBatchTessellator.DrawRange r = ranges.get(j);
            source.copyRange(r.byteOffset(), r.byteLength(), writePos);
            writePos += r.byteLength();
        }
        final java.nio.ByteBuffer buf = TessellatorStreamingDrawer.getRepackBuffer();
        buf.position(0);
        buf.limit(totalBytes);

        TessellatorStreamingDrawer.drawPacked(buf, drawMode, flags, totalVertices);
    }

    /**
     * Pack current GLSM state into a long key for grouping.
     * Layout: [activeTexEn:1][activeUnit:8][tex1En:1][tex0En:1][textureId:20]
     * [srcRgb:12][dstRgb:12][blendEnabled:1][depthMask:1] = 57 bits
     * GL blend constants (e.g. GL_SRC_ALPHA=0x0302) need 12 bits.
     *
     * NOT captured (uniform within a particle layer bracket — set once before startDrawingQuads,
     * not changed by individual particles):
     * - depth test enable (always GL_LEQUAL for particles)
     * - alpha test func/ref (set per-layer, not per-particle)
     * - separate blend alpha factors (srcAlpha/dstAlpha) — particles use glBlendFunc not glBlendFuncSeparate
     * - fog state (mode, color, start/end — constant for the frame)
     */
    static long captureStateKey() {
        final int activeUnit = GLStateManager.getActiveTextureUnit();
        final int textureId = GLStateManager.getTextures().getTextureUnitBindings(activeUnit).getBinding();
        final int srcRgb = GLStateManager.getBlendState().getSrcRgb();
        final int dstRgb = GLStateManager.getBlendState().getDstRgb();
        final boolean blendEnabled = GLStateManager.getBlendMode().isEnabled();
        final boolean depthMask = GLStateManager.getDepthState().isEnabled();
        final boolean tex0Enabled = GLStateManager.getTextures().getTextureUnitStates(0).isEnabled();
        final boolean tex1Enabled = GLStateManager.getTextures().getTextureUnitStates(1).isEnabled();
        final boolean activeTexEnabled = GLStateManager.getTextures().getTextureUnitStates(activeUnit).isEnabled();

        return packStateKey(activeUnit, textureId, srcRgb, dstRgb, blendEnabled, depthMask, tex0Enabled, tex1Enabled, activeTexEnabled);
    }

    /** Packs the captured texture selector, binding, and fixed-function draw state. */
    static long packStateKey(int activeUnit, int textureId, int srcRgb, int dstRgb, boolean blendEnabled, boolean depthMask, boolean tex0Enabled, boolean tex1Enabled, boolean activeTexEnabled) {
        return ((long) (textureId & 0xFFFFF) << 26)
            | ((long) (srcRgb & 0xFFF) << 14)
            | ((long) (dstRgb & 0xFFF) << 2)
            | (blendEnabled ? 2L : 0L)
            | (depthMask ? 1L : 0L)
            | (tex0Enabled ? (1L << 46) : 0L)
            | (tex1Enabled ? (1L << 47) : 0L)
            | ((long) (activeUnit & 0xFF) << 48)
            | (activeTexEnabled ? (1L << 56) : 0L);
    }

    /** Extracts the captured active texture unit. */
    static int unpackTextureUnit(long key) { return (int) ((key >> 48) & 0xFF); }
    /** Extracts the captured texture binding. */
    static int unpackTextureId(long key) { return (int) ((key >> 26) & 0xFFFFF); }
    /** Extracts the captured source RGB blend factor. */
    static int unpackSrcRgb(long key) { return (int) ((key >> 14) & 0xFFF); }
    /** Extracts the captured destination RGB blend factor. */
    static int unpackDstRgb(long key) { return (int) ((key >> 2) & 0xFFF); }
    /** Extracts whether blending was enabled. */
    static boolean unpackBlendEnabled(long key) { return (key & 2L) != 0; }
    /** Extracts the captured depth write mask. */
    static boolean unpackDepthMask(long key) { return (key & 1L) != 0; }
    /** Extracts the texture-2D enable for unit zero. */
    static boolean unpackTex0Enabled(long key) { return (key & (1L << 46)) != 0; }
    /** Extracts the texture-2D enable for unit one. */
    static boolean unpackTex1Enabled(long key) { return (key & (1L << 47)) != 0; }
    /** Extracts the texture-2D enable for the captured active unit. */
    static boolean unpackActiveTexEnabled(long key) { return (key & (1L << 56)) != 0; }

    /** Applies one deferred state after selecting the texture unit captured with its binding. */
    static void applyStateKey(long key) {
        final int textureUnit = unpackTextureUnit(key);
        final int textureId = unpackTextureId(key);
        final int srcRgb = unpackSrcRgb(key);
        final int dstRgb = unpackDstRgb(key);
        final boolean blendEnabled = unpackBlendEnabled(key);
        final boolean depthMask = unpackDepthMask(key);
        final boolean tex0Enabled = unpackTex0Enabled(key);
        final boolean tex1Enabled = unpackTex1Enabled(key);
        final boolean activeTexEnabled = unpackActiveTexEnabled(key);

        markTextureUnitTouched(0);
        markTextureUnitTouched(1);
        markTextureUnitTouched(textureUnit);
        GLStateManager.getTextures().getTextureUnitStates(0).setEnabled(tex0Enabled);
        GLStateManager.getTextures().getTextureUnitStates(1).setEnabled(tex1Enabled);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GLStateManager.getTextures().getTextureUnitStates(textureUnit).setEnabled(activeTexEnabled);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GLStateManager.glBlendFunc(srcRgb, dstRgb);
        if (blendEnabled) {
            GLStateManager.enableBlend();
        } else {
            GLStateManager.disableBlend();
        }
        GLStateManager.glDepthMask(depthMask);
    }

    /** Snapshots texture-2D enables without broadening restoration to unrelated GL_ENABLE_BIT state. */
    private static void snapshotTextureEnableState() {
        final int unitCount = GLStateManager.MAX_TEXTURE_UNITS;
        if (entryTexture2DEnabled == null || entryTexture2DEnabled.length != unitCount) {
            entryTexture2DEnabled = new boolean[unitCount];
            touchedTextureUnits = new boolean[unitCount];
        }
        Arrays.fill(touchedTextureUnits, false);
        for (int unit = 0; unit < unitCount; unit++) {
            entryTexture2DEnabled[unit] = GLStateManager.getTextures().getTextureUnitStates(unit).isEnabled();
        }
    }

    /** Records a texture unit whose enable state the batcher applies. */
    private static void markTextureUnitTouched(int unit) {
        if (textureStatePushed && unit >= 0 && unit < touchedTextureUnits.length) {
            touchedTextureUnits[unit] = true;
        }
    }

    /** Restores blend/depth state plus all texture state owned by the deferred bracket. */
    private static void restoreEntryState() {
        try {
            applyStateKey(entryStateKey);
        } finally {
            try {
                restoreEntryTextureState();
            } finally {
                nestingDepth = 0;
            }
        }
    }

    /** Restores texture bindings, selector, and only the texture enables touched by batching. */
    private static void restoreEntryTextureState() {
        try {
            if (textureStatePushed) {
                textureStatePushed = false;
                GLStateManager.glPopAttrib();
            }
        } finally {
            if (touchedTextureUnits != null) {
                for (int unit = 0; unit < touchedTextureUnits.length; unit++) {
                    if (touchedTextureUnits[unit]) {
                        GLStateManager.getTextures().getTextureUnitStates(unit).setEnabled(entryTexture2DEnabled[unit]);
                    }
                }
                Arrays.fill(touchedTextureUnits, false);
            }
        }
    }
}
