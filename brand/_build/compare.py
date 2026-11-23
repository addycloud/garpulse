"""Builds the direction-comparison sheet + the 512px drafts for the recommendation round."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "_build"

CN = "C:/Windows/Fonts/msyh.ttc"
CN_B = "C:/Windows/Fonts/msyhbd.ttc"

INK = (31, 41, 55)
MUTED = (107, 114, 128)
SHEET = (245, 247, 249)
PANEL = (255, 255, 255)
ACCENT = (17, 198, 166)

ITEMS = [
    ("A", "脉搏卡片", "draft-A-1024.png",
     "青绿圆角方 + 白色负形：一次心跳，向右走成上升台阶（数据条）",
     "最像 App 图标，32px 最稳；代价是「负形 + 台阶」细节多，放大后才看得全",
     True),
    ("B", "字母 G", "draft-B-1024.png",
     "珊瑚色 G，横臂本身就是心跳线——名字 Gar + Pulse 一次说完",
     "品牌名认得最快；代价是字母标在极小尺寸靠字形不靠图形，字标气质要另配",
     False),
    ("C", "手账卡片", "draft-C-1024.png",
     "柠檬绿手绘卡片微倾 + 珊瑚马克笔心跳，亮、软、有手写感",
     "亲和力最强；代价是手绘质感不适合严肃数据场景，切图与四态需重绘描边",
     False),
]


def font(path, size):
    return ImageFont.truetype(path, size)


def main() -> None:
    card_w, gap, pad = 520, 34, 46
    w = pad * 2 + card_w * 3 + gap * 2
    h = 812
    sheet = Image.new("RGB", (w, h), SHEET)
    d = ImageDraw.Draw(sheet)

    f_title = font(CN_B, 30)
    f_tag = font(CN_B, 22)
    f_body = font(CN, 19)
    f_small = font(CN, 17)

    d.text((pad, 34), "GarPulse 图标方向 · 三选一", font=font(CN_B, 36), fill=INK)
    d.text((pad, 86), "全部为本地几何绘制，无生成图；三条方向在构成、主体、色彩、质感上互相拉开。",
           font=f_body, fill=MUTED)

    for i, (key, name, file, idea, cost, star) in enumerate(ITEMS):
        x = pad + i * (card_w + gap)
        y = 140
        d.rounded_rectangle([x, y, x + card_w, y + 620], radius=26, fill=PANEL)
        icon = Image.open(BUILD / file).convert("RGBA")
        icon = icon.resize((344, 344), Image.LANCZOS)
        sheet.paste(icon, (x + (card_w - 344) // 2, y + 30), icon)

        ty = y + 396
        d.text((x + 30, ty), f"{key}", font=font(CN_B, 40), fill=ACCENT)
        d.text((x + 78, ty + 2), name, font=f_title, fill=INK)
        if star:
            badge = f"{key} · 主推"
            bw = d.textlength(badge, font=f_tag) + 26
            d.rounded_rectangle([x + card_w - bw - 26, ty + 4, x + card_w - 26, ty + 40],
                                radius=18, fill=ACCENT)
            d.text((x + card_w - bw - 13, ty + 10), badge, font=f_tag, fill=(255, 255, 255))

        # simple character wrap for CJK
        words, line, lines = list(idea), "", []
        for ch in words:
            if d.textlength(line + ch, font=f_body) > 448:
                lines.append(line)
                line = ch
            else:
                line += ch
        lines.append(line)
        d.multiline_text((x + 30, ty + 62), "\n".join(lines), font=f_body, fill=INK, spacing=9)

        cy = ty + 62 + 26 * len(lines) + 22
        d.line([x + 30, cy, x + card_w - 30, cy], fill=(226, 232, 236), width=2)
        cost_lines, line = [], ""
        for ch in cost:
            if d.textlength(line + ch, font=f_small) > 448:
                cost_lines.append(line)
                line = ch
            else:
                line += ch
        cost_lines.append(line)
        d.multiline_text((x + 30, cy + 18), "\n".join(cost_lines), font=f_small, fill=MUTED,
                         spacing=9)

    out = ROOT / "directions-compare.png"
    sheet.save(out, optimize=True)
    for key, _, file, *_ in ITEMS:
        im = Image.open(BUILD / file).convert("RGBA").resize((512, 512), Image.LANCZOS)
        im.save(ROOT / f"draft-{key}-512.png", optimize=True)
    print("wrote", out)


if __name__ == "__main__":
    main()
