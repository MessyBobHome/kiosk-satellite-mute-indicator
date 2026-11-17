// SPDX-License-Identifier: Apache-2.0
package ks.muteindicator;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Shows that the kiosk's microphone is muted with a symbol, a red glow, or
 * both, drawn over the dashboard and the screensaver. Touches always pass
 * through, so the indicator never blocks the screen or wakes the screensaver.
 *
 * <p>By default it follows the kiosk's own Voice Satellite mute through
 * {@code wakeword.state}, the same mute as the VS Mute switch in Home
 * Assistant, so it keeps working while Home Assistant is unreachable. It can
 * follow any Home Assistant entity instead.
 */
public final class MuteIndicatorPlugin implements KioskPlugin {
    static final String ICON = "icon";
    static final String GRADIENT = "gradient";
    static final String PREVIEW_COMMAND = "preview";
    static final String KIOSK_EVENT = "wakeword.state";
    static final String SCREENSAVER_EVENT = "screensaver.state";
    static final String ENTITY_PREFIX = "ha.entity.";

    /** How long the Preview action shows the indicator. Package-private for tests. */
    long previewMs = 10_000;
    /** How long the indicator shows after the settings page opens or a setting changes. */
    long editPreviewMs = 15_000;
    /** How long to wait before retrying when Kiosk Satellite's overlay rate limit is hit. */
    long retryMs = 1_100;

    private PluginHost host;
    private Config config = Config.from(Collections.<String, Object>emptyMap());
    private ScheduledExecutorService timer;

    private boolean kioskMuted;
    private boolean entityMuted;
    private boolean screensaverActive;
    private boolean settingsOpen;
    /**
     * A preview is showing. Every preview ends on its own timer: the
     * settings page counts as open for as long as it sits on the kiosk or in
     * a Remote Admin tab, so tying the preview to the page alone could leave
     * it up indefinitely.
     */
    private boolean previewing;
    /** The preview came from the settings page, so closing the page ends it. */
    private boolean pagePreview;
    /** Bumped by each preview start or end, so only the latest preview's timer can end it. */
    private int previewToken;

    // Bumped by each event so a read that was requested earlier cannot overwrite it.
    private int kioskEpoch;
    private int screensaverEpoch;

