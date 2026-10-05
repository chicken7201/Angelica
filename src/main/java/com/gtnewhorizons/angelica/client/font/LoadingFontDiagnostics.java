package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in, bounded diagnostics for text disappearing during BetterLoadingScreen startup. */
public final class LoadingFontDiagnostics {
    public static final boolean ENABLED = Boolean.getBoolean("angelica.debug.loadingFonts");
    private static final Logger LOGGER = LogManager.getLogger("Angelica/LoadingFont");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final AtomicInteger RELOAD = new AtomicInteger();
    private static final long START = System.nanoTime();

    /** Prevents construction of the diagnostic utility. */
    private LoadingFontDiagnostics() {}

    /** Formats an identity without invoking a renderer or resource object's toString method. */
    public static String identity(Object object) {
        return object == null ? "null" : object.getClass().getName() + '@'
            + Integer.toHexString(System.identityHashCode(object));
    }

    /** Records lifecycle events in sequence order without initializing any font provider. */
    public static void event(String event, String details) {
        if (!ENABLED) return;
        LOGGER.info("[LoadingFont] seq={} ms={} reload={} thread={} event={} {}",
            SEQUENCE.incrementAndGet(), (System.nanoTime() - START) / 1_000_000,
            RELOAD.get(), Thread.currentThread().getName(), event, details);
    }

    /** Records the original caught client exception before a secondary crash-report failure can hide it. */
    public static void startupFailure(String description, Throwable cause) {
        if (!ENABLED) return;
        event("STARTUP_FAILURE", "description=" + description);
        LOGGER.error("[LoadingFont] Original client failure: " + description, cause);
    }

    /** Records a resource reload boundary and its short calling path. */
    public static void resourceReload(Object manager, boolean beginning) {
        if (!ENABLED) return;
        if (beginning) RELOAD.incrementAndGet();
        final Minecraft mc = Minecraft.getMinecraft();
        event(beginning ? "RESOURCE_RELOAD_BEGIN" : "RESOURCE_RELOAD_END",
            "manager=" + identity(manager) + " mcFont=" + identity(mc.fontRenderer)
                + " mcTextures=" + identity(mc.getTextureManager()) + " caller=" + caller());
    }

