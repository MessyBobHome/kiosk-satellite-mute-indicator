#!/usr/bin/env python3
"""Generates the Mute Indicator artwork as SVG, then renders PNGs with rsvg-convert."""
import subprocess
import sys
from pathlib import Path

OUT = Path(sys.argv[1])
MIC_OFF = ("M19,11C19,12.19 18.66,13.3 18.1,14.28L16.87,13.05C17.14,12.43 17.3,11.74 17.3,11H19M15,11.16L9,5.18V5"
           "A3,3 0 0,1 12,2A3,3 0 0,1 15,5V11L15,11.16M4.27,3L21,19.73L19.73,21L15.54,16.81C14.77,17.27 13.91,17.58 "
           "13,17.72V21H11V17.72C7.72,17.23 5,14.41 5,11H6.7C6.7,14 9.24,16.1 12,16.1C12.81,16.1 13.6,15.91 14.31,15.58"
           "L12.65,13.92L12,14A3,3 0 0,1 9,11V10.28L3,4.27L4.27,3Z")
RED = "#FF3B30"
FONT = "Inter, 'Inter Display', DejaVu Sans, sans-serif"


def glyph(x, y, size, color=RED):
    s = size / 24
    return f'<path d="{MIC_OFF}" fill="{color}" transform="translate({x:.1f},{y:.1f}) scale({s:.4f})"/>'


def badge(cx, cy, size, bg_opacity=0.55):
    """The plugin's default look: a red mic-off on a translucent black circle."""
    r = size / 2
    g = size * 0.6
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#000" fill-opacity="{bg_opacity}"/>'
            + glyph(cx - g / 2, cy - g / 2, g))


def edge_glow(id_, x, y, w, h, depth, opacity=0.6, color="#FF0000"):
    """All-edges glow, the plugin's default gradient: four linear bands."""
    stops = (f'<stop offset="0" stop-color="{color}" stop-opacity="{opacity}"/>'
             f'<stop offset="0.35" stop-color="{color}" stop-opacity="{opacity * 0.45:.3f}"/>'
             f'<stop offset="0.7" stop-color="{color}" stop-opacity="{opacity * 0.12:.3f}"/>'
             f'<stop offset="1" stop-color="{color}" stop-opacity="0"/>')
    defs = (f'<linearGradient id="{id_}t" x1="0" y1="0" x2="0" y2="1">{stops}</linearGradient>'
            f'<linearGradient id="{id_}b" x1="0" y1="1" x2="0" y2="0">{stops}</linearGradient>'
            f'<linearGradient id="{id_}l" x1="0" y1="0" x2="1" y2="0">{stops}</linearGradient>'
            f'<linearGradient id="{id_}r" x1="1" y1="0" x2="0" y2="0">{stops}</linearGradient>')
    body = (f'<rect x="{x}" y="{y}" width="{w}" height="{depth}" fill="url(#{id_}t)"/>'
            f'<rect x="{x}" y="{y + h - depth}" width="{w}" height="{depth}" fill="url(#{id_}b)"/>'
            f'<rect x="{x}" y="{y}" width="{depth}" height="{h}" fill="url(#{id_}l)"/>'
            f'<rect x="{x + w - depth}" y="{y}" width="{depth}" height="{h}" fill="url(#{id_}r)"/>')
    return defs, body


def corner_glow(id_, cx, cy, radius, opacity=0.7, color="#FF0000"):
    defs = (f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{radius}" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{color}" stop-opacity="{opacity}"/>'
            f'<stop offset="0.35" stop-color="{color}" stop-opacity="{opacity * 0.45:.3f}"/>'
            f'<stop offset="0.7" stop-color="{color}" stop-opacity="{opacity * 0.12:.3f}"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')
    return defs, f'<circle cx="{cx}" cy="{cy}" r="{radius}" fill="url(#{id_})"/>'


def screensaver(id_, x, y, w, h):
    """A generic clock screensaver over a dusky photo-like background."""
    defs = (f'<linearGradient id="{id_}sky" x1="0" y1="0" x2="0.35" y2="1">'
            '<stop offset="0" stop-color="#1B2A44"/><stop offset="0.55" stop-color="#3B3552"/>'
            '<stop offset="1" stop-color="#6B4A4A"/></linearGradient>'
            f'<linearGradient id="{id_}hill" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#15192A"/><stop offset="1" stop-color="#0B0D16"/></linearGradient>'
            f'<clipPath id="{id_}clip"><rect x="{x}" y="{y}" width="{w}" height="{h}"/></clipPath>')
    u = w / 1280
    t = h / 800
    hills = (f'<path d="M{x},{y + 560 * t} C{x + 240 * u},{y + 470 * t} {x + 420 * u},{y + 600 * t} {x + 680 * u},{y + 520 * t} '
             f'S{x + 1100 * u},{y + 430 * t} {x + w},{y + 500 * t} V{y + h} H{x} Z" fill="#20223A" opacity="0.9"/>'
             f'<path d="M{x},{y + 650 * t} C{x + 300 * u},{y + 590 * t} {x + 560 * u},{y + 700 * t} {x + 860 * u},{y + 630 * t} '
             f'S{x + 1180 * u},{y + 600 * t} {x + w},{y + 640 * t} V{y + h} H{x} Z" fill="url(#{id_}hill)"/>')
    body = (f'<g clip-path="url(#{id_}clip)"><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#{id_}sky)"/>'
            + hills
            + f'<text x="{x + 80 * u}" y="{y + 300 * t}" font-family="{FONT}" font-weight="300" font-size="{200 * u:.1f}" '
              f'fill="#FFFFFF" fill-opacity="0.92">10:42</text>'
            + f'<text x="{x + 88 * u}" y="{y + 370 * t}" font-family="{FONT}" font-weight="400" font-size="{40 * u:.1f}" '
              f'fill="#FFFFFF" fill-opacity="0.7">Monday, November 16</text></g>')
    return defs, body


