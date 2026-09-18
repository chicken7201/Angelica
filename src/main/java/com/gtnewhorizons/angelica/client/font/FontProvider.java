package com.gtnewhorizons.angelica.client.font;

import net.minecraft.util.ResourceLocation;

public interface FontProvider {

    /** Tries before {@link #getRandomReplacement} gives up and leaves the character alone. */
    int RANDOM_GLYPH_TRIES = 64;

    /**
     * For use with §k. Should fetch a character of the same width as provided, and one this
     * provider can draw, since the provider is picked for the original character.
     */
    char getRandomReplacement(char chr);
    boolean isGlyphAvailable(char chr);
    float getUStart(char chr);
    float getVStart(char chr);
    float getXAdvance(char chr);
    float getGlyphW(char chr);
    float getUSize(char chr);
    float getVSize(char chr);

    /** Returns the minimum U coordinate that the font shader may sample. */
    default float getSampleUStart(char chr) { return getUStart(chr); }

    /** Returns the maximum U coordinate that the font shader may sample. */
    default float getSampleUEnd(char chr) { return getUStart(chr) + getUSize(chr); }

    /** Returns the minimum V coordinate that the font shader may sample. */
    default float getSampleVStart(char chr) { return getVStart(chr); }

    /** Returns the maximum V coordinate that the font shader may sample. */
    default float getSampleVEnd(char chr) { return getVStart(chr) + getVSize(chr); }

    float getShadowOffset();
    ResourceLocation getTexture(char chr);
    float getYScaleMultiplier();
}
