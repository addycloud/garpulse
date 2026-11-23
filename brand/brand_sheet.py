"""One-page spec sheet for the finalised GarPulse mark.

    python brand/brand_sheet.py   ->  brand/logo-sheet.png
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "brand"

CORAL = (0xFF, 0x5A, 0x33, 255)
PAPER = (0xFF, 0xF6, 0xEF, 255)
INK = (0x1A, 0x14, 0x11, 255)
W, H = 1680, 1020


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for name in (("msyhbd.ttc" if bold else "msyh.ttc"), "segoeui.ttf"):
        p = Path("C:/Windows/Fonts") / name
        if p.exists():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def main() -> None:
    sheet = Image.new("RGBA", (W, H), PEP := (0xFB, 0xF7, 0xF3, 255))
    d = ImageDraw.Draw(sheet)

    f_h1 = font(52, True)
    f_h2 = font(26, True)
    f_body = font(21)
    f_mono = font(20)

    d.text((72, 62), "GarPulse", font=f_h1, fill=INK)
    d.text((74, 130), "A letter G whose crossbar rises into a heartbeat.", font=f_body, fill=(120, 106, 98, 255))

    card = Image.open(BRAND / "logo-card-1024.png")
    mark = Image.open(BRAND / "logo-mark-1024.png")
    mono = Image.open(BRAND / "logo-mono-1024.png")

    # ---- four states -------------------------------------------------------
    tile, gap, top = 320, 34, 210
    x = 72

    def tinted(mark_img: Image.Image, color: tuple[int, int, int, int], bg: tuple[int, int, int, int],
               size: int) -> Image.Image:
        """The mark painted in `color` over `bg`, optically matched to the primary card."""
        inner = int(round(size * 0.74))          # same optical size as the card's inset mark
        src = mark_img.resize((inner, inner), Image.LANCZOS)
        layer = Image.new("RGBA", (size, size), bg)
        stamp = Image.new("RGBA", (inner, inner), (0, 0, 0, 0))
        stamp.paste(color, mask=src.split()[3])
        layer.alpha_composite(stamp, ((size - inner) // 2, (size - inner) // 2))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=26, fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(layer, (0, 0), mask)
        return out

    states: list[tuple[Image.Image, str, str]] = [
        (card.resize((tile, tile), Image.LANCZOS), "Primary", "coral on paper"),
        (tinted(mono, INK, PAPER, tile), "One colour", "ink on paper"),
        (tinted(mark, PAPER, CORAL, tile), "Inverse", "paper on coral"),
        (tinted(mark, CORAL, INK, tile), "Dark UI", "coral on ink"),
    ]

    for img, title, sub in states:
        sheet.alpha_composite(img, (x, top))
        d.rounded_rectangle([x, top, x + tile, top + tile], radius=26,
                            outline=(232, 224, 216, 255), width=2)
        d.text((x + 4, top + tile + 18), title, font=f_h2, fill=INK)
        d.text((x + 4, top + tile + 54), sub, font=f_body, fill=(120, 106, 98, 255))
        x += tile + gap

    # ---- legibility strip --------------------------------------------------
    y = 700
    d.text((72, y), "Legibility", font=f_h2, fill=INK)
    sizes = [128, 96, 72, 56, 40, 32]
    base = y + 52 + 128          # common baseline for every thumbnail
    x = 72
    for s in sizes:
        thumb = card.resize((s, s), Image.LANCZOS)
        sheet.alpha_composite(thumb, (x, base - s))
        d.text((x, base + 12), f"{s}px", font=f_mono, fill=(120, 106, 98, 255))
        x += s + 34

    # ---- palette -----------------------------------------------------------
    px, py = 850, 700
    d.text((px, py), "Palette", font=f_h2, fill=INK)
    chips = [("Coral", "#FF5A33", CORAL), ("Paper", "#FFF6EF", PAPER), ("Ink", "#1A1411", INK)]
    for i, (name, hexv, rgb) in enumerate(chips):
        cx = px + i * 250
        d.rounded_rectangle([cx, py + 52, cx + 210, py + 168], radius=18, fill=rgb,
                            outline=(228, 216, 206, 255), width=2)
        d.text((cx, py + 182), name, font=f_h2, fill=INK)
        d.text((cx, py + 216), hexv, font=f_mono, fill=(120, 106, 98, 255))

    sheet.convert("RGB").save(BRAND / "logo-sheet.png")
    print("wrote", BRAND / "logo-sheet.png")


if __name__ == "__main__":
    main()
