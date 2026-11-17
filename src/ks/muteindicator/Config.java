// SPDX-License-Identifier: Apache-2.0
package ks.muteindicator;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.jxl.kiosk.plugins.OverlaySpec;

/**
 * An immutable snapshot of the plugin's settings. Kiosk Satellite validates
 * settings against the manifest before they arrive, but every value is still
 * read defensively so a missing or odd value falls back to its default
 * instead of disabling the plugin.
 */
final class Config {
    // Option labels, exactly as declared in kiosk-satellite-plugin.json.
    static final String SOURCE_KIOSK = "This kiosk";
    static final String SOURCE_ENTITY = "Home Assistant entity";

    static final String STYLE_ICON = "Icon";
    static final String STYLE_GRADIENT = "Gradient";
    static final String STYLE_BOTH = "Icon and gradient";

    static final String SHOW_ALWAYS = "Everywhere";
    static final String SHOW_SCREENSAVER = "Only on the screensaver";
    static final String SHOW_AWAKE = "Only when the screensaver is off";

    static final String BG_CIRCLE = "Circle";
    static final String BG_SQUARE = "Rounded square";
    static final String BG_NONE = "None";

    static final String EDGE_TOP = "Top edge";
    static final String EDGE_BOTTOM = "Bottom edge";
    static final String EDGE_LEFT = "Left edge";
    static final String EDGE_RIGHT = "Right edge";
    static final String EDGE_ALL = "All edges";
    static final String CORNER_TOP_LEFT = "Top left corner";
    static final String CORNER_TOP_RIGHT = "Top right corner";
    static final String CORNER_BOTTOM_LEFT = "Bottom left corner";
    static final String CORNER_BOTTOM_RIGHT = "Bottom right corner";

    static final String DEFAULT_ICON = "mdi:microphone-off";

    final String source;
    final String entity;
    final Set<String> mutedStates;
    final String style;
    final String showOn;
    final int fadeMs;
    final boolean pulse;
    final boolean previewWhileEditing;
    final Icon icon;
    final Gradient gradient;

    private Config(Map<String, Object> s) {
        source = choice(s, "source", SOURCE_KIOSK, SOURCE_KIOSK, SOURCE_ENTITY);
        entity = string(s, "entity", "").trim();
        mutedStates = states(string(s, "mutedStates", "on"));
        style = choice(s, "style", STYLE_ICON, STYLE_ICON, STYLE_GRADIENT, STYLE_BOTH);
        showOn = choice(s, "showOn", SHOW_ALWAYS, SHOW_ALWAYS, SHOW_SCREENSAVER, SHOW_AWAKE);
        fadeMs = number(s, "fadeDuration", 400, 50, 1000);
        pulse = bool(s, "pulse", false);
        previewWhileEditing = bool(s, "previewWhileEditing", true);
        icon = new Icon(
            nonEmpty(string(s, "icon", DEFAULT_ICON), DEFAULT_ICON),
            anchor(choice(s, "iconPosition", "Top right",
                "Top left", "Top center", "Top right", "Middle left", "Center", "Middle right",
                "Bottom left", "Bottom center", "Bottom right")),
            number(s, "iconSize", 56, 16, 320),
            number(s, "iconMargin", 24, 0, 300),
            number(s, "iconOffsetX", 0, -1000, 1000),
            number(s, "iconOffsetY", 0, -1000, 1000),
            color(s, "iconColor", 0xFFFF3B30),
            number(s, "iconOpacity", 100, 5, 100) / 100f,
            choice(s, "iconBackground", BG_CIRCLE, BG_CIRCLE, BG_SQUARE, BG_NONE),
            color(s, "iconBackgroundColor", 0xFF000000),
            number(s, "iconBackgroundOpacity", 55, 0, 100) / 100f,
            pulse);
        gradient = new Gradient(
            choice(s, "gradientPosition", EDGE_ALL, EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT, EDGE_ALL,
                CORNER_TOP_LEFT, CORNER_TOP_RIGHT, CORNER_BOTTOM_LEFT, CORNER_BOTTOM_RIGHT),
            number(s, "gradientSize", 120, 20, 800),
            color(s, "gradientColor", 0xFFFF0000),
            number(s, "gradientOpacity", 60, 5, 100) / 100f,
            pulse);
    }

    static Config from(Map<String, Object> settings) {
        return new Config(settings == null ? Collections.<String, Object>emptyMap() : settings);
    }

    boolean followsEntity() { return SOURCE_ENTITY.equals(source); }

    /** The entity to follow, or null when following the kiosk or none is picked. */
    String entityToFollow() { return followsEntity() && !entity.isEmpty() ? entity : null; }

    boolean showsIcon() { return !STYLE_GRADIENT.equals(style); }

    boolean showsGradient() { return !STYLE_ICON.equals(style); }

    /** Whether the screensaver setting allows the indicator right now. */
    boolean allowedWith(boolean screensaverActive) {
        if (SHOW_SCREENSAVER.equals(showOn)) return screensaverActive;
        if (SHOW_AWAKE.equals(showOn)) return !screensaverActive;
        return true;
    }

    /** Whether a Home Assistant state string means muted. */
    boolean isMutedState(Object state) {
        return state != null && mutedStates.contains(String.valueOf(state).trim().toLowerCase(Locale.ROOT));
    }

    /** Where and how big the icon overlay is. Touches always pass through. */
    OverlaySpec iconSpec() {
        return animated(OverlaySpec.at(icon.anchor)
            .size(icon.size, icon.size)
            .inset(icon.margin)
            .offset(icon.offsetX, icon.offsetY));
    }