    /** The entity with an active subscription, or null. */
    private String followed;
    /** The last availability and state Kiosk Satellite reported for that entity. */
    private String entityStatus;
    private Object entityState;
    /** What each overlay currently shows, or null while hidden. */
    private String shownIcon, shownGradient;
    private boolean retryPending;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mute-indicator");
            thread.setDaemon(true);
            return thread;
        });
        if (host.isHeadless()) {
            host.status("This device has no screen, so there is nothing to show.", false);
            return;
        }
        // Subscribe first, then read, so no change can slip between the two.
        host.subscribe(KIOSK_EVENT);
        host.subscribe(SCREENSAVER_EVENT);
        readKiosk();
        readScreensaver();
        configure(settings);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        if (host == null || host.isHeadless()) return;
        config = Config.from(settings);
        follow(config.entityToFollow());
        if (settingsOpen && config.previewWhileEditing) {
            // A setting changed on the open page: show the result for a while.
            startPreview(editPreviewMs, true);
        } else if (pagePreview && !config.previewWhileEditing) {
            endPreview();
        }
        report();
        render();
    }

    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        if (host == null || !PREVIEW_COMMAND.equals(command)) return;
        startPreview(previewMs, false);
        report();
        render();
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if (("ks." + KIOSK_EVENT).equals(event)) {
            kioskEpoch++;
            kioskMuted = Boolean.TRUE.equals(payload.get("muted"));
        } else if (("ks." + SCREENSAVER_EVENT).equals(event)) {
            screensaverEpoch++;
            screensaverActive = Boolean.TRUE.equals(payload.get("active"));
        } else if ("settings.page".equals(event)) {
            settingsOpen = Boolean.TRUE.equals(payload.get("open"));
            if (settingsOpen && config.previewWhileEditing) startPreview(editPreviewMs, true);
            else if (!settingsOpen && pagePreview) endPreview();
        } else if (followed != null && ("ks." + ENTITY_PREFIX + followed).equals(event)) {
            onEntity(payload);
        } else {
            // A late update for an entity we stopped following, or something unrelated.
            return;
        }
        report();
        render();
    }

    /** Kiosk Satellite revokes the host and removes overlays before stop, so only local state is released. */
    @Override
    public synchronized void stop() {
        host = null;
        followed = null;
        shownIcon = shownGradient = null;
        if (timer != null) timer.shutdownNow();
        timer = null;
    }

    // ---- Mute state ------------------------------------------------------

    boolean muted() {
        return config.followsEntity() ? entityMuted : kioskMuted;
    }

    boolean previewing() {
        return previewing;
    }

    /** Shows the indicator for {@code ms}, replacing any preview already running. */
    private void startPreview(long ms, boolean fromPage) {
        final int token = ++previewToken;
        previewing = true;
        pagePreview = fromPage;
        if (timer == null) return;
        try {
            timer.schedule(() -> {
                synchronized (MuteIndicatorPlugin.this) {
                    if (token != previewToken || host == null) return;
                    endPreview();
                    try {
                        report();
                        render();
                    } catch (RuntimeException sessionEnded) {
                        // The host was revoked between the check and the call.
                    }
                }
            }, ms, TimeUnit.MILLISECONDS);
        } catch (RuntimeException stopped) {
            // The timer was shut down by stop().
        }
    }

    private void endPreview() {
        previewToken++;
        previewing = false;
        pagePreview = false;
    }

    /** Whether the indicator should be on screen right now. */
    boolean visible() {
        return previewing() || (muted() && config.allowedWith(screensaverActive));
    }

    private void readKiosk() {
        final int epoch = kioskEpoch;
        host.executeCommand("getWakeWordState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (MuteIndicatorPlugin.this) {
                if (host == null || epoch != kioskEpoch) return;
                if (ok && data instanceof Map) {
                    kioskMuted = Boolean.TRUE.equals(((Map<?, ?>) data).get("muted"));
                    report();
                    render();
                } else if (!config.followsEntity()) {
                    host.status("Couldn't read the kiosk's mute state" + (error == null ? "" : ": " + error)
                        + ". Check that Voice Satellite is on.", true);
                }
            }
        });
    }

    private void readScreensaver() {
        final int epoch = screensaverEpoch;
        host.executeCommand("isScreensaverActive", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (MuteIndicatorPlugin.this) {
                if (host == null || epoch != screensaverEpoch || !ok) return;
                screensaverActive = Boolean.TRUE.equals(data);
                render();
            }
        });
    }

    /** Moves the entity subscription to {@code entity}, or drops it when null. */
    private void follow(String entity) {
        if (Objects.equals(entity, followed)) return;
        if (followed != null) {
            try { host.unsubscribe(ENTITY_PREFIX + followed); } catch (RuntimeException ignored) {}
        }
        followed = null;
        entityMuted = false;
        entityStatus = null;
        if (entity == null) return;
        try {
            host.subscribe(ENTITY_PREFIX + entity);
            followed = entity;
        } catch (RuntimeException refused) {
            host.status("Couldn't follow " + entity + ": " + refused.getMessage(), true);
        }
    }

    private void onEntity(Map<String, Object> payload) {
        entityStatus = String.valueOf(payload.get("status"));
        entityState = payload.get("state");
        // Only a real, current state counts. Unknown, unavailable or a lost
        // connection never shows as muted.
        entityMuted = "available".equals(entityStatus) && config.isMutedState(entityState);
    }

    /** Puts a one-line summary on the plugin's settings page. */
    private void report() {
        if (host == null) return;
        String shown = visible() ? (previewing() && !muted() ? " Previewing the indicator." : " Indicator showing.") : "";
        if (!config.followsEntity()) {
            host.status("Following this kiosk's microphone. " + (kioskMuted ? "Muted." : "Not muted.") + shown, false);
        } else if (followed == null) {
            host.status("Pick the Home Assistant entity to follow, such as switch.echo_show_8_vs_mute." + shown, true);
        } else if (entityStatus == null || "connecting".equals(entityStatus)) {
            host.status("Waiting for " + followed + " from Home Assistant." + shown, false);
        } else if ("available".equals(entityStatus)) {
            host.status("Following " + followed + ". " + (entityMuted ? "Muted" : "Not muted")
                + " (state " + entityState + ")." + shown, false);
        } else {
            host.status(followed + " is " + entityStatus.replace('_', ' ')
                + ", so the indicator stays hidden." + shown, true);
        }
    }

    // ---- Overlays --------------------------------------------------------

    /** Shows, replaces or hides each overlay so the screen matches the current state. */
    synchronized void render() {
        if (host == null) return;
        boolean visible = visible();
        String icon = visible && config.showsIcon() ? config.iconSignature() : null;
        String gradient = visible && config.showsGradient() ? config.gradientSignature() : null;
        // Each showOverlay builds a new view, so only call it when something changed.
        if (!Objects.equals(gradient, shownGradient) && apply(GRADIENT, gradient, config)) shownGradient = gradient;
        if (!Objects.equals(icon, shownIcon) && apply(ICON, icon, config)) shownIcon = icon;
    }

    /** Returns whether the overlay now matches {@code signature}. */
    private boolean apply(String key, String signature, Config look) {
        try {
            if (signature == null) {
                host.hideOverlay(key);
            } else if (ICON.equals(key)) {
                host.showOverlay(ICON, look.iconSpec(), new IconFactory(look.icon));
            } else {
                host.showOverlay(GRADIENT, look.gradientSpec(), new GradientFactory(look.gradient));
            }
            return true;
        } catch (UnsupportedOperationException olderHost) {
            host.status("This Kiosk Satellite release can't draw plugin overlays. Update Kiosk Satellite.", true);
            return true;
        } catch (IllegalStateException busy) {
            // The overlay rate limit, or a session that is ending. Try again shortly.
            if (!retryPending) {
                retryPending = true;
                schedule(retryMs);
            }
            return false;
        } catch (IllegalArgumentException invalid) {
            host.status("Couldn't show the indicator: " + invalid.getMessage(), true);
            return true;
        }
    }

    private void schedule(long delayMs) {
        if (timer == null) return;
        try {
            timer.schedule(() -> {
                synchronized (MuteIndicatorPlugin.this) {
                    retryPending = false;
                    try {
                        report();
                        render();
                    } catch (RuntimeException sessionEnded) {
                        // The host was revoked between the check and the call.
                    }
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException stopped) {
            // The timer was shut down by stop().
        }
    }

    private static final class IconFactory implements OverlayFactory {
        private final Config.Icon look;
        IconFactory(Config.Icon look) { this.look = look; }
        @Override public View create(Context context, KsTheme theme) { return new IconView(context, theme, look); }
    }

    private static final class GradientFactory implements OverlayFactory {
        private final Config.Gradient look;
        GradientFactory(Config.Gradient look) { this.look = look; }
        @Override public View create(Context context, KsTheme theme) { return new GradientView(context, look); }
    }
}
