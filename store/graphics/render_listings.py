#!/usr/bin/env python3
"""딩그로(Dingrow) 스토어 그래픽. Robolectric 캡처와 일러스트, Pretendard로 만든다."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "composeApp/src/commonMain/composeResources/drawable"
FONT_PATH = ROOT / "composeApp/src/commonMain/composeResources/font/pretendard_medium.otf"
HIRES = Path("/tmp/mongle-hires")
OUT = Path(__file__).resolve().parent
ARTIFACTS = Path("/opt/cursor/artifacts")

CREAM = (255, 246, 238)
INK = (92, 70, 54)
INK_SOFT = (138, 106, 85)
PEACH = (255, 227, 214)
PEACH_DEEP = (233, 139, 132)
MINT = (221, 243, 234)
MINT_DEEP = (143, 203, 182)
BUTTER = (255, 243, 201)
WHITE = (255, 252, 248)
FRAME = (255, 252, 248)

SPECIES = [
    "cat", "rabbit", "bear", "frog", "chick", "mandu", "strawberry", "greentea", "peach", "cloud",
    "star", "ghost", "sprout", "penguin", "hamster", "fox", "seal", "panda", "babydragon", "egg",
    "milk", "injeolmi", "lion", "chestnut", "tiger", "squirrel", "duck", "sheep", "whale", "octopus",
    "mushroom", "sunflower", "lemon", "pudding", "macaron", "donut", "sweetpotato", "snowman", "rainbow", "moon",
    "owl", "koala", "otter", "hen", "pig", "puppy", "shark", "butter", "palette", "barley",
]


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_PATH), size)


def lerp(a, b, t: float):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gradient(w: int, h: int, top, bottom) -> Image.Image:
    column = Image.new("RGB", (1, h))
    px = column.load()
    for y in range(h):
        px[0, y] = lerp(top, bottom, y / max(h - 1, 1))
    return column.resize((w, h), Image.Resampling.BILINEAR).convert("RGBA")


def rounded_mask(size, radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def blob(canvas: Image.Image, cx: int, cy: int, rx: int, ry: int, color, alpha: int) -> None:
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=(*color, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(radius=max(rx, ry) * 0.08))
    canvas.alpha_composite(layer)


def load_art(name: str) -> Image.Image:
    path = ART / f"{name}.webp"
    if not path.exists():
        path = ART / f"{name}.png"
    return Image.open(path).convert("RGBA")


def fit(image: Image.Image, w: int, h: int) -> Image.Image:
    scale = min(w / image.width, h / image.height)
    size = (max(1, int(image.width * scale)), max(1, int(image.height * scale)))
    return image.resize(size, Image.Resampling.LANCZOS)


def contain_paste(canvas: Image.Image, image: Image.Image, box) -> None:
    x, y, w, h = box
    sprite = fit(image, w, h)
    ox = x + (w - sprite.width) // 2
    oy = y + (h - sprite.height) // 2
    canvas.paste(sprite, (ox, oy), sprite)


def text_width(text: str, face) -> int:
    box = face.getbbox(text)
    return box[2] - box[0]


def fit_font(text: str, max_width: int, start: int, minimum: int = 18):
    size = start
    face = font(size)
    while size > minimum and text_width(text, face) > max_width:
        size -= 2
        face = font(size)
    return face


def draw_text(draw: ImageDraw.ImageDraw, text: str, x: int, y: int, face, fill) -> int:
    box = face.getbbox(text)
    draw.text((x, y - box[1]), text, font=face, fill=fill)
    return box[3] - box[1]


def draw_center(draw, text: str, y: int, face, fill, width: int) -> int:
    tw = text_width(text, face)
    return draw_text(draw, text, (width - tw) // 2, y, face, fill)


def shadow_of(size, radius: int, blur: int = 22, alpha: int = 48) -> Image.Image:
    pad = blur * 3
    sheet = Image.new("RGBA", (size[0] + pad * 2, size[1] + pad * 2), (0, 0, 0, 0))
    rect = Image.new("RGBA", size, (*INK, alpha))
    rect.putalpha(rounded_mask(size, radius))
    sheet.paste(rect, (pad, pad), rect)
    return sheet.filter(ImageFilter.GaussianBlur(blur)), pad


def background(w: int, h: int) -> Image.Image:
    canvas = gradient(w, h, (255, 248, 242), CREAM)
    blob(canvas, int(w * 0.08), int(h * 0.12), int(w * 0.28), int(w * 0.22), PEACH, 170)
    blob(canvas, int(w * 0.95), int(h * 0.2), int(w * 0.22), int(w * 0.18), BUTTER, 150)
    blob(canvas, int(w * 0.1), int(h * 0.92), int(w * 0.3), int(w * 0.16), MINT, 140)
    return canvas


def room_card(w: int, h: int, pet_scale: float = 0.4) -> Image.Image:
    room = load_art("room_wood_day_3x4")
    pet = load_art("base_happy")
    fitted = fit(room, w, h)
    canvas = Image.new("RGBA", (w, h), (*CREAM, 255))
    ox = (w - fitted.width) // 2
    oy = (h - fitted.height) // 2
    canvas.paste(fitted, (ox, oy))
    pw = max(1, int(fitted.width * pet_scale))
    pet = fit(pet, pw, pw)
    px = ox + (fitted.width - pet.width) // 2
    py = oy + fitted.height - pet.height - int(fitted.height * 0.045)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse(
        (px + pet.width * 0.18, py + pet.height * 0.78, px + pet.width * 0.82, py + pet.height * 0.92),
        fill=(92, 70, 54, 50),
    )
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(8)))
    canvas.paste(pet, (px, py), pet)
    return canvas


def feature() -> Image.Image:
    w, h = 1024, 500
    canvas = gradient(w, h, (255, 244, 236), (255, 248, 242))
    blob(canvas, 80, 40, 220, 140, PEACH, 180)
    blob(canvas, 180, 460, 260, 120, MINT, 150)
    blob(canvas, 980, 80, 180, 140, BUTTER, 140)

    card_w, card_h = 360, 432
    card = room_card(card_w - 28, card_h - 28, pet_scale=0.42)
    framed = Image.new("RGBA", (card_w, card_h), (*WHITE, 255))
    framed.paste(card, (14, 14), card)
    mask = rounded_mask((card_w, card_h), 36)
    framed.putalpha(mask)
    shade, pad = shadow_of((card_w, card_h), 36, blur=18, alpha=42)
    card_x, card_y = 624, (h - card_h) // 2
    canvas.alpha_composite(shade, (card_x - pad, card_y - pad + 8))
    canvas.alpha_composite(framed, (card_x, card_y))

    icon = fit(Image.open(ROOT / "store/icon-1024.png").convert("RGBA"), 72, 72)
    icon_mask = rounded_mask(icon.size, 20)
    icon.putalpha(icon_mask)
    canvas.paste(icon, (64, 56), icon)
    draw = ImageDraw.Draw(canvas)
    draw_text(draw, "딩그로", 152, 58, font(40), INK)
    draw_text(draw, "Dingrow", 152, 108, font(24), INK_SOFT)

    title = fit_font("할 일을 끝내면", 520, 64)
    draw_text(draw, "할 일을 끝내면", 64, 188, title, INK)
    second = fit_font("함께 자라요", 520, 64)
    draw_text(draw, "함께 자라요", 64, 268, second, INK)
    sub = font(26)
    draw_text(draw, "일정 알림 · 방 꾸미기 · 친구 도감", 64, 368, sub, INK_SOFT)
    return canvas.convert("RGB")


def shot(name: str) -> Image.Image:
    path = HIRES / name
    if not path.exists():
        path = ARTIFACTS / name
    image = Image.open(path).convert("RGBA")
    base = Image.new("RGBA", image.size, (*CREAM, 255))
    base.alpha_composite(image)
    return base


def place_phone(canvas: Image.Image, screen: Image.Image, top: int, max_w: int, max_h: int) -> tuple[int, int, int, int]:
    aspect = screen.width / screen.height
    bezel_ratio = 0.03
    # outer = inner + 2*bezel, bezel = bezel_ratio * inner_w, inner_h = inner_w / aspect
    # outer_w = inner_w * (1 + 2*bezel_ratio)
    # outer_h = inner_w / aspect + 2 * bezel_ratio * inner_w
    inner_w = min(max_w / (1 + 2 * bezel_ratio), max_h / (1 / aspect + 2 * bezel_ratio))
    inner_w = int(inner_w)
    inner_h = int(inner_w / aspect)
    bezel = max(14, int(inner_w * bezel_ratio))
    outer = (inner_w + bezel * 2, inner_h + bezel * 2)
    radius = int(inner_w * 0.075)
    shade, pad = shadow_of(outer, radius + bezel, blur=26, alpha=46)
    x = (canvas.width - outer[0]) // 2
    y = top + max(0, (max_h - outer[1]) // 2)
    canvas.alpha_composite(shade, (x - pad, y - pad + 10))

    frame = Image.new("RGBA", outer, (*FRAME, 255))
    frame.putalpha(rounded_mask(outer, radius + bezel))
    hair = Image.new("RGBA", outer, (0, 0, 0, 0))
    ImageDraw.Draw(hair).rounded_rectangle(
        (bezel - 2, bezel - 2, outer[0] - bezel + 1, outer[1] - bezel + 1),
        radius=radius + 2,
        outline=(*PEACH, 255),
        width=3,
    )
    screen_im = screen.resize((inner_w, inner_h), Image.Resampling.LANCZOS)
    screen_im.putalpha(rounded_mask((inner_w, inner_h), radius))
    frame.paste(screen_im, (bezel, bezel), screen_im)
    frame.alpha_composite(hair)
    canvas.alpha_composite(frame, (x, y))
    return x, y, outer[0], outer[1]


def eyebrow(canvas: Image.Image, y: int) -> int:
    draw = ImageDraw.Draw(canvas)
    face = font(max(22, canvas.width // 38))
    under_face = font(max(16, canvas.width // 52))
    label = "딩그로"
    under = "Dingrow"
    gap = 10
    icon = fit(load_art("base_idle"), face.size + 8, face.size + 8)
    text_w = max(text_width(label, face), text_width(under, under_face))
    total = icon.width + gap + text_w
    x = (canvas.width - total) // 2
    canvas.paste(icon, (x, y), icon)
    text_x = x + icon.width + gap
    name_h = draw_text(draw, label, text_x, y, face, PEACH_DEEP)
    draw_text(draw, under, text_x, y + name_h + 2, under_face, INK_SOFT)
    block = max(icon.height, name_h + under_face.size + 6)
    return y + block + int(canvas.height * 0.012)


def title_block(canvas: Image.Image, title: str, subtitle: str, y: int) -> int:
    y = eyebrow(canvas, y)
    draw = ImageDraw.Draw(canvas)
    title_face = fit_font(title, int(canvas.width * 0.9), max(40, int(canvas.width * 0.068)))
    y += draw_center(draw, title, y, title_face, INK, canvas.width)
    y += int(canvas.height * 0.008)
    sub_face = fit_font(subtitle, int(canvas.width * 0.88), max(22, int(canvas.width * 0.032)))
    y += draw_center(draw, subtitle, y, sub_face, INK_SOFT, canvas.width)
    return y + int(canvas.height * 0.02)


def card_row(canvas: Image.Image, y: int, items: list[tuple[str, str, str]]) -> int:
    draw_width = int(canvas.width * 0.9)
    gap = int(canvas.width * 0.02)
    count = len(items)
    card_w = (draw_width - gap * (count - 1)) // count
    card_h = int(card_w * 0.72)
    x0 = (canvas.width - (card_w * count + gap * (count - 1))) // 2
    radius = max(18, card_w // 10)
    for index, (art_name, name, price) in enumerate(items):
        x = x0 + index * (card_w + gap)
        shade, pad = shadow_of((card_w, card_h), radius, blur=12, alpha=32)
        canvas.alpha_composite(shade, (x - pad, y - pad + 6))
        card = Image.new("RGBA", (card_w, card_h), (*WHITE, 255))
        card.putalpha(rounded_mask((card_w, card_h), radius))
        sprite = fit(load_art(art_name), int(card_w * 0.42), int(card_h * 0.55))
        card.paste(sprite, ((card_w - sprite.width) // 2, int(card_h * 0.06)), sprite)
        pen = ImageDraw.Draw(card)
        name_face = fit_font(name, card_w - 16, max(16, card_w // 9))
        price_face = fit_font(price, card_w - 16, max(14, card_w // 11))
        name_w = text_width(name, name_face)
        price_w = text_width(price, price_face)
        draw_text(pen, name, (card_w - name_w) // 2, int(card_h * 0.64), name_face, INK)
        draw_text(pen, price, (card_w - price_w) // 2, int(card_h * 0.80), price_face, INK_SOFT)
        canvas.alpha_composite(card, (x, y))
    return y + card_h


def alarm_card(canvas: Image.Image, y: int) -> int:
    card_w = int(canvas.width * 0.78)
    card_h = int(canvas.width * 0.2)
    x = (canvas.width - card_w) // 2
    radius = max(22, card_w // 18)
    shade, pad = shadow_of((card_w, card_h), radius, blur=16, alpha=40)
    canvas.alpha_composite(shade, (x - pad, y - pad + 8))
    card = Image.new("RGBA", (card_w, card_h), (*WHITE, 255))
    card.putalpha(rounded_mask((card_w, card_h), radius))
    icon = fit(Image.open(ROOT / "store/icon-1024.png").convert("RGBA"), int(card_h * 0.28), int(card_h * 0.28))
    icon.putalpha(rounded_mask(icon.size, icon.width // 4))
    card.paste(icon, (int(card_w * 0.04), int(card_h * 0.1)), icon)
    pen = ImageDraw.Draw(card)
    name_face = font(max(20, int(card_h * 0.16)))
    time_face = font(max(16, int(card_h * 0.12)))
    body_face = fit_font('알 속의 보리: "물 마시기" 할 시간이에요', int(card_w * 0.9), max(18, int(card_h * 0.13)))
    draw_text(pen, "보리", int(card_w * 0.04) + icon.width + 14, int(card_h * 0.12), name_face, INK)
    when = "지금"
    draw_text(pen, when, card_w - int(card_w * 0.05) - text_width(when, time_face), int(card_h * 0.14), time_face, INK_SOFT)
    draw_text(pen, '알 속의 보리: "물 마시기" 할 시간이에요', int(card_w * 0.05), int(card_h * 0.42), body_face, INK)
    button_w = int(card_w * 0.42)
    button_h = int(card_h * 0.24)
    button_y = int(card_h * 0.66)
    for label, color, bx in (("완료", PEACH, int(card_w * 0.05)), ("10분 뒤", MINT, int(card_w * 0.53))):
        ImageDraw.Draw(card).rounded_rectangle(
            (bx, button_y, bx + button_w, button_y + button_h),
            radius=button_h // 2,
            fill=(*color, 255),
        )
        face = fit_font(label, button_w - 12, max(16, int(button_h * 0.48)))
        tw = text_width(label, face)
        draw_text(ImageDraw.Draw(card), label, bx + (button_w - tw) // 2, button_y + int(button_h * 0.22), face, INK)
    canvas.alpha_composite(card, (x, y))
    return y + card_h


def dex_grid(canvas: Image.Image, y: int) -> int:
    cols = 10
    rows = 5
    margin = int(canvas.width * 0.04)
    gap = max(6, canvas.width // 140)
    cell = (canvas.width - margin * 2 - gap * (cols - 1)) // cols
    radius = max(12, cell // 6)
    for index, name in enumerate(SPECIES):
        col = index % cols
        row = index // cols
        x = margin + col * (cell + gap)
        cy = y + row * (cell + gap)
        card = Image.new("RGBA", (cell, cell), (*WHITE, 255))
        card.putalpha(rounded_mask((cell, cell), radius))
        sprite = fit(load_art(name), int(cell * 0.82), int(cell * 0.82))
        card.paste(sprite, ((cell - sprite.width) // 2, (cell - sprite.height) // 2), sprite)
        canvas.alpha_composite(card, (x, cy))
    return y + rows * cell + gap * (rows - 1)


def build_launcher() -> Image.Image:
    w, h = 1080, 2220
    canvas = Image.new("RGBA", (w, h), (*CREAM, 255))
    blob(canvas, 120, 160, 280, 180, PEACH, 120)
    blob(canvas, 980, 420, 220, 160, MINT, 90)
    draw = ImageDraw.Draw(canvas)
    clock = font(132)
    date = font(40)
    y = 120
    y += draw_center(draw, "9:41", y, clock, INK, w) + 8
    y += draw_center(draw, "10월 21일 수요일", y, date, INK_SOFT, w) + 36

    large = shot("widget-large.png")
    lw = w - 96
    lh = int(lw * large.height / large.width)
    large = large.resize((lw, lh), Image.Resampling.LANCZOS)
    large.putalpha(rounded_mask(large.size, 48))
    lx = (w - lw) // 2
    shade, pad = shadow_of(large.size, 48, blur=16, alpha=36)
    canvas.alpha_composite(shade, (lx - pad, y - pad + 8))
    canvas.alpha_composite(large, (lx, y))
    y += lh + 28

    small = shot("widget-small.png")
    sw = int((lw - 28) * 0.56)
    sh = int(sw * small.height / small.width)
    max_small = h - y - 48
    if sh > max_small:
        sh = max_small
        sw = int(sh * small.width / small.height)
    small = small.resize((sw, sh), Image.Resampling.LANCZOS)
    small.putalpha(rounded_mask(small.size, 40))
    shade, pad = shadow_of(small.size, 40, blur=14, alpha=32)
    canvas.alpha_composite(shade, (lx - pad, y - pad + 6))
    canvas.alpha_composite(small, (lx, y))

    icon_box = lw - sw - 28
    icon = fit(Image.open(ROOT / "store/icon-1024.png").convert("RGBA"), int(icon_box * 0.52), int(icon_box * 0.52))
    icon.putalpha(rounded_mask(icon.size, icon.width // 4))
    ix = lx + sw + 28 + (icon_box - icon.width) // 2
    label = font(32)
    under = font(22)
    name_h = 36
    under_h = 28
    block = icon.height + 8 + name_h + under_h
    iy = y + max(0, (sh - block) // 2)
    canvas.paste(icon, (ix, iy), icon)
    name_w = text_width("딩그로", label)
    under_w = text_width("Dingrow", under)
    draw_text(draw, "딩그로", ix + (icon.width - name_w) // 2, iy + icon.height + 8, label, INK)
    draw_text(draw, "Dingrow", ix + (icon.width - under_w) // 2, iy + icon.height + 8 + name_h, under, INK_SOFT)
    return canvas


def render_scene(w: int, h: int, title: str, subtitle: str, screen: Image.Image, band: str) -> Image.Image:
    canvas = background(w, h)
    y = int(h * 0.04)
    y = title_block(canvas, title, subtitle, y)
    if band == "gifts":
        y = card_row(
            canvas,
            y,
            [
                ("seashell", "조개껍데기", "100코인"),
                ("clover", "네잎클로버", "150코인"),
                ("star_piece", "별조각", "300코인"),
            ],
        )
        y += int(h * 0.018)
    elif band == "alarm":
        y = alarm_card(canvas, y)
        y += int(h * 0.018)
    elif band == "dex":
        y = dex_grid(canvas, y)
        y += int(h * 0.016)
    bottom = int(h * 0.035)
    place_phone(canvas, screen, y, int(w * 0.9), h - bottom - y)
    return canvas.convert("RGB")


SCENES = [
    ("01-home", "할 일을 끝내면 자라요", "밥 주고, 산책하고, 같이 지내요", "home.png", "home"),
    ("02-schedule", "제시간에 알려 줄게요", "완료하거나 10분 뒤로 미뤄요", "schedule.png", "alarm"),
    ("03-room", "밤하늘 방을 꾸며요", "가구와 옷으로 방을 바꿔요", "room.png", "room"),
    ("04-shop", "선물을 팔아 코인을 모아요", "모은 코인으로 가구와 옷을 사요", "shop.png", "gifts"),
    ("05-dex", "친구 50종을 모아 봐요", "도감에서 데려오거나 산책에서 만나요", "dex.png", "dex"),
    ("06-widgets", "홈 화면에서도 함께", "위젯으로 밥 주고 일정을 완료해요", "widgets", "widgets"),
]


def main() -> None:
    missing = [name for name in SPECIES if not (ART / f"{name}.webp").exists()]
    if missing:
        raise SystemExit(f"missing species art: {missing}")
    OUT.mkdir(parents=True, exist_ok=True)
    made = []

    feature_path = OUT / "play-feature-1024x500.png"
    feature().save(feature_path, "PNG", optimize=True)
    made.append(feature_path)

    screens = {name: shot(name) for name in ("home.png", "schedule.png", "room.png", "shop.png", "dex.png")}
    screens["widgets"] = build_launcher()

    for slug, title, subtitle, source, band in SCENES:
        screen = screens[source]
        play = render_scene(1080, 1920, title, subtitle, screen, band)
        ios = render_scene(1290, 2796, title, subtitle, screen, band)
        play_path = OUT / f"play-phone-{slug}-1080x1920.png"
        ios_path = OUT / f"ios-67-{slug}-1290x2796.png"
        play.save(play_path, "PNG", optimize=True)
        ios.save(ios_path, "PNG", optimize=True)
        made.extend([play_path, ios_path])

    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    for path in made:
        shutil.copy2(path, ARTIFACTS / path.name)
        with Image.open(path) as image:
            print(f"{path.name} {image.size} {image.mode}")


if __name__ == "__main__":
    main()
