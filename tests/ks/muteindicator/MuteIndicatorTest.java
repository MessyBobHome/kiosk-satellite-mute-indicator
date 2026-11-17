// SPDX-License-Identifier: Apache-2.0
package ks.muteindicator;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the plugin asks Kiosk Satellite for. Drawing the views needs a device. */
public final class MuteIndicatorTest {
    static final class Host implements PluginHost {
        final Map<String, OverlaySpec> shown = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        final Set<String> subscriptions = new LinkedHashSet<>();
        final Map<String, CommandCallback> pending = new LinkedHashMap<>();
        String status;
        boolean statusError;
        int refuseShows;

        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) {
            assert factory != null;
            if (refuseShows > 0) { refuseShows--; throw new IllegalStateException("Too many overlays"); }
            shown.put(key, spec);
            calls.add("show " + key);
        }
        public void hideOverlay(String key) { shown.remove(key); calls.add("hide " + key); }
        public void subscribe(String event) { subscriptions.add(event); }
        public void unsubscribe(String event) { subscriptions.remove(event); }
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            assert arguments.isEmpty();
            pending.put(command, callback);
        }
        public void status(String message, boolean error) { status = message; statusError = error; }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}

        void answer(String command, boolean ok, Object data) {
            CommandCallback callback = pending.remove(command);
            assert callback != null : "no pending " + command;
            callback.onResult(ok, data, ok ? null : "failed");
        }
    }

    static Map<String, Object> settings(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    static Map<String, Object> payload(String key, Object value) {
        return settings(key, value, "time", "2026-11-16T12:00:00Z");
    }

    public static void main(String[] args) throws Exception {
        defaultsFollowTheKiosk();
        readsDoNotOverwriteNewerEvents();
        gradientPositions();
        screensaverRules();
        followsAnEntity();
        previewWhileEditing();
        previewCommandEnds();
        retriesWhenRateLimited();
        configParsing();
        colours();
        System.out.println("MuteIndicatorTest passed");
    }

    static void defaultsFollowTheKiosk() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.start(host, settings());
        assert host.subscriptions.contains("wakeword.state") && host.subscriptions.contains("screensaver.state");
        assert host.pending.containsKey("getWakeWordState") && host.pending.containsKey("isScreensaverActive");
        assert host.shown.isEmpty();

        host.answer("getWakeWordState", true, settings("muted", false));
        assert host.shown.isEmpty() && host.status.contains("Not muted");

        // Muting shows the icon only (the default style), top right, over everything, without taking touches.
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        assert host.shown.keySet().equals(Collections.singleton(MuteIndicatorPlugin.ICON));
        OverlaySpec icon = host.shown.get(MuteIndicatorPlugin.ICON);
        assert OverlaySpec.TOP_RIGHT.equals(icon.anchor());
        assert icon.width() == 56 && icon.height() == 56 && icon.inset() == 24;
        assert icon.offsetX() == 0 && icon.offsetY() == 0;
        assert icon.onTop() && !icon.touchable() && !icon.closeOnBack();
        assert OverlaySpec.FADE.equals(icon.enter()) && OverlaySpec.FADE.equals(icon.exit()) && icon.transitionDuration() == 400;
        assert host.status.contains("Muted.") && !host.statusError;

        // The same state again does not rebuild the view.
        int before = host.calls.size();
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        plugin.onEvent("ks.screensaver.state", payload("active", true));
        assert host.calls.size() == before;

        // Changing how it looks replaces it; changing style to both adds the gradient.
        plugin.configure(settings("iconSize", 80.0, "iconPosition", "Bottom left", "iconOffsetX", -12.0, "style", "Icon and gradient", "fadeDuration", 250.0));
        icon = host.shown.get(MuteIndicatorPlugin.ICON);
        assert icon.width() == 80 && OverlaySpec.BOTTOM_LEFT.equals(icon.anchor()) && icon.offsetX() == -12;
        assert icon.transitionDuration() == 250;
        assert host.shown.containsKey(MuteIndicatorPlugin.GRADIENT);

        // Unmuting hides everything.
        plugin.onEvent("ks.wakeword.state", payload("muted", false));
        assert host.shown.isEmpty();
        plugin.stop();
        // Nothing happens after stop.
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        assert host.shown.isEmpty();
    }

    static void readsDoNotOverwriteNewerEvents() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.start(host, settings());
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        // The read was requested before the event, so its stale answer is ignored.
        host.answer("getWakeWordState", true, settings("muted", false));
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        plugin.stop();
    }

    static void gradientPositions() throws Exception {
        Object[][] cases = {
            {"Top edge", OverlaySpec.TOP, OverlaySpec.FILL, 150},
            {"Bottom edge", OverlaySpec.BOTTOM, OverlaySpec.FILL, 150},
            {"Left edge", OverlaySpec.LEFT, 150, OverlaySpec.FILL},
            {"Right edge", OverlaySpec.RIGHT, 150, OverlaySpec.FILL},
            {"Top left corner", OverlaySpec.TOP_LEFT, 150, 150},
            {"Top right corner", OverlaySpec.TOP_RIGHT, 150, 150},
            {"Bottom left corner", OverlaySpec.BOTTOM_LEFT, 150, 150},
            {"Bottom right corner", OverlaySpec.BOTTOM_RIGHT, 150, 150},
            {"All edges", null, OverlaySpec.FILL, OverlaySpec.FILL},
        };
        for (Object[] c : cases) {
            Host host = new Host();
            MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
            plugin.start(host, settings("style", "Gradient", "gradientPosition", c[0], "gradientSize", 150.0));
            plugin.onEvent("ks.wakeword.state", payload("muted", true));
            assert host.shown.keySet().equals(Collections.singleton(MuteIndicatorPlugin.GRADIENT)) : c[0];
            OverlaySpec spec = host.shown.get(MuteIndicatorPlugin.GRADIENT);
            if (c[1] != null) assert c[1].equals(spec.anchor()) : c[0];
            assert spec.width() == (Integer) c[2] && spec.height() == (Integer) c[3] : c[0];
            assert spec.onTop() && !spec.touchable() && !spec.closeOnBack() : c[0];
            plugin.stop();
        }
    }

    static void screensaverRules() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.start(host, settings("showOn", "Only on the screensaver"));
        host.answer("isScreensaverActive", true, false);
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        assert host.shown.isEmpty();
        plugin.onEvent("ks.screensaver.state", payload("active", true));
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        plugin.configure(settings("showOn", "Only when the screensaver is off"));
        assert host.shown.isEmpty();
        plugin.onEvent("ks.screensaver.state", payload("active", false));
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        plugin.configure(settings("showOn", "Everywhere"));
        plugin.onEvent("ks.screensaver.state", payload("active", true));
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        plugin.stop();
    }

    static void followsAnEntity() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.start(host, settings("source", "Home Assistant entity"));
        assert host.statusError && host.status.contains("Pick");

        plugin.configure(settings("source", "Home Assistant entity", "entity", "switch.echo_show_8_vs_mute"));
        assert host.subscriptions.contains("ha.entity.switch.echo_show_8_vs_mute");
        // The kiosk's own mute is ignored while following an entity.
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        assert host.shown.isEmpty();

        Map<String, Object> on = settings("status", "available", "state", "on", "attributes", Collections.emptyMap());
        plugin.onEvent("ks.ha.entity.switch.echo_show_8_vs_mute", on);
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON) && host.status.contains("Muted");

        plugin.onEvent("ks.ha.entity.switch.echo_show_8_vs_mute", settings("status", "available", "state", "off"));
        assert host.shown.isEmpty();

        plugin.onEvent("ks.ha.entity.switch.echo_show_8_vs_mute", on);
        plugin.onEvent("ks.ha.entity.switch.echo_show_8_vs_mute", settings("status", "disconnected", "state", null));
        assert host.shown.isEmpty() && host.statusError;

        // Custom muted states.
        plugin.configure(settings("source", "Home Assistant entity", "entity", "switch.echo_show_8_vs_mute", "mutedStates", " Muted, ON "));
        plugin.onEvent("ks.ha.entity.switch.echo_show_8_vs_mute", settings("status", "available", "state", "muted"));
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);

        // Switching entity moves the subscription and ignores the old one.
        plugin.configure(settings("source", "Home Assistant entity", "entity", "input_boolean.quiet"));
        assert !host.subscriptions.contains("ha.entity.switch.echo_show_8_vs_mute");
        assert host.subscriptions.contains("ha.entity.input_boolean.quiet");
        assert host.shown.isEmpty();
        plugin.onEvent("ks.ha.entity.switch.echo_show_8_vs_mute", on);
        assert host.shown.isEmpty();

        // Back to the kiosk drops the entity and uses the kiosk's state.
        plugin.configure(settings("source", "This kiosk"));
        assert !host.subscriptions.contains("ha.entity.input_boolean.quiet");
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        plugin.stop();
    }

    static void previewWhileEditing() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.editPreviewMs = 200;
        plugin.start(host, settings("style", "Icon and gradient"));
        plugin.onEvent("settings.page", payload("open", true));
        assert host.shown.size() == 2 && host.status.contains("Previewing");

        // The page stays open (left on the kiosk or in a Remote Admin tab), yet the preview still ends.
        Thread.sleep(600);
        synchronized (plugin) { assert host.shown.isEmpty() : "page preview should end while the page stays open"; }

        // Changing a setting on the open page shows the result again, then it ends again.
        plugin.configure(settings("style", "Icon and gradient", "iconSize", 80.0));
        assert host.shown.size() == 2;
        Thread.sleep(600);
        synchronized (plugin) { assert host.shown.isEmpty() : "preview after a change should end"; }

        // Closing the page ends a page preview at once.
        plugin.configure(settings("style", "Icon"));
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        plugin.onEvent("settings.page", payload("open", false));
        assert host.shown.isEmpty();

        // Turning the option off stops page previews.
        plugin.configure(settings("previewWhileEditing", false));
        plugin.onEvent("settings.page", payload("open", true));
        assert host.shown.isEmpty();
        plugin.configure(settings("previewWhileEditing", false, "iconSize", 90.0));
        assert host.shown.isEmpty();
        plugin.stop();
    }

    static void previewCommandEnds() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.previewMs = 150;
        plugin.start(host, settings());
        plugin.execute("preview", Collections.<String, Object>emptyMap());
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        Thread.sleep(600);
        synchronized (plugin) { assert host.shown.isEmpty() : "preview should end"; }
        plugin.execute("unknown", Collections.<String, Object>emptyMap());
        assert host.shown.isEmpty();

        // Run from the plugin's own page (which is open), the action still ends on time.
        plugin.editPreviewMs = 150;
        plugin.onEvent("settings.page", payload("open", true));
        plugin.execute("preview", Collections.<String, Object>emptyMap());
        assert host.shown.containsKey(MuteIndicatorPlugin.ICON);
        Thread.sleep(600);
        synchronized (plugin) { assert host.shown.isEmpty() : "action preview should end with the page open"; }

        // Pressing it again restarts the 10 seconds instead of ending early.
        plugin.previewMs = 400;
        plugin.execute("preview", Collections.<String, Object>emptyMap());
        Thread.sleep(250);
        plugin.execute("preview", Collections.<String, Object>emptyMap());
        Thread.sleep(250);
        synchronized (plugin) { assert host.shown.containsKey(MuteIndicatorPlugin.ICON) : "second press should restart"; }
        Thread.sleep(400);
        synchronized (plugin) { assert host.shown.isEmpty(); }

        // A real mute outlasts the preview.
        plugin.execute("preview", Collections.<String, Object>emptyMap());
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        Thread.sleep(700);
        synchronized (plugin) { assert host.shown.containsKey(MuteIndicatorPlugin.ICON) : "mute should keep it up"; }
        plugin.stop();
    }

    static void retriesWhenRateLimited() throws Exception {
        Host host = new Host();
        MuteIndicatorPlugin plugin = new MuteIndicatorPlugin();
        plugin.retryMs = 100;
        plugin.start(host, settings());
        host.refuseShows = 1;
        plugin.onEvent("ks.wakeword.state", payload("muted", true));
        assert host.shown.isEmpty();
        Thread.sleep(500);
        synchronized (plugin) { assert host.shown.containsKey(MuteIndicatorPlugin.ICON) : "should retry"; }
        plugin.stop();
    }

    static void configParsing() {
        Config c = Config.from(null);
        assert Config.SOURCE_KIOSK.equals(c.source) && c.entityToFollow() == null;
        assert c.showsIcon() && !c.showsGradient();
        assert c.icon.size == 56 && c.icon.opacity == 1f && c.gradient.opacity == 0.6f;
        assert c.icon.name.equals("mdi:microphone-off");

        // Out-of-range and wrong-typed values fall back or clamp instead of failing.
        c = Config.from(settings("iconSize", 9999.0, "fadeDuration", "fast", "style", "Sparkles",
            "iconOpacity", 50.0, "gradientOpacity", 1.0, "icon", "  ", "source", "Home Assistant entity", "entity", ""));
        assert c.icon.size == 320 && c.fadeMs == 400 && Config.STYLE_ICON.equals(c.style);
        assert c.icon.opacity == 0.5f && c.gradient.opacity == 0.05f && c.icon.name.equals("mdi:microphone-off");
        assert c.followsEntity() && c.entityToFollow() == null;

        c = Config.from(settings("mutedStates", " , "));
        assert c.isMutedState("on") && !c.isMutedState("off") && !c.isMutedState(null);

        // Every position maps to an anchor.
        assert OverlaySpec.TOP.equals(Config.anchor("Top center"));
        assert OverlaySpec.CENTER.equals(Config.anchor("Center"));
        assert OverlaySpec.RIGHT.equals(Config.anchor("Middle right"));
        assert OverlaySpec.BOTTOM.equals(Config.anchor("Bottom center"));
        assert OverlaySpec.TOP_RIGHT.equals(Config.anchor("nonsense"));

        // Signatures change with the look, so a changed setting rebuilds the overlay.
        assert !Config.from(settings("iconOpacity", 50.0)).iconSignature().equals(Config.from(null).iconSignature());
        assert !Config.from(settings("gradientSize", 300.0)).gradientSignature().equals(Config.from(null).gradientSignature());
        assert Config.from(settings("gradientSize", 300.0)).iconSignature().equals(Config.from(null).iconSignature());
    }

    static void colours() {
        assert Config.color(settings("c", "#FF3B30"), "c", 0) == 0xFFFF3B30;
        assert Config.color(settings("c", "00ff00"), "c", 0) == 0xFF00FF00;
        assert Config.color(settings("c", "#XYZ123"), "c", 7) == 7;
        assert Config.color(settings("c", "#FFF"), "c", 7) == 7;
        assert Gradients.withAlpha(0xFFFF0000, 0.5f) == 0x80FF0000;
        assert Gradients.withAlpha(0xFFFF0000, 2f) == 0xFFFF0000;
        assert Gradients.withAlpha(0xFFFF0000, -1f) == 0x00FF0000;
        int[] fade = Gradients.fade(0xFFFF0000, 1f);
        assert fade.length == Gradients.FADE_STOPS.length && fade[0] == 0xFFFF0000 && fade[3] == 0x00FF0000;
        for (long t = 0; t < Gradients.PULSE_MS; t += 50) {
            int b = Gradients.breath(t);
            assert b >= 102 && b <= 255;
        }
        assert Gradients.breath(0) == 102 && Gradients.breath(Gradients.PULSE_MS / 2) == 255;
    }
}
