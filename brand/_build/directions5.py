"""Five mutually distinct logo directions for GarPulse. Local geometry only, no generated images.

Each direction uses a DIFFERENT subject drawn from the brand's own semantics:
  A heart silhouette / B open data ribbon / C smartwatch object / D hand-drawn stopwatch / E geometric negative space
All palettes are bright, single-hue-dominant, cream/white negative space, zero gradients or shadows.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SS = 3
N = 1024
OUT = Path("brand/directions5")
OUT.mkdir(parents=True, exist_ok=True)


def sc(v):
    return int(round(v * SS))


def canvas(bg):
    im = Image.new("RGBA", (N * SS, N * SS), bg)
    return im, ImageDraw.Draw(im)


def save(im, name):
    small = im.resize((N, N), Image.LANCZOS)
    small.save(OUT / f"{name}-1024.png")
    small.resize((512, 512), Image.LANCZOS).save(OUT / f"{name}-512.png")


def thick_line(d, pts, width, color):
    d.line([(sc(x), sc(y)) for x, y in pts], fill=color, width=sc(width), joint="curve")
    r = sc(width / 2)
    for x, y in (pts[0], pts[-1]):
        d.ellipse([sc(x) - r, sc(y) - r, sc(x) + r, sc(y) + r], fill=color)


# ---------------------------------------------------------------------------
# A · Heart pulse — symbol mark. A heart whose centre is cut by one ECG trace.
# ---------------------------------------------------------------------------
def direction_a():
    im, d = canvas((255, 242, 239, 255))  # light coral paper
    pink = (255, 61, 139, 255)
    pts = []
    for i in range(360):
        t = i / 360 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((sc(512 + x * 22), sc(535 - y * 22)))
    d.polygon(pts, fill=pink)
    thick_line(d, [
        (235, 520), (335, 520), (385, 578), (455, 405),
        (530, 622), (600, 500), (665, 520), (790, 520),
    ], 40, (255, 242, 239, 255))
    return im


# ---------------------------------------------------------------------------
# B · Data ribbon — abstract mark. One open line: pulse becomes an up-arrow.
# ---------------------------------------------------------------------------
def direction_b():
    im, d = canvas((234, 251, 251, 255))  # pale cyan paper
    cyan = (18, 184, 212, 255)
    thick_line(d, [
        (135, 610), (300, 610), (365, 700), (455, 320),
        (560, 740), (650, 540), (790, 540),
    ], 104, cyan)
    # A single clean triangle at the end of the wave.
    d.polygon([(sc(765), sc(445)), (sc(910), sc(540)), (sc(765), sc(635))], fill=cyan)
    return im


# ---------------------------------------------------------------------------
# C · Smartwatch — pictographic mark. Wearable object with a pulse on screen.
# ---------------------------------------------------------------------------
def direction_c():
    im, d = canvas((234, 250, 240, 255))  # pale mint paper
    green = (30, 210, 90, 255)
    cream = (255, 248, 228, 255)
    d.rounded_rectangle([sc(430), sc(78), sc(594), sc(310)], radius=sc(34), fill=green)
    d.rounded_rectangle([sc(430), sc(714), sc(594), sc(946)], radius=sc(34), fill=green)
    d.rounded_rectangle([sc(250), sc(250), sc(774), sc(774)], radius=sc(150), fill=green)
    d.rounded_rectangle([sc(322), sc(322), sc(702), sc(702)], radius=sc(96), fill=cream)
    thick_line(d, [
        (365, 512), (420, 512), (448, 552), (480, 430),
        (520, 590), (555, 500), (660, 500),
    ], 34, green)
    return im


# ---------------------------------------------------------------------------
# D · Hand-drawn stopwatch — style card 09 technique, fresh subject.
# ---------------------------------------------------------------------------
def direction_d():
    im, d = canvas((255, 248, 228, 255))  # cream paper
    yellow = (255, 214, 32, 255)
    ink = (31, 58, 110, 255)  # <=8%: structure lines only
    coral = (255, 102, 74, 255)
    lime = (182, 232, 33, 255)

    def jitter_circle(cx, cy, r, n=260):
        pts = []
        for i in range(n):
            t = i / n * 2 * math.pi
            j = 3.0 * math.sin(5 * t + 0.7) + 1.8 * math.sin(11 * t + 1.9)
            rr = r + j
            pts.append((sc(cx + rr * math.cos(t)), sc(cy + rr * math.sin(t))))
        return pts

    # crown + side button, both touching the body so they read as one stopwatch
    d.rounded_rectangle([sc(480), sc(180), sc(544), sc(292)], radius=sc(16), fill=yellow, outline=ink, width=sc(9))
    d.rounded_rectangle([sc(724), sc(404), sc(806), sc(470)], radius=sc(14), fill=yellow, outline=ink, width=sc(9))
    body = jitter_circle(512, 536, 262)
    d.polygon(body, fill=yellow)
    d.line(body + [body[0]], fill=ink, width=sc(11), joint="curve")
    inner = jitter_circle(512, 536, 205)
    d.line(inner + [inner[0]], fill=ink, width=sc(6), joint="curve")
    thick_line(d, [
        (382, 540), (430, 540), (455, 578), (492, 440),
        (542, 626), (582, 520), (652, 520),
    ], 15, ink)
    # small hand-drawn accents OUTSIDE the subject
    d.ellipse([sc(130), sc(140), sc(210), sc(220)], fill=coral)
    for k in range(10):
        a = k / 10 * 2 * math.pi
        d.line([(sc(170 + 48 * math.cos(a)), sc(180 + 48 * math.sin(a))),
                (sc(170 + 66 * math.cos(a)), sc(180 + 66 * math.sin(a)))],
               fill=coral, width=sc(6))
    sx, sy, sr = 860, 210, 34
    d.polygon([(sc(sx), sc(sy - sr)), (sc(sx + sr * .3), sc(sy - sr * .3)),
               (sc(sx + sr), sc(sy)), (sc(sx + sr * .3), sc(sy + sr * .3)),
               (sc(sx), sc(sy + sr)), (sc(sx - sr * .3), sc(sy + sr * .3)),
               (sc(sx - sr), sc(sy)), (sc(sx - sr * .3), sc(sy - sr * .3))], fill=lime)
    return im


# ---------------------------------------------------------------------------
# E · Diamond pulse — style card 07 technique with a fresh mother shape.
# ---------------------------------------------------------------------------
def direction_e():
    im, d = canvas((255, 244, 230, 255))  # warm cream paper
    orange = (255, 155, 61, 255)
    paper = (255, 244, 230, 255)
    d.polygon([(sc(512), sc(140)), (sc(884), sc(512)), (sc(512), sc(884)), (sc(140), sc(512))], fill=orange)
    # Horizontal ECG slit reads unmistakably as a pulse; all points stay inside the diamond.
    thick_line(d, [
        (255, 512), (350, 512), (392, 558), (452, 372),
        (540, 652), (602, 488), (660, 512), (770, 512),
    ], 44, paper)
    return im


# ---------------------------------------------------------------------------
# Comparison sheet — five directions side by side, light background throughout.
# ---------------------------------------------------------------------------
def sheet():
    cards = [
        ("A", "心跳印章", "心形 · 玫红", "health pulse / heart", "代价：健康类通用感强"),
        ("B", "上升波形", "开放折线 · 青", "wave becomes trend", "代价：无闭合外轮廓"),
        ("C", "腕上脉冲", "手表 · 绿", "wearable data", "代价：意象偏硬件"),
        ("D", "手绘秒表", "手账 · 黄", "hand-drawn, human", "代价：32px 细节会糊"),
        ("E", "菱形脉冲", "负形 · 橙", "geometry, 24px safe", "代价：语义靠解释"),
    ]
    cw, gap, top = 360, 26, 34
    w = 72 + len(cards) * cw + (len(cards) - 1) * gap + 72
    h = 640
    im = Image.new("RGB", (w, h), (250, 246, 240))
    d = ImageDraw.Draw(im)
    fp = "C:/Windows/Fonts/segoeui.ttf"
    fc = "C:/Windows/Fonts/msyh.ttc"  # Segoe UI has no Chinese glyphs
    f_title = ImageFont.truetype(fc, 28)
    f_sub = ImageFont.truetype(fc, 21)
    f_small = ImageFont.truetype(fp, 18)
    f_cost = ImageFont.truetype(fc, 18)
    for i, (letter, title, color, tag, cost) in enumerate(cards):
        x = 72 + i * (cw + gap)
        d.rounded_rectangle([x, top, x + cw, top + 530], radius=22, fill=(255, 255, 255),
                            outline=(228, 220, 210), width=2)
        icon = Image.open(OUT / f"direction-{letter}-512.png").resize((292, 292), Image.LANCZOS)
        im.paste(icon.convert("RGB"), (x + (cw - 292) // 2, top + 22))
        d.text((x + 20, top + 336), f"{letter} · {title}", font=f_title, fill=(35, 28, 24))
        d.text((x + 20, top + 382), color, font=f_sub, fill=(120, 104, 94))
        d.line([(x + 20, top + 424), (x + cw - 20, top + 424)], fill=(236, 230, 224), width=2)
        d.text((x + 20, top + 440), tag, font=f_small, fill=(120, 104, 94))
        d.text((x + 20, top + 474), cost, font=f_cost, fill=(150, 136, 126))
    d.text((72, top + 550), "GarPulse — five distinct directions / bright palettes / local geometry",
           font=f_sub, fill=(80, 68, 60))
    im.save("brand/directions5-compare.png")


if __name__ == "__main__":
    for name, fn in (("A", direction_a), ("B", direction_b), ("C", direction_c),
                     ("D", direction_d), ("E", direction_e)):
        save(fn(), f"direction-{name}")
    sheet()
    print("five directions rendered")
