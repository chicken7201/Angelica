package com.gtnewhorizons.angelica.client.font;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FontProviderBitmapTest {

    /** Accepts current Modernity/bundled sheets and uniformly scaled resource-pack variants. */
    @ParameterizedTest
    @CsvSource({"128, 536, 67", "144, 900, 75", "128, 128, 16", "256, 1072, 67", "288, 1800, 75"})
    void acceptsCompleteModernGrids(int width, int height, int rows) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        assertDoesNotThrow(() -> FontProviderBitmap.validateSheetGrid(image, 16, rows));
    }

    /** Reproduces RC-2's older Zedtech sheets, which otherwise map glyphs onto partial rows. */
    @ParameterizedTest
    @CsvSource({"128, 304, 67", "144, 312, 75", "129, 536, 67", "128, 1, 67"})
    void rejectsLegacyAndPartialGrids(int width, int height, int rows) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        assertThrows(IOException.class, () -> FontProviderBitmap.validateSheetGrid(image, 16, rows));
    }

    /** Rejects unreadable images and empty definitions before any bitmap sampling occurs. */
    @Test
    void rejectsUnreadableAndEmptyDefinitions() {
        assertThrows(IOException.class, () -> FontProviderBitmap.validateSheetGrid(null, 16, 67));
        final BufferedImage image = new BufferedImage(128, 536, BufferedImage.TYPE_INT_ARGB);
        assertThrows(IOException.class, () -> FontProviderBitmap.validateSheetGrid(image, 0, 67));
        assertThrows(IOException.class, () -> FontProviderBitmap.validateSheetGrid(image, 16, 0));
    }
}
