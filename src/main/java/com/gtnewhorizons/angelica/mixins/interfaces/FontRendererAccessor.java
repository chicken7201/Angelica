package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

public interface FontRendererAccessor {

    int angelica$drawStringBatched(String text, int x, int y, int argb, boolean dropShadow);

    BatchingFontRenderer angelica$getBatcher();

    void angelica$bindTexture(ResourceLocation location);

    /** Returns the renderer's manager, including a private loading-screen manager during early startup. */
    TextureManager angelica$getTextureManager();
}
