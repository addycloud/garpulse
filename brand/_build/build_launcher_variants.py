"""Generate adaptive-icon foreground vectors for the five alternate GarPulse logos.

The geometry mirrors brand/_build/directions5.py; every mark is remapped into the
108dp adaptive-icon canvas, centred inside the 72dp safe zone. Run from repo root.
"""
from pathlib import Path
import math

RES = Path("app/src/main/res")
DRAW = RES / "drawable"
MIP = RES / "mipmap-anydpi-v26"
DRAW.mkdir(parents=True, exist_ok=True)
MIP.mkdir(parents=True, exist_ok=True)

BOX = 68.0  # fits inside the 72dp adaptive safe zone
CENTER = 54.0
BBOX = {
    "A": (158, 270, 866, 910),
    "B": (80, 266, 912, 794),
    "C": (248, 76, 776, 948),
    "D": (102, 114, 896, 808),
    "E": (138, 138, 886, 886),
}
BG = {
    "A": "#FFF2EF",
    "B": "#EAFBFB",
    "C": "#EAFAF0",
    "D": "#FFF8E4",
    "E": "#FFF4E6",
}
NAME = {
    "A": "heart",
    "B": "ribbon",
    "C": "watch",
    "D": "stopwatch",
    "E": "diamond",
}


def tx(letter, x, y):
    x0, y0, x1, y1 = BBOX[letter]
    s = BOX / max(x1 - x0, y1 - y0)
    ox = CENTER - s * (x0 + x1) / 2
    oy = CENTER - s * (y0 + y1) / 2
    return ox + s * x, oy + s * y


def scale(letter, v):
    x0, y0, x1, y1 = BBOX[letter]
    return v * BOX / max(x1 - x0, y1 - y0)


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pt(letter, x, y):
    a, b = tx(letter, x, y)
    return f"{f(a)},{f(b)}"


def path(d, color, fill=True, width=None, caps=False, stroke=None):
    out = ['    <path', f'        android:pathData="{d}"']
    if fill:
        out.append(f'        android:fillColor="{color}"')
    else:
        out.append('        android:fillColor="#00000000"')
    if width is not None:
        out += [
            f'        android:strokeColor="{stroke or color}"',
            f'        android:strokeWidth="{f(width)}"',
        ]
        if caps:
            out += [
                '        android:strokeLineCap="round"',
                '        android:strokeLineJoin="round"',
            ]
    out.append('    />')
    return "\n".join(out)


def poly(letter, points, close=True):
    d = "M" + " L".join(pt(letter, x, y) for x, y in points)
    return d + (" Z" if close else "")


def rr(letter, x1, y1, x2, y2, r):
    """Rounded rectangle as a single closed path."""
    a, b = tx(letter, x1 + r, y1)
    c, d = tx(letter, x2 - r, y1)
    e, g = tx(letter, x2, y1 + r)
    h, i = tx(letter, x2, y2 - r)
    j, k = tx(letter, x2 - r, y2)
    l, m = tx(letter, x1 + r, y2)
    n, o = tx(letter, x1, y2 - r)
    p, q = tx(letter, x1, y1 + r)
    R = f(scale(letter, r))
    return (
        f"M{f(a)},{f(b)} L{f(c)},{f(d)} A{R},{R} 0 0 1 {f(e)},{f(g)} "
        f"L{f(h)},{f(i)} A{R},{R} 0 0 1 {f(j)},{f(k)} "
        f"L{f(l)},{f(m)} A{R},{R} 0 0 1 {f(n)},{f(o)} "
        f"L{f(p)},{f(q)} A{R},{R} 0 0 1 {f(a)},{f(b)} Z"
    )


def circle(letter, cx, cy, r):
    x, y = tx(letter, cx, cy)
    R = scale(letter, r)
    rs = f(R)
    return (f"M{f(x - R)},{f(y)} A{rs},{rs} 0 1 0 {f(x + R)},{f(y)} "
            f"A{rs},{rs} 0 1 0 {f(x - R)},{f(y)} Z")


