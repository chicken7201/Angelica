package com.gtnewhorizons.angelica.client.font;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;

public class DarkModeUtils {

    private static final Logger LOGGER = LogManager.getLogger("DarkModeUtils");

    private static final String ROOT_KEY = "dark_mode_utils";
    private static final int SUPPORTED_SCHEMA = 1;
    private static final String DEBUG_PULSE = "debug_pulse";
    private static final double DEBUG_PULSE_TIME_SCALE = 5e-9;

    private static final String[] INPUT_STREAM_METHOD_NAMES = { "getInputStreamByName", "func_110591_a" };
    private static final Map<Class<?>, Method> INPUT_STREAM_METHOD_CACHE = new HashMap<>();

    private static FontRecolorRule guiFontRule = null;
    private static ButtonFontRules buttonFontRules = null;
    private static boolean shadowsGlobal = false;

    static {
        TexturePackChangeHandler.register(new TexturePackChangeHandler("Angelica Dark Mode Utils", 1) {

            @Override
            /** Keeps current rules available until the new pack stack is loaded. */
            public void beforeChange() {}

            @Override
            /** Refreshes recolor rules after resource packs change. */
            public void afterChange() {
                reload();
            }
        });
    }

    /** Exposes pack recoloring through static helpers only. */
    private DarkModeUtils() {}

    /** Triggers registration of the resource-pack change handler. */
    public static void init() { /* see static block */ }

    /** Resolves the highest-priority configured rules from active packs. */
    private static void reload() {
        FontRecolorRule guiFont = null;
        ButtonFontRules buttonFont = null;
        boolean shadowsGlobalValue = false;
        ResourcePackRepository repository = Minecraft.getMinecraft().getResourcePackRepository();
        for (ResourcePackRepository.Entry entry : repository.getRepositoryEntries()) {
            IResourcePack pack = entry.getResourcePack();
            PackDarkModeRules rules = readPackRules(pack);
            if (rules == null) {
                continue;
            }
            if (rules.guiFont() != null) {
                guiFont = rules.guiFont();
            }
            if (rules.buttonFont() != null) {
                buttonFont = rules.buttonFont();
            }
            if (rules.shadowsGlobal() != null) {
                shadowsGlobalValue = rules.shadowsGlobal();
            }
        }
        guiFontRule = guiFont;
        buttonFontRules = buttonFont;
        shadowsGlobal = shadowsGlobalValue;
    }

