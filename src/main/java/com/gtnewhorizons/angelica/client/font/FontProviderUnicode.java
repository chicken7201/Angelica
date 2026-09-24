package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import jss.util.RandomXoshiro256StarStar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class FontProviderUnicode implements FontProvider, IResourceManagerReloadListener {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/UnicodeFont");
    private static final int PAGE_COUNT = 256;
    private static final ResourceLocation[] unicodePageLocations = new ResourceLocation[PAGE_COUNT];

    private final Object pageStateLock = new Object();
    private final Object[] pageCreationLocks = new Object[PAGE_COUNT];
    private final LoadedPage[] unicodePages = new LoadedPage[PAGE_COUNT];
    private final List<LoadedPage> retiredPages = new ArrayList<>();
    private final int[] pageCreationCounts = new int[PAGE_COUNT];
    private final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();
    private volatile byte[] glyphWidth = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
    private volatile IResourceManager resourceManager;
    private volatile int resourceGeneration;

    /** Registers this provider; the reloadable manager immediately supplies the initial resources. */
    private FontProviderUnicode() {
        initializePageCreationLocks();
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
    }

    /** Creates an isolated provider for tests without accessing the Minecraft singleton. */
    FontProviderUnicode(IResourceManager resourceManager, byte[] glyphWidth) {
        initializePageCreationLocks();
        this.resourceManager = resourceManager;
        this.glyphWidth = glyphWidth;
    }

    /** Initializes the per-page locks used to serialize lazy composition. */
    private void initializePageCreationLocks() {
        for (int i = 0; i < this.pageCreationLocks.length; i++) {
            this.pageCreationLocks[i] = new Object();
        }
    }

    /** Atomically installs a new glyph table and retires every page from the previous resource generation. */
    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        final byte[] loadedGlyphWidth;
        try (InputStream inputstream = resourceManager.getResource(new ResourceLocation("font/glyph_sizes.bin")).getInputStream()) {
            loadedGlyphWidth = readGlyphWidths(inputstream);
        } catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        }

        final boolean locked = GLStateManager.acquireDrawLock();
        final int previousGeneration;
        final int retiredCount;
        try {
            synchronized (this.pageStateLock) {
                previousGeneration = this.resourceGeneration;
                this.resourceGeneration++;
                this.resourceManager = resourceManager;
                this.glyphWidth = loadedGlyphWidth;

                int count = 0;
                for (int i = 0; i < this.unicodePages.length; i++) {
                    final LoadedPage page = this.unicodePages[i];
                    if (page != null) {
                        this.retiredPages.add(page);
                        this.unicodePages[i] = null;
                        count++;
                    }
                }
                retiredCount = count;
            }
        } finally {
            if (locked) GLStateManager.releaseDrawLock();
        }

        LOGGER.debug(
            "Resource reload generation changed: old={}, new={}, thread={}",
            previousGeneration,
            this.resourceGeneration,
            Thread.currentThread().getName());
        if (retiredCount > 0) {
            LOGGER.debug(
                "Unicode page cache cleared: retiredPages={}, generation={}, thread={}",
                retiredCount,
                this.resourceGeneration,
                Thread.currentThread().getName());
        }
    }

    /** Reads the complete glyph_sizes.bin table even when the stream returns short chunks. */
    static byte[] readGlyphWidths(InputStream inputstream) throws IOException {
        final byte[] loadedGlyphWidth = new byte[UnicodeGlyphMetrics.GLYPH_COUNT];
        new DataInputStream(inputstream).readFully(loadedGlyphWidth);
        return loadedGlyphWidth;
    }

    /** Returns the lazily initialized singleton Unicode font provider. */
    public static FontProviderUnicode get() {
        return InstLoader.instance;
    }

    /** Returns and caches the vanilla resource location for one Unicode texture page. */
    private ResourceLocation getUnicodePageLocation(int page) {
        final ResourceLocation lookup = unicodePageLocations[page];
        if (lookup != null) {
            return lookup;
        }

        final ResourceLocation location = new ResourceLocation(String.format(
            "textures/font/unicode_page_%02x.png",
            page));
        unicodePageLocations[page] = location;
        return location;
    }

    /** Returns a current-generation page, composing it once on the CPU when absent. */
    private LoadedPage getPage(char chr) {
        if (Character.isSurrogate(chr)) {
            return null;
        }

        final int pageIndex = chr >>> 8;
        LoadedPage page = getCachedPage(pageIndex, true);
        if (page != null) {
            return page;
        }

        synchronized (this.pageCreationLocks[pageIndex]) {
            page = getCachedPage(pageIndex, false);
            if (page != null) {
                LOGGER.debug(
                    "Duplicate Unicode page creation prevented: page={}, generation={}, thread={}",
                    pageLabel(pageIndex),
                    page.generation,
                    Thread.currentThread().getName());
                return page;
            }

            while (true) {
                final int generation;
                final IResourceManager manager;
                synchronized (this.pageStateLock) {
                    generation = this.resourceGeneration;
                    manager = this.resourceManager;
                }
                if (manager == null) {
                    throw new IllegalStateException("Unicode font resources have not been initialized");
                }

                LOGGER.debug(
                    "Unicode page requested: page={}, generation={}, thread={}, cached=false",
                    pageLabel(pageIndex),
                    generation,
                    Thread.currentThread().getName());
                final LoadedPage candidate = loadPage(pageIndex, generation, manager);

                synchronized (this.pageStateLock) {
                    if (generation != this.resourceGeneration || manager != this.resourceManager) {
                        LOGGER.debug(
                            "Unicode page cache stale: page={}, pageGeneration={}, currentGeneration={}, thread={}",
                            pageLabel(pageIndex),
                            generation,
                            this.resourceGeneration,
                            Thread.currentThread().getName());
                        continue;
                    }

                    final LoadedPage existing = this.unicodePages[pageIndex];
                    if (existing != null && existing.generation == generation) {
                        LOGGER.debug(
                            "Duplicate Unicode page creation prevented: page={}, generation={}, thread={}",
                            pageLabel(pageIndex),
                            generation,
                            Thread.currentThread().getName());
                        return existing;
                    }

                    final boolean recreated = this.pageCreationCounts[pageIndex]++ > 0;
                    this.unicodePages[pageIndex] = candidate;
                    LOGGER.debug(
                        "Unicode page {}: page={}, generation={}, thread={}",
                        recreated ? "recreated" : "created",
                        pageLabel(pageIndex),
                        generation,
                        Thread.currentThread().getName());
                    return candidate;
                }
            }
        }
    }

    /** Returns a cached page only when it belongs to the currently installed resource generation. */
    private LoadedPage getCachedPage(int pageIndex, boolean logHit) {
        synchronized (this.pageStateLock) {
            final LoadedPage page = this.unicodePages[pageIndex];
            if (page == null || page.generation != this.resourceGeneration) {
                return null;
            }
            if (logHit && !page.cacheHitLogged) {
                page.cacheHitLogged = true;
                LOGGER.debug(
                    "Unicode page cache hit: page={}, generation={}, thread={}",
                    pageLabel(pageIndex),
                    page.generation,
                    Thread.currentThread().getName());
            }
            return page;
        }
    }

    /** Composes all resource-pack layers for one page without touching TextureManager or OpenGL. */
    private LoadedPage loadPage(int pageIndex, int generation, IResourceManager manager) {
        final ResourceLocation sourceLocation = getUnicodePageLocation(pageIndex);
        try {
            final List<BufferedImage> layers = loadPageLayers(pageIndex, manager);
            if (layers.isEmpty()) {
                LOGGER.debug(
                    "Unicode font page unavailable: page={}, generation={}, location={}",
                    pageLabel(pageIndex),
                    generation,
                    sourceLocation);
                return new LoadedPage(pageIndex, generation, null, null);
            }
            final UnicodeGlyphPage glyphPage = UnicodeGlyphPage.compose(layers);
            if (pageIndex == FontGlyphRanges.UNICODE_SUBSCRIPT_DIGIT_START >>> 8) {
                try {
                    final UnicodeGlyphPage reference = UnicodeGlyphPage.compose(
                        loadPageLayers(FontGlyphRanges.GTNH_SUBSCRIPT_ZERO >>> 8, manager));
                    final int aligned = glyphPage.alignSubscriptDigitsToReference(layers, reference);
                    if (aligned > 0) {
                        LOGGER.debug(
                            "Unicode subscript glyph family aligned: count={}, reference=U+{}, generation={}, thread={}",
                            aligned,
                            Integer.toHexString(FontGlyphRanges.GTNH_SUBSCRIPT_ZERO).toUpperCase(),
                            generation,
                            Thread.currentThread().getName());
                    }
                } catch (IOException | IllegalArgumentException exception) {
                    LOGGER.debug(
                        "GTNH subscript-zero reference unavailable; preserving composed Unicode page {}",
                        sourceLocation,
                        exception);
                }
            }
            return new LoadedPage(pageIndex, generation, glyphPage, glyphPage.takeImage());
        } catch (IOException | IllegalArgumentException exception) {
            throw new RuntimeException("Failed to compose Unicode font page " + sourceLocation, exception);
        }
    }

    /** Loads every resource-pack image layer for one Unicode page in manager priority order. */
    private List<BufferedImage> loadPageLayers(int pageIndex, IResourceManager manager) throws IOException {
        final ResourceLocation sourceLocation = getUnicodePageLocation(pageIndex);
        final List<BufferedImage> layers = new ArrayList<>();
        final List<IResource> resources;
        try {
            resources = manager.getAllResources(sourceLocation);
        } catch (FileNotFoundException ignored) {
            return layers;
        }
        for (IResource resource : resources) {
            try (InputStream inputstream = resource.getInputStream()) {
                final BufferedImage image = ImageIO.read(inputstream);
                if (image == null) {
                    throw new IOException("Unsupported Unicode font page image: " + sourceLocation);
                }
                layers.add(image);
            }
        }
        return layers;
    }

    /** Uploads a current CPU page from a valid render context and returns its registered dynamic location. */
    private ResourceLocation ensurePageTexture(LoadedPage page) {
        if (page == null || page.metrics == null) {
            return null;
        }
        if (!hasCurrentGlContext()) {
            synchronized (this.pageStateLock) {
                if (!page.wrongThreadLogged) {
                    page.wrongThreadLogged = true;
                    LOGGER.debug(
                        "Wrong-thread texture upload prevented: page={}, generation={}, thread={}",
                        pageLabel(page.pageIndex),
                        page.generation,
                        Thread.currentThread().getName());
                }
            }
            return null;
        }
        if (!UnicodeTextureLifecycle.tryBeginTextureUse()) {
            synchronized (this.pageStateLock) {
                if (!page.reloadDeferredLogged) {
                    page.reloadDeferredLogged = true;
                    LOGGER.debug(
                        "Dynamic texture registration deferred during reload: page={}, generation={}, thread={}",
                        pageLabel(page.pageIndex),
                        page.generation,
                        Thread.currentThread().getName());
                }
            }
            return null;
        }

        final boolean locked = GLStateManager.acquireDrawLock();
        try {
            synchronized (this.pageStateLock) {
                final TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
                clearRetiredPages(textureManager);
                return ensurePageTextureLocked(page, textureManager);
            }
        } finally {
            if (locked) GLStateManager.releaseDrawLock();
            UnicodeTextureLifecycle.endTextureUse();
        }
    }

    /** Registers or repairs one page while the lifecycle, draw, and page-state locks are held. */
    private ResourceLocation ensurePageTextureLocked(LoadedPage page, TextureManager textureManager) {
        if (!isCurrentPageLocked(page)) {
            return null;
        }

        if (page.texture == null) {
            LOGGER.debug(
                "Dynamic texture registration started: page={}, generation={}, thread={}, state=CPU_READY",
                pageLabel(page.pageIndex),
                page.generation,
                Thread.currentThread().getName());
            page.dynamicTexture = withPageTextureBindingPreserved(() -> new DynamicTexture(page.image));
            page.texture = textureManager.getDynamicTextureLocation(
                String.format("angelica_unicode_page_%02x", page.pageIndex),
                page.dynamicTexture);
            page.image = null;
            LOGGER.debug(
                "Dynamic texture registration completed: page={}, location={}, generation={}, thread={}, registered={}",
                pageLabel(page.pageIndex),
                page.texture,
                page.generation,
                Thread.currentThread().getName(),
                textureManager.getTexture(page.texture) == page.dynamicTexture);
        } else if (textureManager.getTexture(page.texture) != page.dynamicTexture
            || page.dynamicTexture.glTextureId == -1) {
            final boolean registrationMissing = textureManager.getTexture(page.texture) != page.dynamicTexture;
            final boolean uploadMissing = page.dynamicTexture.glTextureId == -1;
            if (!page.missingRegistrationLogged) {
                page.missingRegistrationLogged = true;
                LOGGER.debug(
                    "Dynamic texture missing before bind: page={}, location={}, generation={}, thread={}, registered={}, uploaded={}",
                    pageLabel(page.pageIndex),
                    page.texture,
                    page.generation,
                    Thread.currentThread().getName(),
                    !registrationMissing,
                    !uploadMissing);
            }
            if (uploadMissing) {
                withPageTextureBindingPreserved(() -> {
                    page.dynamicTexture.updateDynamicTexture();
                    return null;
                });
            }
            if (registrationMissing) {
                textureManager.loadTexture(page.texture, page.dynamicTexture);
            }
            LOGGER.debug(
                "Dynamic texture registration completed: page={}, location={}, generation={}, thread={}, recreated=true, reuploaded={}",
                pageLabel(page.pageIndex),
                page.texture,
                page.generation,
                Thread.currentThread().getName(),
                uploadMissing);
        }

        return textureManager.getTexture(page.texture) == page.dynamicTexture ? page.texture : null;
    }

    /** Keeps a lazy Unicode page upload from replacing the texture bound by the caller before font state is saved. */
    static <T> T withPageTextureBindingPreserved(Supplier<T> upload) {
        final int activeUnit = GLStateManager.getActiveTextureUnitForServerState();
        final int previousTexture = GLStateManager.getBoundTextureForServerState();
        try {
            return upload.get();
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + activeUnit);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    /** Removes retired dynamic pages only while no font draw can use their old generation. */
    private void clearRetiredPages(TextureManager textureManager) {
        if (this.retiredPages.isEmpty()) {
            return;
        }

        int cleared = 0;
        for (LoadedPage page : this.retiredPages) {
            if (page.texture != null && page.dynamicTexture != null) {
                if (textureManager.getTexture(page.texture) == page.dynamicTexture) {
                    textureManager.mapTextureObjects.remove(page.texture);
                }
                page.dynamicTexture.deleteGlTexture();
            }
            cleared++;
        }
        this.retiredPages.clear();
        LOGGER.debug(
            "Unicode page cache cleared: releasedPages={}, generation={}, thread={}",
            cleared,
            this.resourceGeneration,
            Thread.currentThread().getName());
    }

    /** Reports whether the calling thread owns a usable OpenGL context. */
    private static boolean hasCurrentGlContext() {
        return BackendManager.RENDER_BACKEND.hasContext();
    }

    /** Reports whether a page is still installed in the active resource generation. */
    private boolean isCurrentPage(LoadedPage page) {
        synchronized (this.pageStateLock) {
            return isCurrentPageLocked(page);
        }
    }

    /** Reports whether a page is current while the page-state lock is already held. */
    private boolean isCurrentPageLocked(LoadedPage page) {
        return page != null && page.generation == this.resourceGeneration && this.unicodePages[page.pageIndex] == page;
    }

    /** Formats a Unicode page number for rate-limited diagnostics. */
    private static String pageLabel(int pageIndex) {
        return String.format("%02x", pageIndex);
    }

    /** Returns an immutable rendering snapshot so reload cannot mix metrics from different page generations. */
    GlyphRenderInfo getRenderInfo(char chr) {
        if (Character.isSurrogate(chr)) {
            return null;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            final LoadedPage page = getPage(chr);
            final byte packedBounds;
            synchronized (this.pageStateLock) {
                if (!isCurrentPageLocked(page)) {
                    continue;
                }
                packedBounds = this.glyphWidth[chr];
            }
            if (page.metrics == null || !UnicodeGlyphMetrics.isAvailable(packedBounds)
                || !page.metrics.isGlyphAvailable(chr & 255)) {
                return null;
            }

            final boolean customGlyph = FontGlyphRanges.isGtnhPrivateUseGlyph(chr);
            final ResourceLocation texture = ensurePageTexture(page);
            if (texture == null && !isCurrentPage(page)) {
                continue;
            }
            return new GlyphRenderInfo(
                customGlyph ? page.metrics.getDeclaredUStart(chr & 255, packedBounds) : page.metrics.getUStart(chr & 255),
                page.metrics.getVStart(chr & 255),
                customGlyph ? page.metrics.getDeclaredXAdvance(packedBounds)
                    : page.metrics.getXAdvance(chr & 255, packedBounds),
                customGlyph ? page.metrics.getDeclaredGlyphWidth(packedBounds) : page.metrics.getGlyphWidth(chr & 255),
                customGlyph ? page.metrics.getDeclaredUSize(packedBounds) : page.metrics.getUSize(chr & 255),
                page.metrics.getVSize(),
                page.metrics.getSampleUStart(chr & 255),
                page.metrics.getSampleUEnd(chr & 255),
                page.metrics.getSampleVStart(chr & 255),
                page.metrics.getSampleVEnd(chr & 255),
                texture);
        }
        return null;
    }

    /** Validates or repairs a current Unicode texture and returns its directly bindable OpenGL ID. */
    int prepareTextureForBind(ResourceLocation location) {
        if (location == null || !hasCurrentGlContext() || !UnicodeTextureLifecycle.tryBeginTextureUse()) {
            return -1;
        }

        final boolean locked = GLStateManager.acquireDrawLock();
        try {
            synchronized (this.pageStateLock) {
                final TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
                clearRetiredPages(textureManager);
                for (LoadedPage page : this.unicodePages) {
                    if (page != null && page.generation == this.resourceGeneration && location.equals(page.texture)) {
                        if (ensurePageTextureLocked(page, textureManager) == null || page.dynamicTexture == null) {
                            return -1;
                        }
                        return page.dynamicTexture.glTextureId;
                    }
                }
                return -1;
            }
        } finally {
            if (locked) GLStateManager.releaseDrawLock();
            UnicodeTextureLifecycle.endTextureUse();
        }
    }

    /** Logs one summary when reload invalidates Unicode commands in an already built font batch. */
    void logDiscardedBatch(int discardedCommands) {
        LOGGER.debug(
            "Font batch discarded after reload: unicodeCommands={}, generation={}, thread={}, reloading={}",
            discardedCommands,
            this.resourceGeneration,
            Thread.currentThread().getName(),
            UnicodeTextureLifecycle.isTextureManagerReloading());
    }

    /** Returns whether both glyph_sizes.bin and the composed texture contain this glyph. */
    @Override
    public boolean isGlyphAvailable(char chr) {
        if (Character.isSurrogate(chr)) {
            return false;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            final LoadedPage page = getPage(chr);
            final byte packedBounds;
            synchronized (this.pageStateLock) {
                if (!isCurrentPageLocked(page)) {
                    continue;
                }
                packedBounds = this.glyphWidth[chr];
            }
            return page.metrics != null && UnicodeGlyphMetrics.isAvailable(packedBounds)
                && page.metrics.isGlyphAvailable(chr & 255);
        }
        return false;
    }

    /**
     * A random glyph of the same width, for {@code §k}. Kept inside the character's own
     * 256-glyph page so the swap neither pulls in another page's texture nor splits the
     * batch. Empty entries are left alone, and never swapped in for a glyph that draws.
     */
    @Override
    public char getRandomReplacement(char chr) {
        if (this.glyphWidth[chr] == 0) {
            return chr;
        }
        final int pageStart = (chr / 256) * 256;
        final float targetWidth = getXAdvance(chr);
        for (int attempt = 0; attempt < RANDOM_GLYPH_TRIES; attempt++) {
            final char candidate = (char) (pageStart + fontRandom.nextInt(256));
            if (this.glyphWidth[candidate] != 0 && getXAdvance(candidate) == targetWidth) {
                return candidate;
            }
        }
        return chr;
    }

    /** Returns the composed bitmap UV, preserving declared GTNH custom-glyph bearings. */
    @Override
    public float getUStart(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        if (metrics == null) {
            return 0.0F;
        }
        final byte packedBounds = this.glyphWidth[chr];
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredUStart(chr & 255, packedBounds)
            : metrics.getUStart(chr & 255);
    }

    /** Returns the composed glyph cell's top UV. */
    @Override
    public float getVStart(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        return metrics == null ? 0.0F : metrics.getVStart(chr & 255);
    }

    /** Returns cursor advance separately from atlas bounds and padding. */
    @Override
    public float getXAdvance(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        if (metrics == null) {
            return 0.0F;
        }
        final byte packedBounds = this.glyphWidth[chr];
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredXAdvance(packedBounds)
            : metrics.getXAdvance(chr & 255, packedBounds);
    }

    /** Returns the screen quad width from the selected bitmap bounds. */
    @Override
    public float getGlyphW(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        if (metrics == null) {
            return 0.0F;
        }
        final byte packedBounds = this.glyphWidth[chr];
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredGlyphWidth(packedBounds)
            : metrics.getGlyphWidth(chr & 255);
    }

    /** Returns the composed bitmap UV span, preserving GTNH custom-glyph bounds. */
    @Override
    public float getUSize(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        if (metrics == null) {
            return 0.0F;
        }
        final byte packedBounds = this.glyphWidth[chr];
        return FontGlyphRanges.isGtnhPrivateUseGlyph(chr)
            ? metrics.getDeclaredUSize(packedBounds)
            : metrics.getUSize(chr & 255);
    }

    /** Returns one composed Unicode cell's vertical UV span. */
    @Override
    public float getVSize(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        return metrics == null ? 0.0F : metrics.getVSize();
    }

    /** Keeps anti-aliasing samples inside the glyph's atlas cell. */
    @Override
    public float getSampleUStart(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        return metrics == null ? 0.0F : metrics.getSampleUStart(chr & 255);
    }

    /** Keeps anti-aliasing samples away from the next horizontal atlas cell. */
    @Override
    public float getSampleUEnd(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        return metrics == null ? 0.0F : metrics.getSampleUEnd(chr & 255);
    }

    /** Keeps anti-aliasing samples inside the glyph's atlas cell. */
    @Override
    public float getSampleVStart(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        return metrics == null ? 0.0F : metrics.getSampleVStart(chr & 255);
    }

    /** Keeps anti-aliasing samples away from the next vertical atlas cell. */
    @Override
    public float getSampleVEnd(char chr) {
        final UnicodeGlyphPage metrics = getPageMetrics(chr);
        return metrics == null ? 0.0F : metrics.getSampleVEnd(chr & 255);
    }

    /** Returns page metrics only for renderable BMP code units backed by an existing page. */
    private UnicodeGlyphPage getPageMetrics(char chr) {
        final LoadedPage page = getPage(chr);
        return page == null ? null : page.metrics;
    }

    /** Returns the configured shadow offset for Unicode glyphs. */
    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset * FontConfig.fontShadowOffsetUC;
    }

    /** Returns the registered texture for a current page, or null while upload must be deferred. */
    @Override
    public ResourceLocation getTexture(char chr) {
        if (Character.isSurrogate(chr)) {
            return null;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            final LoadedPage page = getPage(chr);
            if (page.metrics == null) {
                if (isCurrentPage(page)) {
                    return null;
                }
                continue;
            }
            final ResourceLocation texture = ensurePageTexture(page);
            if (texture != null || isCurrentPage(page)) {
                return texture;
            }
        }
        return null;
    }

    /** Keeps Unicode glyphs at the renderer's standard vertical scale. */
    @Override
    public float getYScaleMultiplier() {
        return 1;
    }

    static final class GlyphRenderInfo {

        final float uStart;
        final float vStart;
        final float xAdvance;
        final float glyphWidth;
        final float uSize;
        final float vSize;
        final float sampleUStart;
        final float sampleUEnd;
        final float sampleVStart;
        final float sampleVEnd;
        final ResourceLocation texture;

        /** Captures all bounds, UVs, advance, and texture state used to build one glyph quad. */
        private GlyphRenderInfo(float uStart, float vStart, float xAdvance, float glyphWidth, float uSize,
            float vSize, float sampleUStart, float sampleUEnd, float sampleVStart, float sampleVEnd,
            ResourceLocation texture) {
            this.uStart = uStart;
            this.vStart = vStart;
            this.xAdvance = xAdvance;
            this.glyphWidth = glyphWidth;
            this.uSize = uSize;
            this.vSize = vSize;
            this.sampleUStart = sampleUStart;
            this.sampleUEnd = sampleUEnd;
            this.sampleVStart = sampleVStart;
            this.sampleVEnd = sampleVEnd;
            this.texture = texture;
        }
    }

    private static final class InstLoader {

        private static final FontProviderUnicode instance = new FontProviderUnicode();

        /** Prevents construction of the singleton holder. */
        private InstLoader() {}
    }

    private static final class LoadedPage {

        private final int pageIndex;
        private final int generation;
        private final UnicodeGlyphPage metrics;
        private BufferedImage image;
        private DynamicTexture dynamicTexture;
        private ResourceLocation texture;
        private boolean cacheHitLogged;
        private boolean wrongThreadLogged;
        private boolean reloadDeferredLogged;
        private boolean missingRegistrationLogged;

        /** Keeps one generation's CPU metrics, image, and eventual dynamic texture registration together. */
        private LoadedPage(int pageIndex, int generation, UnicodeGlyphPage metrics, BufferedImage image) {
            this.pageIndex = pageIndex;
            this.generation = generation;
            this.metrics = metrics;
            this.image = image;
        }
    }
}
