"""GarPulse final logo — direction B (letter G whose arm is a heartbeat).

Renders the mark, traces its outline to a Bezier path (potrace) and writes:

  brand/logo-card-1024.png     paper rounded card + coral G   (store / preview)
  brand/logo-card-512.png
  brand/logo-mark-1024.png     coral G on transparency       (dark surfaces)
  brand/logo-mono-1024.png     one-colour ink version
  brand/_build/logo-path.txt   the pathData used by Android

  res/drawable/ic_launcher_foreground.xml   (coral G, adaptive safe zone)
  res/drawable/ic_launcher_monochrome.xml   (same G, untinted)

    python brand/build_logo.py
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import potrace

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "brand" / "_build"
RES = ROOT / "app" / "src" / "main" / "res" / "drawable"

C = 1024          # design canvas
SS = 4            # supersample for the traced master (1024 * 4 = 4096)

CORAL = (0xFF, 0x5A, 0x33, 255)
PAPER = (0xFF, 0xF6, 0xEF, 255)
INK = (0x1A, 0x14, 0x11, 255)

# geometry (same construction as the approved draft, in 1024-space)
CX, CY = 512, 512
RING_R, THICK = 292, 118
APERTURE = (40, -360, 420, -34)     # x0, y0, x1, y1 relative to centre
# The arm ends *inside* the ring band: its round cap (r = ARM_W/2 = 41) has to stay between
# the counter's edge (174) and the outer edge (292), otherwise it pokes through the bowl and
# the silhouette stops being a clean circle. A flat end is used there instead of a cap: it is
# buried in the band, so the bowl stays a perfect circle.
ARM_END = RING_R - 62
# Spike centreline, from the bar's left terminal up to the peak and back down onto the bar.
SPIKE = [(2, 0), (64, 0), (104, -110), (144, 0)]
ARM_W = 82


def draw_mark(scale: int, color=CORAL) -> Image.Image:
    """The G alone on transparency, rendered at C*scale."""
    im = Image.new("RGBA", (C * scale, C * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy = CX * scale, CY * scale
    r, t = RING_R * scale, THICK * scale

    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    # straight aperture above the arm (the letter's mouth)
    x0, y0, x1, y1 = APERTURE
    d.rectangle([cx + x0 * scale, cy + y0 * scale, cx + x1 * scale, cy + y1 * scale],
                fill=(0, 0, 0, 0))
    # counter
    d.ellipse([cx - (r - t), cy - (r - t), cx + (r - t), cy + (r - t)], fill=(0, 0, 0, 0))

    # Crossbar: a rectangle with a rounded left terminal. The right end is cut flat inside the
    # ring band, so the bowl's outer edge stays perfectly round.
    hw = ARM_W / 2 * scale
    left, right = (cx + 2 * scale), (cx + ARM_END * scale)
    d.rectangle([left, cy - hw, right, cy + hw], fill=color)
    d.ellipse([left - hw, cy - hw, left + hw, cy + hw], fill=color)

    # The heartbeat peak, drawn last: its lower half is buried in the crossbar, so the stroke's
    # inner-corner cusp never shows and the bar/spike junction stays clean.
    w = ARM_W * scale
    pts = [(cx + x * scale, cy + y * scale) for x, y in SPIKE]
    d.line(pts, fill=color, width=int(round(w)), joint="curve")
    return im


def rounded_card(size: int) -> Image.Image:
    """Paper rounded square with the coral mark, at `size` px (transparent corners)."""
    master = draw_mark(SS)
    card = Image.new("RGBA", (C * SS, C * SS), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle(
        [124 * SS, 124 * SS, 900 * SS, 900 * SS], radius=176 * SS, fill=PAPER)
    card.alpha_composite(master)
    return card.resize((size, size), Image.LANCZOS)


def trace_path() -> tuple[str, tuple[float, float, float, float]]:
    """Trace the mark and return (pathData in a 108x108 viewport, bbox in 1024-space)."""
    im = draw_mark(SS)
    alpha = np.array(im)[:, :, 3]
    # potracer traces the *False* pixels as the shape, so invert: the mark must be the subject.
    bmp = potrace.Bitmap(alpha <= 127)

    ys, xs = np.nonzero(alpha > 127)
    bx0, bx1 = xs.min() / SS, (xs.max() + 1) / SS
    by0, by1 = ys.min() / SS, (ys.max() + 1) / SS
    # potracer traces the bitmap's white pixels; its y axis already points down
    path = bmp.trace(turdsize=8, alphamax=1.0, opttolerance=1.4)

    def m(x: float, y: float) -> tuple[float, float]:
        """1024-space -> 108 viewport, mark centred, longest side = TARGET dp."""
        px, py = x / SS, y / SS
        return ((px - (bx0 + bx1) / 2) * K + 54.0, (py - (by0 + by1) / 2) * K + 54.0)

    parts: list[str] = []
    for curve in path:
        sx, sy = m(curve.start_point.x, curve.start_point.y)
        parts.append(f"M{sx:.2f},{sy:.2f}")
        for seg in curve:
            if seg.is_corner:
                c = seg.c
                ex, ey = m(seg.end_point.x, seg.end_point.y)
                cx1, cy1 = m(c.x, c.y)
                parts.append(f"L{cx1:.2f},{cy1:.2f}L{ex:.2f},{ey:.2f}")
            else:
                c1, c2 = seg.c1, seg.c2
                ex, ey = m(seg.end_point.x, seg.end_point.y)
                x1, y1 = m(c1.x, c1.y)
                x2, y2 = m(c2.x, c2.y)
                parts.append(f"C{x1:.2f},{y1:.2f} {x2:.2f},{y2:.2f} {ex:.2f},{ey:.2f}")
        parts.append("Z")
    return "".join(parts), (bx0, by0, bx1, by1)


TARGET = 64.0   # dp: comfortably inside the 66dp adaptive safe circle


def main() -> None:
    BUILD.mkdir(parents=True, exist_ok=True)

    mark = draw_mark(SS)
    ys, xs = np.nonzero(np.array(mark)[:, :, 3] > 127)
    side = max(xs.max() - xs.min(), ys.max() - ys.min()) + 1
    global K
    K = TARGET / (side / SS)

    path, bbox = trace_path()
    (BUILD / "logo-path.txt").write_text(path + "\n", encoding="utf-8")

    # ---- raster deliverables -------------------------------------------------
    rounded_card(1024).save(ROOT / "brand" / "logo-card-1024.png")
    rounded_card(512).save(ROOT / "brand" / "logo-card-512.png")

    tight = mark.crop((int(bbox[0] * SS), int(bbox[1] * SS),
                       int(bbox[2] * SS), int(bbox[3] * SS)))
    side_px = max(tight.size)
    sq = Image.new("RGBA", (side_px, side_px), (0, 0, 0, 0))
    sq.alpha_composite(tight, ((side_px - tight.width) // 2, (side_px - tight.height) // 2))
    sq.resize((1024, 1024), Image.LANCZOS).save(ROOT / "brand" / "logo-mark-1024.png")

    mono = Image.new("RGBA", (C * SS, C * SS), (0, 0, 0, 0))
    mono.paste(INK, mask=mark.split()[3])
    mono = mono.crop((int(bbox[0] * SS), int(bbox[1] * SS),
                      int(bbox[2] * SS), int(bbox[3] * SS)))
    ms = max(mono.size)
    msq = Image.new("RGBA", (ms, ms), (0, 0, 0, 0))
    msq.alpha_composite(mono, ((ms - mono.width) // 2, (ms - mono.height) // 2))
    msq.resize((1024, 1024), Image.LANCZOS).save(ROOT / "brand" / "logo-mono-1024.png")

    # ---- android vectors -----------------------------------------------------
    fg = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- GarPulse mark: a letter G whose crossbar rises into a heartbeat spike.
     Coral on the paper background layer; sized to the adaptive-icon safe zone. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF5A33"
        android:fillType="evenOdd"
        android:pathData="{path}" />
</vector>
'''
    mono_xml = fg.replace('android:fillColor="#FF5A33"', 'android:fillColor="#000000"')
    (RES / "ic_launcher_foreground.xml").write_text(fg, encoding="utf-8")
    (RES / "ic_launcher_monochrome.xml").write_text(mono_xml, encoding="utf-8")

    print("bbox(1024):", [round(v, 1) for v in bbox], "scale K=", round(K, 4))
    print("path chars:", len(path), "segments:", path.count("M"))


if __name__ == "__main__":
    main()