    // Mechanism similar to GTNHLib's PackMcmetaReader. Will be refactored into one common implementation if a third use case arises.
    private static PackDarkModeRules readPackRules(IResourcePack pack) {
        try (InputStream stream = openPackMcmeta(pack)) {
            if (stream == null) {
                return null;
            }
            JsonElement rootElement = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!rootElement.isJsonObject()) {
                return null;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has(ROOT_KEY) || !root.get(ROOT_KEY).isJsonObject()) {
                return null;
            }
            JsonObject darkModeUtils = root.getAsJsonObject(ROOT_KEY);
            int schema = darkModeUtils.has("schema") ? darkModeUtils.get("schema").getAsInt() : -1;
            if (schema != SUPPORTED_SCHEMA) {
                LOGGER.warn("Unsupported dark_mode_utils schema {} in pack {}", schema, pack.getPackName());
                return null;
            }
            JsonObject target = darkModeUtils.has("target") && darkModeUtils.get("target").isJsonObject()
                ? darkModeUtils.getAsJsonObject("target")
                : null;
            if (target == null) {
                return null;
            }

            FontRecolorRule guiFont = target.has("gui_font") && target.get("gui_font").isJsonObject()
                ? parseFontRecolorRule(target.getAsJsonObject("gui_font"), pack.getPackName(), "gui_font")
                : null;

            ButtonFontRules buttonFont = target.has("button_font") && target.get("button_font").isJsonObject()
                ? parseButtonFontRules(target.getAsJsonObject("button_font"), pack.getPackName())
                : null;

            Boolean shadowsGlobal = target.has("shadows_global")
                ? target.get("shadows_global").getAsBoolean()
                : null;

            return new PackDarkModeRules(guiFont, buttonFont, shadowsGlobal);
        } catch (IOException e) {
            // pack.mcmeta missing/unreadable
            return null;
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dark_mode_utils block in pack {}: {}", pack.getPackName(), e.toString());
            return null;
        }
    }
    /** Parses the three independently configurable button states. */
    private static ButtonFontRules parseButtonFontRules(JsonObject buttonFont, String packName) {
        ButtonColorRule enabled = parseButtonState(buttonFont, "enabled", packName);
        ButtonColorRule hovered = parseButtonState(buttonFont, "hovered", packName);
        ButtonColorRule disabled = parseButtonState(buttonFont, "disabled", packName);
        if (enabled == null && hovered == null && disabled == null) {
            return null;
        }
        return new ButtonFontRules(enabled, hovered, disabled);
    }

    /** Reads one button color and reports malformed pack rules. */
    private static ButtonColorRule parseButtonState(JsonObject buttonFont, String state, String packName) {
        if (!buttonFont.has(state) || !buttonFont.get(state).isJsonObject()) {
            return null;
        }
        try {
            String outputValue = buttonFont.getAsJsonObject(state).get("output").getAsString().trim();
            boolean debugPulse = DEBUG_PULSE.equalsIgnoreCase(outputValue);
            return new ButtonColorRule(debugPulse ? 0 : parseHexColor(outputValue), debugPulse);
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dark_mode_utils button_font.{} rule in pack {}: {}", state, packName, e.toString());
            return null;
        }
    }

    /** Reads a GUI color range, replacement color, and shadow preference. */
    private static FontRecolorRule parseFontRecolorRule(JsonObject obj, String packName, String context) {
        try {
            JsonObject input = obj.getAsJsonObject("input");
            int minimum = parseHexColor(input.get("minimum").getAsString());
            int maximum = parseHexColor(input.get("maximum").getAsString());
            String outputValue = obj.get("output").getAsString().trim();
            boolean debugPulse = DEBUG_PULSE.equalsIgnoreCase(outputValue);
            int output = debugPulse ? 0 : parseHexColor(outputValue);
            boolean shadow = obj.has("shadow") && obj.get("shadow").getAsBoolean();
            return new FontRecolorRule(minimum, maximum, output, debugPulse, shadow);
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dark_mode_utils {} rule in pack {}: {}", context, packName, e.toString());
            return null;
        }
    }

    /** Converts a configured color to a 24-bit RGB value. */
    private static int parseHexColor(String value) {
        return Integer.decode(value.trim()) & 0x00FFFFFF;
    }

    /** Opens pack metadata through either mapped resource-pack method name. */
    private static InputStream openPackMcmeta(IResourcePack pack) throws IOException {
        Method method = findGetInputStreamByName(pack.getClass());
        if (method == null) {
            return null;
        }
        try {
            return (InputStream) method.invoke(pack, "pack.mcmeta");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** Caches the metadata stream method found in a pack's class hierarchy. */
    private static Method findGetInputStreamByName(Class<?> type) {
        if (INPUT_STREAM_METHOD_CACHE.containsKey(type)) {
            return INPUT_STREAM_METHOD_CACHE.get(type);
        }
        Method found = null;
        outer: for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : INPUT_STREAM_METHOD_NAMES) {
                try {
                    found = current.getDeclaredMethod(name, String.class);
                    found.setAccessible(true);
                    break outer;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        INPUT_STREAM_METHOD_CACHE.put(type, found);
        return found;
    }

    public static final long NO_RECOLOR = -1L;

    /** Applies the current GUI recolor rule when the input color matches. */
    public static long computeGuiFontRecolor(int argbColor) {
        FontRecolorRule rule = guiFontRule;
        if (rule == null) {
            return NO_RECOLOR;
        }
        return rule.tryRecolor(argbColor);
    }

    /** Returns the pack's global recolored-text shadow preference. */
    public static boolean shadowsGlobal() {
        return shadowsGlobal;
    }

    // layout: newColor (32), shadowRgb (24, no alpha), zeroes (7), shadow (1)
    /** Encodes a recolored glyph and its shadow without allocation. */
    private static long packRecolor(int newColor, int shadowRgb, boolean shadow) {
        return ((long) newColor << 32) | ((long) (shadowRgb & 0xFFFFFF) << 8) | (shadow ? 1L : 0L);
    }

    /** Extracts the replacement ARGB color. */
    public static int unpackColor(long packed) {
        return (int) (packed >>> 32);
    }

    /** Extracts the replacement shadow RGB color. */
    public static int unpackShadowRgb(long packed) {
        return (int) ((packed >>> 8) & 0xFFFFFF);
    }

    /** Extracts whether the recolored glyph requests a shadow. */
    public static boolean unpackShadow(long packed) {
        return (packed & 1L) != 0;
    }

    /**
     * Inside a button section, swaps text drawn in one of the button's own three colors for the matching button_font
     * color. Pure white also counts as the enabled color, since it's the most likely override of it. Any other color
     * is returned untouched.
     */
    public static int recolorButtonText(int argbColor, int enabledColor, int hoveredColor, int disabledColor) {
        final ButtonFontRules rules = buttonFontRules;
        if (rules == null) {
            return argbColor;
        }
        final int rgb = argbColor & 0x00FFFFFF;
        final ButtonColorRule rule;
        if (rgb == enabledColor || rgb == 0xFFFFFF) { rule = rules.enabled(); }
        else if (rgb == hoveredColor) { rule = rules.hovered(); }
        else if (rgb == disabledColor) { rule = rules.disabled(); }
        else { return argbColor; }

        if (rule == null) {
            return argbColor;
        }
        return (argbColor & 0xFF000000) | (rule.debugPulse() ? computeDebugPulseRgb() : rule.output());
    }

    /** Produces the diagnostic animated color, frozen during HUD caching. */
    private static int computeDebugPulseRgb() {
        final float time = HUDCaching.renderingCacheOverride ? 0f
            : (float) ((System.nanoTime() & 0xFFFFFFFFFFFFL) * DEBUG_PULSE_TIME_SCALE);
        final int animated = (int) (Math.round(0x00007F80 * (Math.sin(2 * time) + 1)) & 0x0000FFFF);
        return 0x00FF0000 | animated;
    }

    private record PackDarkModeRules(FontRecolorRule guiFont, ButtonFontRules buttonFont, Boolean shadowsGlobal) {}

    private record ButtonFontRules(ButtonColorRule enabled, ButtonColorRule hovered, ButtonColorRule disabled) {}

    private record ButtonColorRule(int output, boolean debugPulse) {}

    private static final class FontRecolorRule {

        final int minR, minG, minB;
        final int maxR, maxG, maxB;
        final int output;
        final boolean debugPulse;
        final boolean shadow;

        /** Stores the per-channel thresholds and replacement settings. */
        FontRecolorRule(int minimum, int maximum, int output, boolean debugPulse, boolean shadow) {
            this.minR = (minimum >> 16) & 0xFF;
            this.minG = (minimum >> 8) & 0xFF;
            this.minB = minimum & 0xFF;
            this.maxR = (maximum >> 16) & 0xFF;
            this.maxG = (maximum >> 8) & 0xFF;
            this.maxB = maximum & 0xFF;
            this.output = output;
            this.debugPulse = debugPulse;
            this.shadow = shadow;
        }

        /** Recolors near-black or near-white inputs while preserving alpha. */
        long tryRecolor(int argbColor) {
            final int r = (argbColor >> 16) & 0xFF;
            final int g = (argbColor >> 8) & 0xFF;
            final int b = argbColor & 0xFF;

            final boolean nearBlack = r <= minR && g <= minG && b <= minB;
            final boolean nearWhite = r >= maxR && g >= maxG && b >= maxB;
            if (!nearBlack && !nearWhite) {
                return NO_RECOLOR;
            }

            final int outputRgb = debugPulse ? computeDebugPulseRgb() : output;
            final int newColor = (argbColor & 0xFF000000) | outputRgb;
            final int shadowRgb = (outputRgb & 0xFCFCFC) >> 2;
            return packRecolor(newColor, shadowRgb, shadow);
        }
    }
}
