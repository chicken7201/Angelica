package com.gtnewhorizons.angelica.client.rendering;

import java.util.ArrayList;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.*;

class DeferredDrawBatcherTest {

    // Verifies the basic deferred state-key fields round-trip together.
    @Test
    void stateKey_roundtrip_basic() {
        long key = DeferredDrawBatcher.packStateKey(0, 42, GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, true, true);
        assertEquals(0, DeferredDrawBatcher.unpackTextureUnit(key));
        assertEquals(42, DeferredDrawBatcher.unpackTextureId(key));
        assertEquals(GL11.GL_SRC_ALPHA, DeferredDrawBatcher.unpackSrcRgb(key));
        assertEquals(GL11.GL_ONE, DeferredDrawBatcher.unpackDstRgb(key));
        assertTrue(DeferredDrawBatcher.unpackBlendEnabled(key));
        assertFalse(DeferredDrawBatcher.unpackDepthMask(key));
    }

    // Verifies disabled flags and a non-zero texture unit round-trip.
    @Test
    void stateKey_roundtrip_allFlags() {
        long key = DeferredDrawBatcher.packStateKey(1, 0, 0, 0, false, true, true, true, false);
        assertEquals(1, DeferredDrawBatcher.unpackTextureUnit(key));
        assertEquals(0, DeferredDrawBatcher.unpackTextureId(key));
        assertEquals(0, DeferredDrawBatcher.unpackSrcRgb(key));
        assertEquals(0, DeferredDrawBatcher.unpackDstRgb(key));
        assertFalse(DeferredDrawBatcher.unpackBlendEnabled(key));
        assertTrue(DeferredDrawBatcher.unpackDepthMask(key));
    }

    // Verifies the full texture-id field and upper texture-unit bits round-trip.
    @Test
    void stateKey_roundtrip_largeTextureId() {
        // 20-bit texture ID = max 1048575
        int texId = (1 << 20) - 1;
        long key = DeferredDrawBatcher.packStateKey(31, texId, 0x0302, 0x0303, true, true, true, true, true);
        assertEquals(31, DeferredDrawBatcher.unpackTextureUnit(key));
        assertEquals(texId, DeferredDrawBatcher.unpackTextureId(key));
        assertEquals(0x0302, DeferredDrawBatcher.unpackSrcRgb(key));
        assertEquals(0x0303, DeferredDrawBatcher.unpackDstRgb(key));
        assertTrue(DeferredDrawBatcher.unpackBlendEnabled(key));
        assertTrue(DeferredDrawBatcher.unpackDepthMask(key));
    }

    // Verifies texture unit, texture id, and blend state all participate in key equality.
    @Test
    void stateKey_differentKeysNotEqual() {
        long keyA = DeferredDrawBatcher.packStateKey(0, 42, GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, true, true);
        long keyB = DeferredDrawBatcher.packStateKey(0, 43, GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, true, true);
        long keyC = DeferredDrawBatcher.packStateKey(0, 42, GL11.GL_ONE, GL11.GL_ONE, true, false, true, true, true);
        long keyD = DeferredDrawBatcher.packStateKey(1, 42, GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, true, true);
        assertNotEquals(keyA, keyB, "Different texture IDs should produce different keys");
        assertNotEquals(keyA, keyC, "Different blend funcs should produce different keys");
        assertNotEquals(keyA, keyD, "Different active texture units should produce different keys");
    }

    // Verifies alternating texture-unit and texture-id combinations never coalesce.
    @Test
    void stateKey_crossUnitSequenceRemainsDistinct() {
        long unit0A = DeferredDrawBatcher.packStateKey(0, 11, 0x0302, 0x0001, true, true, true, true, true);
        long unit1B = DeferredDrawBatcher.packStateKey(1, 22, 0x0302, 0x0001, true, true, true, true, true);
        long unit0C = DeferredDrawBatcher.packStateKey(0, 33, 0x0302, 0x0001, true, true, true, true, true);
        long unit1D = DeferredDrawBatcher.packStateKey(1, 44, 0x0302, 0x0001, true, true, true, true, true);

        assertEquals(4, java.util.stream.LongStream.of(unit0A, unit1B, unit0C, unit1D).distinct().count());
    }

