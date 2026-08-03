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

    /** Verifies valid packed bounds continue to mark glyphs supplied by GTNH and resource packs as available. */
    @Test
    void acceptsValidGlyphSizeEntries() {
        final byte packedBounds = packBounds(4, 12);

        assertTrue(UnicodeGlyphMetrics.isAvailable(packedBounds));
        assertEquals(4, UnicodeGlyphMetrics.getStartColumn(packedBounds));
        assertEquals(13, UnicodeGlyphMetrics.getEndColumnExclusive(packedBounds));
        assertEquals(9, UnicodeGlyphMetrics.getBitmapWidth(packedBounds));
    }

    /** Verifies empty or inverted glyph_sizes.bin entries remain unavailable. */
    @Test
    void rejectsMissingAndInvalidGlyphSizeEntries() {
        assertFalse(UnicodeGlyphMetrics.isAvailable((byte) 0));
        assertFalse(UnicodeGlyphMetrics.isAvailable(packBounds(15, 0)));
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