def device(x, y, w, h, content_defs, content_body):
    """A simple tablet frame around a 16:10 screen."""
    bezel = w * 0.035
    sw = w - 2 * bezel
    sh = sw / 1.6  # the screen is exactly 16:10, like an Echo Show 8
    h = sh + 2 * bezel
    sx, sy = x + bezel, y + bezel
    cid = "clip%d%d" % (int(x), int(y))
    defs = content_defs + f'<clipPath id="{cid}"><rect x="{sx}" y="{sy}" width="{sw}" height="{sh}"/></clipPath>'
    k = sw / 1280
    body = (f'<rect x="{x}" y="{y + 10}" width="{w}" height="{h}" rx="{bezel * 1.4}" fill="#000" fill-opacity="0.35"/>'
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{bezel * 1.4}" fill="#16181D" stroke="#2C2F36" stroke-width="2"/>'
            f'<g clip-path="url(#{cid})"><g transform="translate({sx},{sy}) scale({k:.5f})">{content_body}</g></g>')
    return defs, body


def screen(kind, id_):
    """The 1280x800 screen content for each indicator style."""
    d, b = screensaver(id_, 0, 0, 1280, 800)
    if kind in ("gradient", "both"):
        gd, gb = edge_glow(id_ + "g", 0, 0, 1280, 800, 150)
        d, b = d + gd, b + gb
    if kind == "corner":
        gd, gb = corner_glow(id_ + "c", 1280, 0, 420, 0.75)
        d, b = d + gd, b + gb
    if kind in ("icon", "both", "corner"):
        b += badge(1280 - 24 * 1.33 - 37, 24 * 1.33 + 37, 75)
    return d, b


def svg(width, height, defs, body, bg=None):
    background = f'<rect width="{width}" height="{height}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
            f'<defs>{defs}</defs>{background}{body}</svg>')


def render(name, content, width):
    src = OUT / "src" / (name + ".svg")
    src.parent.mkdir(parents=True, exist_ok=True)
    src.write_text(content)
    png = OUT / (name + ".png")
    subprocess.run(["rsvg-convert", "-w", str(width), "-o", str(png), str(src)], check=True)
    return png


def icon():
    s = 256
    defs = ('<linearGradient id="tile" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#23262E"/><stop offset="1" stop-color="#121419"/></linearGradient>'
            '<clipPath id="round"><rect width="256" height="256" rx="58"/></clipPath>')
    gd, gb = edge_glow("ig", 0, 0, s, s, 64, opacity=0.8)
    body = (f'<g clip-path="url(#round)"><rect width="{s}" height="{s}" fill="url(#tile)"/>{gb}</g>'
            + badge(128, 128, 172, bg_opacity=0.62))
    return svg(s, s, defs + gd, body)


def banner():
    w, h = 1280, 640
    defs = ('<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
            '<stop offset="0" stop-color="#101217"/><stop offset="1" stop-color="#1C1F27"/></linearGradient>'
            '<radialGradient id="halo" cx="930" cy="330" r="420" gradientUnits="userSpaceOnUse">'
            '<stop offset="0" stop-color="#FF3B30" stop-opacity="0.18"/><stop offset="1" stop-color="#FF3B30" stop-opacity="0"/>'
            '</radialGradient>')
    sd, sb = screen("both", "bn")
    dd, db = device(610, 150, 600, 375, sd, sb)
    text = (f'<g transform="translate(80,0)">'
            + badge(44, 214, 88, bg_opacity=0.9)
            + f'<text x="0" y="330" font-family="{FONT}" font-weight="700" font-size="72" fill="#FFFFFF">Mute Indicator</text>'
            + f'<text x="2" y="378" font-family="{FONT}" font-weight="500" font-size="28" fill="#FF6B61">for Kiosk Satellite</text>'
            + f'<text x="2" y="440" font-family="{FONT}" font-weight="400" font-size="24" fill="#B8BCC6">See when your kiosk is muted,</text>'
            + f'<text x="2" y="474" font-family="{FONT}" font-weight="400" font-size="24" fill="#B8BCC6">even on the screensaver.</text>'
            + '</g>')
    body = f'<rect width="{w}" height="{h}" fill="url(#bg)"/><rect width="{w}" height="{h}" fill="url(#halo)"/>' + db + text
    return svg(w, h, defs + dd, body)


def preview(kind, label):
    w, h = 960, 660
    sd, sb = screen(kind, "p" + kind)
    dd, db = device(60, 40, 840, 525, sd, sb)
    caption = (f'<text x="{w / 2}" y="625" text-anchor="middle" font-family="{FONT}" font-weight="500" '
               f'font-size="26" fill="#D5D8DF">{label}</text>')
    return svg(w, h, dd, db + caption, bg="#121419")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    print(render("icon", icon(), 256))
    print(render("banner", banner(), 1280))
    print(render("style-icon", preview("icon", "Icon"), 960))
    print(render("style-gradient", preview("gradient", "Gradient: all edges"), 960))
    print(render("style-both", preview("both", "Icon and gradient"), 960))
    print(render("style-corner", preview("corner", "Icon with a top right corner gradient"), 960))