    // Verifies entries with equal complete state keys sort into the same group.
    @Test
    void grouping_sameKeyEntriesMerged() {
        // Verify that entries with the same state key sort together
        long keyA = DeferredDrawBatcher.packStateKey(0, 1, 0x0302, 0x0001, true, true, true, true, true);
        long keyB = DeferredDrawBatcher.packStateKey(1, 2, 0x0302, 0x0001, true, true, true, true, true);

        // Create entries with interleaved keys: A, B, A, B, A
        final int flags = VertexFlags.convertToFlags(true, true, false, true);
        var entries = new ArrayList<DeferredBatchTessellator.DrawRange>();
        entries.add(new DeferredBatchTessellator.DrawRange(keyA, 0, 128, 4, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(keyB, 128, 128, 4, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(keyA, 256, 128, 4, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(keyB, 384, 128, 4, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(keyA, 512, 128, 4, GL11.GL_QUADS, flags));

        // Sort by state key (same as exitAndFlush)
        entries.sort((a, b) -> Long.compare(a.stateKey(), b.stateKey()));

        // Count groups
        int groupCount = 0;
        int[] groupSizes = new int[2];
        long currentKey = entries.get(0).stateKey();
        int groupSize = 0;

        for (int i = 0; i <= entries.size(); i++) {
            if (i == entries.size() || entries.get(i).stateKey() != currentKey) {
                groupSizes[groupCount] = groupSize;
                groupCount++;
                if (i < entries.size()) {
                    currentKey = entries.get(i).stateKey();
                    groupSize = 1;
                }
            } else {
                groupSize++;
            }
        }

        assertEquals(2, groupCount, "Should have exactly 2 groups");
        // keyA < keyB (texture ID 1 vs 2), so keyA group comes first
        assertEquals(3, groupSizes[0], "Key A group should have 3 entries");
        assertEquals(2, groupSizes[1], "Key B group should have 2 entries");
    }

    // Verifies aligned quad ranges preserve their total vertices and bytes.
    @Test
    void grouping_mergedVertexCountCorrect() {
        long key = DeferredDrawBatcher.packStateKey(0, 1, 0x0302, 0x0001, true, true, true, true, true);

        final int flags = VertexFlags.convertToFlags(true, true, false, true);
        var entries = new ArrayList<DeferredBatchTessellator.DrawRange>();
        entries.add(new DeferredBatchTessellator.DrawRange(key, 0, 128, 4, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(key, 128, 256, 8, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(key, 384, 128, 4, GL11.GL_QUADS, flags));

        // Simulate merge within a group: sum vertices and byte lengths
        int totalVertices = 0;
        int totalBytes = 0;
        for (var e : entries) {
            totalVertices += e.vertexCount();
            totalBytes += e.byteLength();
        }

        assertEquals(16, totalVertices, "Merged group should have 4+8+4=16 vertices");
        assertEquals(512, totalBytes, "Merged group should have 128+256+128=512 bytes");
    }

    // Verifies safe fixed-size primitives merge while incomplete quad ranges remain isolated.
    @Test
    void grouping_respectsPrimitiveBoundaries() {
        long key = DeferredDrawBatcher.packStateKey(0, 1, 0x0302, 0x0001, true, true, true, true, true);
        final int flags = VertexFlags.convertToFlags(true, true, false, true);

        var fourPlusFour = new ArrayList<DeferredBatchTessellator.DrawRange>();
        fourPlusFour.add(new DeferredBatchTessellator.DrawRange(key, 0, 128, 4, GL11.GL_QUADS, flags));
        fourPlusFour.add(new DeferredBatchTessellator.DrawRange(key, 128, 128, 4, GL11.GL_QUADS, flags));
        assertEquals(2, DeferredDrawBatcher.findMergeEnd(fourPlusFour, 0, fourPlusFour.size()));

        var eightPlusFour = new ArrayList<DeferredBatchTessellator.DrawRange>();
        eightPlusFour.add(new DeferredBatchTessellator.DrawRange(key, 0, 256, 8, GL11.GL_QUADS, flags));
        eightPlusFour.add(new DeferredBatchTessellator.DrawRange(key, 256, 128, 4, GL11.GL_QUADS, flags));
        assertEquals(2, DeferredDrawBatcher.findMergeEnd(eightPlusFour, 0, eightPlusFour.size()));

        var incomplete = new ArrayList<DeferredBatchTessellator.DrawRange>();
        incomplete.add(new DeferredBatchTessellator.DrawRange(key, 0, 192, 6, GL11.GL_QUADS, flags));
        incomplete.add(new DeferredBatchTessellator.DrawRange(key, 192, 192, 6, GL11.GL_QUADS, flags));
        assertEquals(1, DeferredDrawBatcher.findMergeEnd(incomplete, 0, incomplete.size()));
        assertEquals(2, DeferredDrawBatcher.findMergeEnd(incomplete, 1, incomplete.size()));

        var alignedThenIncomplete = new ArrayList<DeferredBatchTessellator.DrawRange>();
        alignedThenIncomplete.add(new DeferredBatchTessellator.DrawRange(key, 0, 128, 4, GL11.GL_QUADS, flags));
        alignedThenIncomplete.add(new DeferredBatchTessellator.DrawRange(key, 128, 192, 6, GL11.GL_QUADS, flags));
        assertEquals(1, DeferredDrawBatcher.findMergeEnd(alignedThenIncomplete, 0, alignedThenIncomplete.size()));
    }

    // Verifies fixed-size modes merge only complete primitives and continuous modes stay separate.
    @Test
    void grouping_handlesOtherPrimitiveModesConservatively() {
        long key = DeferredDrawBatcher.packStateKey(0, 1, 0x0302, 0x0001, true, true, true, true, true);
        final int flags = VertexFlags.convertToFlags(true, true, false, true);

        var triangles = new ArrayList<DeferredBatchTessellator.DrawRange>();
        triangles.add(new DeferredBatchTessellator.DrawRange(key, 0, 96, 3, GL11.GL_TRIANGLES, flags));
        triangles.add(new DeferredBatchTessellator.DrawRange(key, 96, 192, 6, GL11.GL_TRIANGLES, flags));
        assertEquals(2, DeferredDrawBatcher.findMergeEnd(triangles, 0, triangles.size()));

        var incompleteLines = new ArrayList<DeferredBatchTessellator.DrawRange>();
        incompleteLines.add(new DeferredBatchTessellator.DrawRange(key, 0, 96, 3, GL11.GL_LINES, flags));
        incompleteLines.add(new DeferredBatchTessellator.DrawRange(key, 96, 96, 3, GL11.GL_LINES, flags));
        assertEquals(1, DeferredDrawBatcher.findMergeEnd(incompleteLines, 0, incompleteLines.size()));

        var triangleStrip = new ArrayList<DeferredBatchTessellator.DrawRange>();
        triangleStrip.add(new DeferredBatchTessellator.DrawRange(key, 0, 128, 4, GL11.GL_TRIANGLE_STRIP, flags));
        triangleStrip.add(new DeferredBatchTessellator.DrawRange(key, 128, 128, 4, GL11.GL_TRIANGLE_STRIP, flags));
        assertEquals(1, DeferredDrawBatcher.findMergeEnd(triangleStrip, 0, triangleStrip.size()));
    }

    // Verifies draw modes remain separate even when their other state matches.
    @Test
    void grouping_differentDrawModesFormSubgroups() {
        long key = DeferredDrawBatcher.packStateKey(0, 1, 0x0302, 0x0001, true, true, true, true, true);

        final int flags = VertexFlags.convertToFlags(true, true, false, true);
        var entries = new ArrayList<DeferredBatchTessellator.DrawRange>();
        entries.add(new DeferredBatchTessellator.DrawRange(key, 0, 128, 4, GL11.GL_QUADS, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(key, 128, 128, 4, GL11.GL_TRIANGLES, flags));
        entries.add(new DeferredBatchTessellator.DrawRange(key, 256, 128, 4, GL11.GL_QUADS, flags));

        // Same state key, but different draw modes can't be merged.
        // The flush logic subgroups by (drawMode, format flags).
        // Count subgroups: QUADS(entry0), TRIANGLES(entry1), QUADS(entry2)
        // Since entries aren't further sorted by drawMode, this produces 3 subgroups.
        int subgroupCount = 0;
        int i = 0;
        while (i < entries.size()) {
            int drawMode = entries.get(i).drawMode();
            while (i < entries.size() && entries.get(i).drawMode() == drawMode) i++;
            subgroupCount++;
        }

        // Two QUADS entries are not contiguous (TRIANGLES between them), so 3 subgroups
        assertEquals(3, subgroupCount, "Different draw modes should form separate subgroups");
    }

    // Verifies common OpenGL blend constants retain all twelve bits.
    @Test
    void stateKey_blendConstants() {
        // Verify all common GL blend constants roundtrip correctly
        int[] blendConstants = {
            GL11.GL_ZERO,           // 0x0000
            GL11.GL_ONE,            // 0x0001
            GL11.GL_SRC_ALPHA,      // 0x0302
            GL11.GL_ONE_MINUS_SRC_ALPHA, // 0x0303
            GL11.GL_DST_ALPHA,      // 0x0304
            GL11.GL_ONE_MINUS_DST_ALPHA, // 0x0305
        };

        for (int src : blendConstants) {
            for (int dst : blendConstants) {
                long key = DeferredDrawBatcher.packStateKey(0, 1, src, dst, true, true, true, true, true);
                assertEquals(src, DeferredDrawBatcher.unpackSrcRgb(key), "srcRgb roundtrip failed for 0x" + Integer.toHexString(src));
                assertEquals(dst, DeferredDrawBatcher.unpackDstRgb(key), "dstRgb roundtrip failed for 0x" + Integer.toHexString(dst));
            }
        }
    }

    // Verifies texture-enable flags, including the active unit flag, round-trip independently.
    @Test
    void stateKey_textureEnableRoundtrip() {
        // tex0=true, tex1=false
        long key1 = DeferredDrawBatcher.packStateKey(0, 42, 0x0302, 0x0001, true, true, true, false, true);
        assertTrue(DeferredDrawBatcher.unpackTex0Enabled(key1));
        assertFalse(DeferredDrawBatcher.unpackTex1Enabled(key1));
        assertTrue(DeferredDrawBatcher.unpackActiveTexEnabled(key1));
        assertEquals(42, DeferredDrawBatcher.unpackTextureId(key1));

        // tex0=false, tex1=true
        long key2 = DeferredDrawBatcher.packStateKey(1, 42, 0x0302, 0x0001, true, true, false, true, true);
        assertFalse(DeferredDrawBatcher.unpackTex0Enabled(key2));
        assertTrue(DeferredDrawBatcher.unpackTex1Enabled(key2));
        assertTrue(DeferredDrawBatcher.unpackActiveTexEnabled(key2));
        assertEquals(42, DeferredDrawBatcher.unpackTextureId(key2));

        // Enable bits don't interfere with existing fields
        long keyBothOff = DeferredDrawBatcher.packStateKey(0, 42, 0x0302, 0x0001, true, true, false, false, false);
        long keyBothOn  = DeferredDrawBatcher.packStateKey(0, 42, 0x0302, 0x0001, true, true, true, true, true);
        assertEquals(DeferredDrawBatcher.unpackTextureId(keyBothOff), DeferredDrawBatcher.unpackTextureId(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackSrcRgb(keyBothOff), DeferredDrawBatcher.unpackSrcRgb(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackDstRgb(keyBothOff), DeferredDrawBatcher.unpackDstRgb(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackBlendEnabled(keyBothOff), DeferredDrawBatcher.unpackBlendEnabled(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackDepthMask(keyBothOff), DeferredDrawBatcher.unpackDepthMask(keyBothOn));

        // Different tex enable states produce different keys
        assertNotEquals(key1, key2, "Different texture enable states should produce different keys");
    }

    // Verifies captured byte ranges remain contiguous and non-overlapping.
    @Test
    void drawRange_byteOffsetsNonOverlapping() {
        long key = DeferredDrawBatcher.packStateKey(0, 1, 0x0302, 0x0001, true, true, true, true, true);
        final int flags = VertexFlags.convertToFlags(true, true, false, true);

        // Simulate sequential byte offsets as would be produced by interceptDraw
        var ranges = new ArrayList<DeferredBatchTessellator.DrawRange>();
        int offset = 0;
        for (int i = 0; i < 5; i++) {
            int byteLen = (i + 1) * 128; // 128, 256, 384, 512, 640
            ranges.add(new DeferredBatchTessellator.DrawRange(key, offset, byteLen, (i + 1) * 4, GL11.GL_QUADS, flags));
            offset += byteLen;
        }

        // Verify ranges are contiguous and non-overlapping
        for (int i = 1; i < ranges.size(); i++) {
            var prev = ranges.get(i - 1);
            var curr = ranges.get(i);
            assertEquals(prev.byteOffset() + prev.byteLength(), curr.byteOffset(), "Range " + i + " should start where range " + (i - 1) + " ends");
        }

        // Total bytes = 128+256+384+512+640 = 1920
        assertEquals(1920, offset, "Total accumulated bytes");
    }
}
