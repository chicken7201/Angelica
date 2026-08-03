package com.gtnewhorizons.angelica.client.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class UnicodeGlyphMetricsTest {

    private static final float EPSILON = 0.000001F;
    private static final String REQUIRED_GLYPHS = "0123456789OreABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        + "₀₁₂₃₄₅₆₇₈₉H₂SO₄U₂₃₈◀◁▶▷❄";

    /** Verifies every requested glyph keeps both horizontal edge texel centers in its UV range. */
    @Test
    void includesRequestedGlyphEdgeTexels() {
        final byte fullCellBounds = packBounds(0, 15);

        for (int i = 0; i < REQUIRED_GLYPHS.length(); i++) {
            final char chr = REQUIRED_GLYPHS.charAt(i);
            final float cellLeft = chr % 16 * UnicodeGlyphMetrics.CELL_SIZE;
            final float firstTexelCenter = (cellLeft + 0.5F) / UnicodeGlyphMetrics.ATLAS_SIZE;
            final float lastTexelCenter = (cellLeft + UnicodeGlyphMetrics.CELL_SIZE - 0.5F)
                / UnicodeGlyphMetrics.ATLAS_SIZE;
            final float cellTop = (chr & 255) / 16 * UnicodeGlyphMetrics.CELL_SIZE;
            final float topTexelCenter = (cellTop + 0.5F) / UnicodeGlyphMetrics.ATLAS_SIZE;
            final float bottomTexelCenter = (cellTop + UnicodeGlyphMetrics.CELL_SIZE - 0.5F)
                / UnicodeGlyphMetrics.ATLAS_SIZE;
            final float uStart = UnicodeGlyphMetrics.getUStart(chr, fullCellBounds);
            final float uEnd = uStart + UnicodeGlyphMetrics.getUSize(fullCellBounds);
            final float vStart = UnicodeGlyphMetrics.getVStart(chr);
            final float vEnd = vStart + UnicodeGlyphMetrics.getVSize();

            assertTrue(uStart <= firstTexelCenter, Character.toString(chr));
            assertTrue(uEnd >= lastTexelCenter, Character.toString(chr));
            assertTrue(vStart <= topTexelCenter, Character.toString(chr));
            assertTrue(vEnd >= bottomTexelCenter, Character.toString(chr));
        }
    }

    /** Verifies glyph UVs and anti-aliasing bounds remain inside one fixed atlas cell. */
    @Test
    void keepsUvAndSamplesInsideAtlasCell() {
        final char chr = '❄';
        final byte packedBounds = packBounds(2, 13);
        final float uStart = UnicodeGlyphMetrics.getUStart(chr, packedBounds);
        final float uEnd = uStart + UnicodeGlyphMetrics.getUSize(packedBounds);
        final float sampleUStart = UnicodeGlyphMetrics.getSampleUStart(chr);
        final float sampleUEnd = UnicodeGlyphMetrics.getSampleUEnd(chr);

        assertTrue(sampleUStart <= uStart);
        assertTrue(sampleUEnd >= uEnd);
        assertTrue(sampleUEnd <= sampleUStart + (float) UnicodeGlyphMetrics.CELL_SIZE / UnicodeGlyphMetrics.ATLAS_SIZE);
        assertTrue(UnicodeGlyphMetrics.getSampleVStart(chr) <= UnicodeGlyphMetrics.getVStart(chr));
        assertTrue(UnicodeGlyphMetrics.getSampleVEnd(chr)
            >= UnicodeGlyphMetrics.getVStart(chr) + UnicodeGlyphMetrics.getVSize());
    }

    /** Verifies the first and last atlas cells never produce UVs outside the texture. */
    @Test
    void keepsOuterAtlasCellsInsideTexture() {
        final byte fullCellBounds = packBounds(0, 15);
        final char[] edgeCells = { '\u0000', '\u000F', '\u00F0', '\u00FF', '\uFFFF' };

        for (char chr : edgeCells) {
            final float uStart = UnicodeGlyphMetrics.getUStart(chr, fullCellBounds);
            final float uEnd = uStart + UnicodeGlyphMetrics.getUSize(fullCellBounds);
            final float vStart = UnicodeGlyphMetrics.getVStart(chr);
            final float vEnd = vStart + UnicodeGlyphMetrics.getVSize();

            assertTrue(uStart >= 0.0F);
            assertTrue(vStart >= 0.0F);
            assertTrue(uEnd <= 1.0F);
            assertTrue(vEnd <= 1.0F);
            assertTrue(UnicodeGlyphMetrics.getSampleUEnd(chr) <= 1.0F);
            assertTrue(UnicodeGlyphMetrics.getSampleVEnd(chr) <= 1.0F);
        }
    }

    /** Verifies wide right bearings remain encoded instead of falling back to a full-width glyph. */
    @Test
    void preservesOfficialWideGlyphBoundsBehavior() {
        final byte packedBounds = packBounds(4, 12);

        assertTrue(UnicodeGlyphMetrics.isAvailable(packedBounds));
        assertEquals(4, UnicodeGlyphMetrics.getStartColumn(packedBounds));
        assertEquals(13, UnicodeGlyphMetrics.getEndColumnExclusive(packedBounds));
        assertEquals(5.5F, UnicodeGlyphMetrics.getXAdvance(packedBounds), EPSILON);
    }

    /** Verifies atlas sample padding does not increase the string advance. */
    @Test
    void keepsAdvanceIndependentFromAtlasSampleBounds() {
        final char chr = '₂';
        final byte packedBounds = packBounds(4, 12);
        final float sampleWidth = UnicodeGlyphMetrics.getSampleUEnd(chr)
            - UnicodeGlyphMetrics.getSampleUStart(chr);

        assertEquals(5.5F, UnicodeGlyphMetrics.getXAdvance(packedBounds), EPSILON);
        assertEquals(4.49F, UnicodeGlyphMetrics.getGlyphWidth(packedBounds) - 1.0F, EPSILON);
        assertTrue(sampleWidth > UnicodeGlyphMetrics.getUSize(packedBounds));
    }

    /** Verifies empty or inverted bounds are treated as missing glyphs. */
    @Test
    void rejectsMissingAndInvalidGlyphBounds() {
        assertFalse(UnicodeGlyphMetrics.isAvailable((byte) 0));
        assertFalse(UnicodeGlyphMetrics.isAvailable(packBounds(15, 0)));
        assertEquals(0.0F, UnicodeGlyphMetrics.getXAdvance((byte) 0), EPSILON);
    }

    /** Verifies glyph_sizes.bin is fully consumed across short InputStream reads. */
    @Test
    void readsCompleteGlyphWidthTableAcrossShortChunks() throws IOException {
        final byte[] expected = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
        }

        final byte[] actual = FontProviderUnicode.readGlyphWidths(
            new ChunkedInputStream(new ByteArrayInputStream(expected), 37));

        assertArrayEquals(expected, actual);
    }

    /** Verifies a truncated glyph_sizes.bin cannot leave stale widths in the cache. */
    @Test
    void rejectsTruncatedGlyphWidthTable() {
        final byte[] truncated = new byte[UnicodeGlyphMetrics.GLYPH_COUNT - 1];
        Arrays.fill(truncated, (byte) 1);

        assertThrows(EOFException.class,
            () -> FontProviderUnicode.readGlyphWidths(new ByteArrayInputStream(truncated)));
    }

    /** Packs inclusive glyph bitmap columns into the glyph_sizes.bin representation. */
    private static byte packBounds(int startColumn, int endColumn) {
        return (byte) (startColumn << 4 | endColumn);
    }

    private static final class ChunkedInputStream extends FilterInputStream {

        private final int chunkSize;

        /** Creates a stream that limits each bulk read to the requested chunk size. */
        private ChunkedInputStream(InputStream inputstream, int chunkSize) {
            super(inputstream);
            this.chunkSize = chunkSize;
        }

        /** Limits bulk reads so readFully must perform multiple reads. */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return super.read(buffer, offset, Math.min(length, this.chunkSize));
        }
    }
}
