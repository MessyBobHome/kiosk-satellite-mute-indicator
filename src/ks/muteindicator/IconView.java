// SPDX-License-Identifier: Apache-2.0
package ks.muteindicator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import me.jxl.kiosk.plugins.KsTheme;

/**
 * The mute symbol: a Material Design Icon from Kiosk Satellite's own set,
 * optionally on a circle or rounded square so it stays readable over photos.
 * It fills the overlay, so the overlay's size is the icon's size.
 */
final class IconView extends View {
    private final Config.Icon look;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path scaled = new Path();
    private final RectF box = new RectF();
    private Path icon;

    IconView(Context context, KsTheme theme, Config.Icon look) {
        super(context);
        this.look = look;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        icon = lookUp(theme, look.name);
        if (icon == null && !Config.DEFAULT_ICON.equals(look.name)) icon = lookUp(theme, Config.DEFAULT_ICON);
        // Opacity goes into the paint, not View.setAlpha, so Kiosk Satellite's
        // fade transition is free to animate the view's own alpha.
        setContentDescription("Muted");
    }

    /** Releases before KS's own icon set, or an unknown name, give null. */
    private static Path lookUp(KsTheme theme, String name) {
        try {
            return theme.iconPath(name);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        // onDraw runs outside the guards Kiosk Satellite puts around plugin
        // callbacks, and an exception here would end the app.
        try {
            draw(canvas, getWidth(), getHeight());
        } catch (RuntimeException ignored) {
            return;
        }
        if (look.pulse) postInvalidateOnAnimation();
    }

    private void draw(Canvas canvas, int width, int height) {
        float side = Math.min(width, height);
        if (side <= 0) return;
        float left = (width - side) / 2f, top = (height - side) / 2f;
        float strength = look.opacity * (look.pulse ? Gradients.breath(SystemClock.uptimeMillis()) : 255) / 255f;

        boolean background = !Config.BG_NONE.equals(look.background) && look.backgroundOpacity > 0f;
        if (background) {
            fill.setColor(Gradients.withAlpha(look.backgroundColor, look.backgroundOpacity * strength));
            box.set(left, top, left + side, top + side);
            if (Config.BG_SQUARE.equals(look.background)) {
                float r = side * 0.24f;
                canvas.drawRoundRect(box, r, r, fill);
            } else {
                canvas.drawOval(box, fill);
            }
        }

        // The glyph takes 60% of a background shape, or nearly all of the space without one.
        float glyph = side * (background ? 0.6f : 0.92f);
        float gx = left + (side - glyph) / 2f, gy = top + (side - glyph) / 2f;
        int color = Gradients.withAlpha(look.color, strength);
        if (icon != null) {
            matrix.setScale(glyph / 24f, glyph / 24f);
            matrix.postTranslate(gx, gy);
            scaled.reset();
            icon.transform(matrix, scaled);
            fill.setColor(color);
            canvas.drawPath(scaled, fill);
        } else {
            drawFallback(canvas, gx, gy, glyph, color);
        }
    }

    /** A plain microphone with a slash, for when no icon set is available. */
    private void drawFallback(Canvas canvas, float x, float y, float size, int color) {
        float u = size / 24f;
        stroke.setColor(color);
        stroke.setStrokeWidth(2f * u);
        fill.setColor(color);
        box.set(x + 9 * u, y + 2 * u, x + 15 * u, y + 14 * u);
        canvas.drawRoundRect(box, 3 * u, 3 * u, fill);
        box.set(x + 5 * u, y + 5 * u, x + 19 * u, y + 17 * u);
        canvas.drawArc(box, 0, 180, false, stroke);
        canvas.drawLine(x + 12 * u, y + 17 * u, x + 12 * u, y + 21 * u, stroke);
        canvas.drawLine(x + 8 * u, y + 21 * u, x + 16 * u, y + 21 * u, stroke);
        stroke.setStrokeWidth(2.4f * u);
        canvas.drawLine(x + 3 * u, y + 3 * u, x + 21 * u, y + 21 * u, stroke);
    }
}
