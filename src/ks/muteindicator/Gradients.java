// SPDX-License-Identifier: Apache-2.0
package ks.muteindicator;

/** Small colour helpers shared by the views, kept free of Android so they can be tested. */
final class Gradients {
    /** One gentle pulse every two seconds. */
    static final long PULSE_MS = 2000;

    private Gradients() {}

    /** The color with its alpha set to {@code opacity} (0 to 1), keeping its red, green and blue. */
    static int withAlpha(int color, float opacity) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * The pulse's alpha, from 40% to 100% of full (102 to 255), following a
     * cosine so it eases in and out rather than blinking.
     */
    static int breath(long uptimeMs) {
        double phase = (uptimeMs % PULSE_MS) / (double) PULSE_MS;
        double wave = 0.5 - 0.5 * Math.cos(phase * Math.PI * 2);
        return (int) Math.round(102 + 153 * wave);
    }

    /**
     * Colors for a fade from the edge inward: full strength at the edge, a
     * soft shoulder, then fully transparent in the same hue so the fade never
     * passes through grey.
     */
    static int[] fade(int color, float opacity) {
        return new int[] {
            withAlpha(color, opacity),
            withAlpha(color, opacity * 0.45f),
            withAlpha(color, opacity * 0.12f),
            withAlpha(color, 0f),
        };
    }

    /** Where each {@link #fade} color sits along the gradient, 0 at the edge. */
    static final float[] FADE_STOPS = {0f, 0.35f, 0.7f, 1f};
}