def heart():
    pts = []
    for i in range(140):
        t = i / 140 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((512 + x * 22, 535 - y * 22))
    parts = [
        path(poly("A", pts), "#FF3D8B"),
        path(
            poly("A", [(235, 520), (335, 520), (385, 578), (455, 405),
                       (530, 622), (600, 500), (665, 520), (790, 520)], close=False),
            "#FFF2EF", fill=False, width=scale("A", 40), caps=True,
        ),
    ]
    return parts


def ribbon():
    return [
        path(
            poly("B", [(135, 610), (300, 610), (365, 700), (455, 320),
                       (560, 740), (650, 540), (790, 540)], close=False),
            "#12B8D8", fill=False, width=scale("B", 104), caps=True,
        ),
        path(poly("B", [(765, 445), (910, 540), (765, 635)]), "#12B8D8"),
    ]


def watch():
    return [
        path(rr("C", 430, 78, 594, 310, 34), "#1ED25A"),
        path(rr("C", 430, 714, 594, 946, 34), "#1ED25A"),
        path(rr("C", 250, 250, 774, 774, 150), "#1ED25A"),
        path(rr("C", 322, 322, 702, 702, 96), "#FFF8E4"),
        path(
            poly("C", [(365, 512), (420, 512), (448, 552), (480, 430),
                       (520, 590), (555, 500), (660, 500)], close=False),
            "#1ED25A", fill=False, width=scale("C", 34), caps=True,
        ),
    ]


def stopwatch():
    return [
        path(rr("D", 480, 180, 544, 292, 16), "#FFD620", width=scale("D", 9), stroke="#1F3A6E"),
        path(rr("D", 724, 404, 806, 470, 14), "#FFD620", width=scale("D", 9), stroke="#1F3A6E"),
        path(circle("D", 512, 536, 262), "#FFD620", width=scale("D", 11), stroke="#1F3A6E"),
        path(circle("D", 512, 536, 205), "#1F3A6E", fill=False, width=scale("D", 6)),
        path(
            poly("D", [(382, 540), (430, 540), (455, 578), (492, 440),
                       (542, 626), (582, 520), (652, 520)], close=False),
            "#1F3A6E", fill=False, width=scale("D", 15), caps=True,
        ),
        path(circle("D", 170, 180, 40), "#FF664A"),
        path(poly("D", [(860, 176), (870, 196), (894, 210), (870, 224),
                        (860, 244), (850, 224), (826, 210), (850, 196)]), "#B6E821"),
    ]


def diamond():
    return [
        path(poly("E", [(512, 140), (884, 512), (512, 884), (140, 512)]), "#FF9B3D"),
        path(
            poly("E", [(255, 512), (350, 512), (392, 558), (452, 372),
                       (540, 652), (602, 488), (660, 512), (770, 512)], close=False),
            "#FFF4E6", fill=False, width=scale("E", 44), caps=True,
        ),
    ]


XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- GarPulse alternate launcher mark: {name}. Generated by brand/_build/build_launcher_variants.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{paths}
</vector>
"""

ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<!-- GarPulse alternate launcher icon: {name}. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background_{name}" />
    <foreground android:drawable="@drawable/ic_launcher_{name}_foreground" />
</adaptive-icon>
"""


def main():
    for letter, fn in (("A", heart), ("B", ribbon), ("C", watch),
                       ("D", stopwatch), ("E", diamond)):
        name = NAME[letter]
        (DRAW / f"ic_launcher_{name}_foreground.xml").write_text(
            XML.format(name=name, paths="\n".join(fn())), encoding="utf-8"
        )
        for suffix in ("", "_round"):
            (MIP / f"ic_launcher_{name}{suffix}.xml").write_text(
                ADAPTIVE.format(name=name), encoding="utf-8"
            )
        print("generated", name)


if __name__ == "__main__":
    main()