    /** Where and how big the gradient overlay is. Touches always pass through. */
    OverlaySpec gradientSpec() {
        int d = gradient.size;
        OverlaySpec spec;
        switch (gradient.position) {
            case EDGE_TOP: spec = OverlaySpec.at(OverlaySpec.TOP).size(OverlaySpec.FILL, d); break;
            case EDGE_BOTTOM: spec = OverlaySpec.at(OverlaySpec.BOTTOM).size(OverlaySpec.FILL, d); break;
            case EDGE_LEFT: spec = OverlaySpec.at(OverlaySpec.LEFT).size(d, OverlaySpec.FILL); break;
            case EDGE_RIGHT: spec = OverlaySpec.at(OverlaySpec.RIGHT).size(d, OverlaySpec.FILL); break;
            case CORNER_TOP_LEFT: spec = OverlaySpec.at(OverlaySpec.TOP_LEFT).size(d, d); break;
            case CORNER_TOP_RIGHT: spec = OverlaySpec.at(OverlaySpec.TOP_RIGHT).size(d, d); break;
            case CORNER_BOTTOM_LEFT: spec = OverlaySpec.at(OverlaySpec.BOTTOM_LEFT).size(d, d); break;
            case CORNER_BOTTOM_RIGHT: spec = OverlaySpec.at(OverlaySpec.BOTTOM_RIGHT).size(d, d); break;
            default: spec = OverlaySpec.fullScreen(); break;
        }
        return animated(spec);
    }

    /**
     * Draw over the screensaver, the menu and camera views, let every touch
     * through and ignore back, so the indicator never gets in the way and
     * only the mute state decides whether it shows.
     */
    private OverlaySpec animated(OverlaySpec spec) {
        spec.onTop(true).touchable(false).closeOnBack(false);
        try {
            spec.enter(OverlaySpec.FADE).exit(OverlaySpec.FADE).transitionDuration(fadeMs);
        } catch (LinkageError olderHost) {
            // Releases without transitions show and hide at once.
        }
        return spec;
    }

    /** Text that changes whenever the icon overlay would look or sit differently. */
    String iconSignature() { return "icon|" + fadeMs + "|" + icon; }

    /** Text that changes whenever the gradient overlay would look or sit differently. */
    String gradientSignature() { return "gradient|" + fadeMs + "|" + gradient; }

    static final class Icon {
        final String name;
        final String anchor;
        final int size, margin, offsetX, offsetY;
        final int color;
        final float opacity;
        final String background;
        final int backgroundColor;
        final float backgroundOpacity;
        final boolean pulse;

        Icon(String name, String anchor, int size, int margin, int offsetX, int offsetY, int color, float opacity,
             String background, int backgroundColor, float backgroundOpacity, boolean pulse) {
            this.name = name; this.anchor = anchor; this.size = size; this.margin = margin;
            this.offsetX = offsetX; this.offsetY = offsetY; this.color = color; this.opacity = opacity;
            this.background = background; this.backgroundColor = backgroundColor;
            this.backgroundOpacity = backgroundOpacity; this.pulse = pulse;
        }

        @Override public String toString() {
            return name + "|" + anchor + "|" + size + "|" + margin + "|" + offsetX + "|" + offsetY + "|"
                + Integer.toHexString(color) + "|" + opacity + "|" + background + "|"
                + Integer.toHexString(backgroundColor) + "|" + backgroundOpacity + "|" + pulse;
        }
    }

    static final class Gradient {
        final String position;
        final int size;
        final int color;
        final float opacity;
        final boolean pulse;

        Gradient(String position, int size, int color, float opacity, boolean pulse) {
            this.position = position; this.size = size; this.color = color; this.opacity = opacity; this.pulse = pulse;
        }

        @Override public String toString() {
            return position + "|" + size + "|" + Integer.toHexString(color) + "|" + opacity + "|" + pulse;
        }
    }

    // ---- Reading values --------------------------------------------------

    static String anchor(String position) {
        switch (position) {
            case "Top left": return OverlaySpec.TOP_LEFT;
            case "Top center": return OverlaySpec.TOP;
            case "Middle left": return OverlaySpec.LEFT;
            case "Center": return OverlaySpec.CENTER;
            case "Middle right": return OverlaySpec.RIGHT;
            case "Bottom left": return OverlaySpec.BOTTOM_LEFT;
            case "Bottom center": return OverlaySpec.BOTTOM;
            case "Bottom right": return OverlaySpec.BOTTOM_RIGHT;
            default: return OverlaySpec.TOP_RIGHT;
        }
    }

    private static String string(Map<String, Object> s, String key, String fallback) {
        Object value = s.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean bool(Map<String, Object> s, String key, boolean fallback) {
        Object value = s.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int number(Map<String, Object> s, String key, int fallback, int min, int max) {
        Object value = s.get(key);
        if (!(value instanceof Number)) return fallback;
        double d = ((Number) value).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return fallback;
        return (int) Math.max(min, Math.min(max, Math.round(d)));
    }

    private static String choice(Map<String, Object> s, String key, String fallback, String... options) {
        Object value = s.get(key);
        return value instanceof String && Arrays.asList(options).contains(value) ? (String) value : fallback;
    }

    /** Parses #RRGGBB into an opaque ARGB color. */
    static int color(Map<String, Object> s, String key, int fallback) {
        Object value = s.get(key);
        if (!(value instanceof String)) return fallback;
        String text = ((String) value).trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(text, 16);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static Set<String> states(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            String state = part.trim().toLowerCase(Locale.ROOT);
            if (!state.isEmpty()) out.add(state);
        }
        if (out.isEmpty()) out.add("on");
        return Collections.unmodifiableSet(out);
    }
}
