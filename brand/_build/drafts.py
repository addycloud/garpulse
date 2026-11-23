"""Direction drafts v2 for the GarPulse logo. Hand-built geometry, no generative images.

  python brand/_build/drafts.py
"""
from __future__ import annotations

import colorsys
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "_build"
C = 1024
S = 3

# bright, single-hue brand colours (all L >= 55 or vivid mid-tone with S >= 80)
TEAL = "#14C9A6"
CORAL = "#FF5A33"
LIME = "#CBF23C"
PAPER = "#FFF6EF"


def canvas(bg=None):
    im = Image.new("RGBA", (C * S, C * S), bg or (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def rr(d, box, radius, fill):
    x0, y0, x1, y1 = box
    d.rounded_rectangle([x0 * S, y0 * S, x1 * S, y1 * S], radius=radius * S, fill=fill)


def circle(d, cx, cy, r, fill):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill)


def stroke(d, pts, width, color):
    w = width * S
    xy = [(x * S, y * S) for x, y in pts]
    d.line(xy, fill=color, width=w, joint="curve")
    for x, y in (xy[0], xy[-1]):
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=color)


def hsl(hex_color: str):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    hh, ll, ss = colorsys.rgb_to_hls(r, g, b)
    return round(hh * 360), round(ss * 100), round(ll * 100)


# ---------------------------------------------------------------- A  pulse card
# Bright teal rounded square; one white negative-space line that starts as a heartbeat and
# finishes as an ascending staircase — pulse turning into data rows.
def direction_a() -> Image.Image:
    im, d = canvas()
    dy = -74
    rr(d, (124, 124, 900, 900), 176, TEAL)
    trace = [
        (196, 616 + dy), (330, 616 + dy), (392, 470 + dy), (462, 744 + dy), (532, 616 + dy),
        (664, 616 + dy), (710, 616 + dy), (710, 546 + dy), (756, 546 + dy), (756, 476 + dy),
        (812, 476 + dy),
    ]
    stroke(d, trace, 52, "#FFFFFF")
    return im


# ---------------------------------------------------------------- B  letter G
# Coral G on a light card: the crossbar's right end rises into a heartbeat spike, and the
# spike sits exactly in the letter's aperture.
def direction_b() -> Image.Image:
    im, d = canvas()
    rr(d, (124, 124, 900, 900), 176, PAPER)
    cx, cy = 512, 512
    ring_r, thick = 292, 118

    # draw the mark on its own layer so punching the counter never spills onto the canvas
    mark = Image.new("RGBA", (C * S, C * S), (0, 0, 0, 0))
    md = ImageDraw.Draw(mark)
    circle(md, cx, cy, ring_r, CORAL)
    # clean straight aperture above the arm (Helvetica-style G mouth), then the counter
    md.rectangle([(cx + 40) * S, (cy - 360) * S, (cx + 420) * S, (cy - 34) * S], fill=(0, 0, 0, 0))
    circle(md, cx, cy, ring_r - thick, (0, 0, 0, 0))
    # the arm is the heartbeat: one stroke from the aperture out into the ring
    arm = [(cx + 2, cy), (cx + 84, cy), (cx + 130, cy - 110),
           (cx + 176, cy), (cx + ring_r - 34, cy)]
    stroke(md, arm, 82, CORAL)
    im.alpha_composite(mark)
    return im


# ---------------------------------------------------------------- C  hand-drawn card
# Tilted lime card with a coral marker heartbeat; bright stationery energy.
def direction_c() -> Image.Image:
    im, d = canvas()
    card = Image.new("RGBA", (C * S, C * S), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    rr(cd, (188, 196, 836, 844), 136, LIME)
    squiggle = [
        (250, 574), (334, 574), (380, 468), (436, 690), (492, 574),
        (600, 574), (646, 524), (704, 574), (782, 574),
    ]
    stroke(cd, squiggle, 46, CORAL)
    card = card.rotate(-7, resample=Image.BICUBIC, center=(C * S / 2, C * S / 2))
    im.alpha_composite(card)
    return im


def main() -> None:
    BUILD.mkdir(parents=True, exist_ok=True)
    for name, fn in (("A", direction_a), ("B", direction_b), ("C", direction_c)):
        fn().resize((1024, 1024), Image.LANCZOS).save(BUILD / f"draft-{name}-1024.png")
    for label, color in (("teal", TEAL), ("coral", CORAL), ("lime", LIME)):
        print(label, color, "HSL", hsl(color))


if __name__ == "__main__":
    main()
