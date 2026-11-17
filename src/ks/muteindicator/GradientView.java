// SPDX-License-Identifier: Apache-2.0
package ks.muteindicator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * The red glow. An edge fades from full colour at the screen edge to
 * transparent at the chosen depth, a corner fades outward from the corner
 * and All edges draws all four edges at once.
 */
final class GradientView extends View {
    private final Config.Gradient look;
    private final float depth;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    // For All edges: top, bottom, left and right shaders.
    private Shader top, bottom, left, right, single;

    GradientView(Context context, Config.Gradient look) {
        super(context);
        this.look = look;
        this.depth = look.size * context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        try {
            build(w, h);
        } catch (RuntimeException ignored) {
            top = bottom = left = right = single = null;
        }
    }

    private void build(int w, int h) {
        int[] colors = Gradients.fade(look.color, look.opacity);
        float[] stops = Gradients.FADE_STOPS;
        Shader.TileMode clamp = Shader.TileMode.CLAMP;
        top = bottom = left = right = single = null;
        switch (look.position) {
            case Config.EDGE_TOP: single = new LinearGradient(0, 0, 0, h, colors, stops, clamp); break;
            case Config.EDGE_BOTTOM: single = new LinearGradient(0, h, 0, 0, colors, stops, clamp); break;
            case Config.EDGE_LEFT: single = new LinearGradient(0, 0, w, 0, colors, stops, clamp); break;
            case Config.EDGE_RIGHT: single = new LinearGradient(w, 0, 0, 0, colors, stops, clamp); break;
            case Config.CORNER_TOP_LEFT: single = radial(0, 0, Math.max(w, h), colors, stops); break;
            case Config.CORNER_TOP_RIGHT: single = radial(w, 0, Math.max(w, h), colors, stops); break;
            case Config.CORNER_BOTTOM_LEFT: single = radial(0, h, Math.max(w, h), colors, stops); break;
            case Config.CORNER_BOTTOM_RIGHT: single = radial(w, h, Math.max(w, h), colors, stops); break;
            default:
                // All edges: the overlay is full screen and each band is the chosen depth.
                float d = Math.max(1f, Math.min(depth, Math.min(w, h) / 2f));
                top = new LinearGradient(0, 0, 0, d, colors, stops, clamp);
                bottom = new LinearGradient(0, h, 0, h - d, colors, stops, clamp);
                left = new LinearGradient(0, 0, d, 0, colors, stops, clamp);
                right = new LinearGradient(w, 0, w - d, 0, colors, stops, clamp);
                break;
        }
    }

    private static Shader radial(float x, float y, float radius, int[] colors, float[] stops) {
        return new RadialGradient(x, y, Math.max(1f, radius), colors, stops, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        // onDraw runs outside Kiosk Satellite's callback guards, so nothing may escape.
        try {
            paint.setAlpha(look.pulse ? Gradients.breath(SystemClock.uptimeMillis()) : 255);
            int w = getWidth(), h = getHeight();
            if (single != null) {
                paint.setShader(single);
                canvas.drawRect(0, 0, w, h, paint);
            } else if (top != null) {
                float d = Math.max(1f, Math.min(depth, Math.min(w, h) / 2f));
                paint.setShader(top);
                canvas.drawRect(0, 0, w, d, paint);
                paint.setShader(bottom);
                canvas.drawRect(0, h - d, w, h, paint);
                paint.setShader(left);
                canvas.drawRect(0, 0, d, h, paint);
                paint.setShader(right);
                canvas.drawRect(w - d, 0, w, h, paint);
            }
        } catch (RuntimeException ignored) {
            return;
        }
        if (look.pulse) postInvalidateOnAnimation();
    }
}