    /** Keeps only the relevant startup call chain instead of logging a full stack per event. */
    public static String caller() {
        final StringBuilder result = new StringBuilder();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            final String name = frame.getClassName();
            if (name.startsWith("alexiil.mods.load.") || name.equals("net.minecraft.client.Minecraft")
                || name.equals("cpw.mods.fml.client.FMLClientHandler")) {
                if (result.length() > 0) result.append(" <- ");
                result.append(name).append('.').append(frame.getMethodName());
                if (result.length() > 400) break;
            }
        }
        return result.toString();
    }

    /** Reads GL state without changing bindings or consuming GL errors. */
    private static String glState() {
        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        if (backend == null || !backend.hasContext()) return "gl=no_context";
        return "context=" + identity(GLStateManager.ctx())
            + " backend=" + backend.getClass().getSimpleName()
            + " splashComplete=" + GLStateManager.isSplashComplete()
            + " caching=" + GLStateManager.isCachingEnabled()
            + " activeActual=" + (backend.getInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0)
            + " activeCached=" + GLStateManager.getActiveTextureUnit()
            + " boundActual=" + backend.getInteger(GL11.GL_TEXTURE_BINDING_2D)
            + " boundCached=" + GLStateManager.getBoundTextureForServerState()
            + " program=" + backend.getInteger(GL20.GL_CURRENT_PROGRAM)
            + " vao=" + backend.getInteger(GL30.GL_VERTEX_ARRAY_BINDING)
            + " fontVao=" + GLStateManager.ctx().fontVao;
    }

    /** Tracks one BLS renderer with event budgets and one-second GL polling. */
    static final class RenderTrace {
        private static final int EVENT_LIMIT = 32;
        private final Map<FontProvider, Boolean> providers = new IdentityHashMap<>();
        private final Map<Integer, String> textures = new HashMap<>();
        private final ColorMask colorMask = new ColorMask();
        private boolean loadingRenderer;
        private FontRenderer renderer;
        private String lastState;
        private String lastOutcome;
        private Boolean lastEmpty;
        private String lastFailure;
        private long nextSample;
        private boolean sample;
        private int stateEvents;
        private int textureEvents;
        private int outcomeEvents;
        private int geometryEvents;
        private int failureEvents;
        private int attempted;
        private int generated;
        private int expectedShader;

        /** Enables detailed traces only for draw calls on the BLS startup thread. */
        boolean begin(BatchingFontRenderer owner, boolean unicode, int shader) {
            loadingRenderer = Thread.currentThread().getName().equals("BLS Splash renderer");
            if (!loadingRenderer) return false;
            renderer = owner.underlying;
            expectedShader = shader;
            attempted = generated = 0;
            final long now = System.nanoTime();
            sample = now >= nextSample;
            if (sample) {
                nextSample = now + 1_000_000_000L;
                final Minecraft mc = Minecraft.getMinecraft();
                GLStateManager.getEffectiveColorMask(colorMask);
                final String state = "renderer=" + identity(renderer) + " batcher=" + identity(owner)
                    + " mcFont=" + identity(mc.fontRenderer) + " mcTextures=" + identity(mc.getTextureManager())
                    + " unicode=" + unicode + " custom=" + FontConfig.enableCustomFont
                    + " isSplash=" + owner.isSplash + " aa=" + FontConfig.fontAAMode
                    + " brightness=" + FontConfig.fontBrightness + " shader=" + shader
                    + " reload=" + RELOAD.get() + ' ' + glState()
                    + " depth=" + GLStateManager.getDepthTest().isEffectivelyEnabled()
                    + " cull=" + GLStateManager.getCullState().isEffectivelyEnabled()
                    + " alphaRef=" + GLStateManager.getAlphaState().getReference()
                    + " colorMask=" + colorMask.red + ',' + colorMask.green + ',' + colorMask.blue + ',' + colorMask.alpha;
                if (!state.equals(lastState)) {
                    lastState = state;
                    if (stateEvents++ < EVENT_LIMIT) event("BLS_DRAW_STATE", state);
                    else if (stateEvents == EVENT_LIMIT + 1) event("BLS_STATE_LIMIT", identity(renderer));
                }
            }
            return true;
        }

        /** Records each provider identity once and counts glyph attempts without per-glyph output. */
        void provider(FontProvider provider) {
            attempted++;
            if (providers.put(provider, Boolean.TRUE) == null) {
                event("BLS_PROVIDER_FIRST_USE", "renderer=" + identity(renderer) + " provider=" + identity(provider));
            }
        }

        /** Counts glyphs that produced textured geometry without emitting a log entry. */
        void generatedGlyph() { generated++; }

        /** Records empty geometry and recovery transitions, ignoring whitespace-only strings. */
        void finish() {
            if (attempted == 0) return;
            final boolean empty = generated == 0;
            if (lastEmpty != null && lastEmpty == empty) return;
            lastEmpty = empty;
            if (geometryEvents++ < EVENT_LIMIT) event(empty ? "BLS_NO_GLYPH_GEOMETRY" : "BLS_GLYPH_GEOMETRY_READY",
                "renderer=" + identity(renderer) + " attempted=" + attempted + " generated=" + generated);
            else if (geometryEvents == EVENT_LIMIT + 1) event("BLS_GEOMETRY_LIMIT", identity(renderer));
        }

        /** Records flush failures and recovery once per status transition. */
        void outcome(String status, String details) {
            if (!loadingRenderer || status.equals(lastOutcome)) return;
            lastOutcome = status;
            if (outcomeEvents++ < EVENT_LIMIT) event("BLS_" + status, "renderer=" + identity(renderer) + ' ' + details);
            else if (outcomeEvents == EVENT_LIMIT + 1) event("BLS_OUTCOME_LIMIT", identity(renderer));
        }

        /** Records persistent exceptions once because BLS catches and retries them every frame. */
        void failure(RuntimeException failure) {
            if (!loadingRenderer) return;
            final String description = failure.getClass().getName() + ": " + failure.getMessage();
            if (description.equals(lastFailure)) return;
            lastFailure = description;
            if (failureEvents++ < EVENT_LIMIT) event("BLS_FONT_EXCEPTION",
                "renderer=" + identity(renderer) + ' ' + description);
            else if (failureEvents == EVENT_LIMIT + 1) event("BLS_EXCEPTION_LIMIT", identity(renderer));
        }

        /** Samples texture validity and the actual binding only on changed states, with a fixed output budget. */
        void texture(int handle, int texture, boolean unicode) {
            if (!loadingRenderer || !sample) return;
            final RenderBackend backend = BackendManager.RENDER_BACKEND;
            if (backend == null || !backend.hasContext()) return;
            final String state = "reload=" + RELOAD.get() + " valid=" + backend.isTexture(texture)
                + " storedValid=" + (handle > 0 && backend.isTexture(handle))
                + " unicode=" + unicode + " shaderExpected=" + expectedShader + ' ' + glState();
            if (state.equals(textures.put(texture, state))) return;
            if (textureEvents++ < EVENT_LIMIT) event("BLS_TEXTURE_BIND",
                "renderer=" + identity(renderer) + " handle=" + handle + " texture=" + texture + ' ' + state);
            else if (textureEvents == EVENT_LIMIT + 1) event("BLS_TEXTURE_LIMIT", identity(renderer));
        }
    }
}
