#!/usr/bin/env python3
"""
Генератор картинок для меню HUB: фон окна, арты карточек, панели строк
и вертикальная надпись на полосе.

Стиль: тёмно-фиолетовый гранж — затёртый почти чёрный фон с фиолетовыми
разводами, царапинами и зерном, тонкие светлые рамки, блочные силуэты сцен.

Запуск:  python3 tools/generate_menu_art.py
Выход:   src/main/resources/Common/UI/Custom/HubMenu/Assets/*.png

Полупрозрачных PNG тут нет намеренно: панели, которые в макете выглядят
полупрозрачными, вырезаются из уже готового фона и затемняются. Поэтому
картинки можно класть в разметку как обычный непрозрачный Background —
и они всё равно совпадают с фоном пиксель в пиксель.

Координаты панелей (LAYOUT) обязаны совпадать с Main_ru.ui / Main_en.ui.
Меняешь разметку — поправь и таблицу.
"""

import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SS = 3  # во сколько раз сцена рисуется крупнее итоговой картинки

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(
    ROOT, "src", "main", "resources", "Common", "UI", "Custom", "HubMenu", "Assets"
)

WINDOW_W, WINDOW_H = 1440, 660

# Где какая панель лежит в окне: (x, y, ширина, высота).
# Эти же числа стоят в разметке — из них собираются отступы и якоря.
LAYOUT = {
    "feature": (24, 88, 460, 430),
    "rail": (500, 88, 44, 430),
    "news": (560, 88, 250, 430),
    "rules": (824, 88, 250, 430),
    "row1": (1088, 88, 328, 134),
    "row2": (1088, 236, 328, 134),
    "row3": (1088, 384, 328, 134),
}

# Палитра меню.
ACCENT = "#a35dff"
ACCENT_DIM = "#6b4fa0"
INK = "#c9b3ff"


def rgb(value):
    """#rrggbb -> (r, g, b) в долях единицы."""
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def col(value, alpha=1.0):
    """#rrggbb -> кортеж для PIL с альфой."""
    r, g, b = rgb(value)
    return (int(r * 255), int(g * 255), int(b * 255), int(alpha * 255))


class Scene:
    """Холст: небо, силуэты, блоки, свет, гранж."""

    def __init__(self, width, height, seed):
        self.w = width
        self.h = height
        self.W = width * SS
        self.H = height * SS
        self.rng = random.Random(seed)
        self.img = np.zeros((self.H, self.W, 3), np.float32)

    # ---------------------------------------------------------------- фон

    def sky(self, stops):
        """Вертикальный градиент по опорным точкам [(доля, цвет), ...]."""
        ys = np.linspace(0.0, 1.0, self.H, dtype=np.float32)
        cols = np.zeros((self.H, 3), np.float32)
        stops = sorted(stops, key=lambda s: s[0])
        cols[:] = np.array(stops[0][1], np.float32)
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            mask = (ys >= t0) & (ys <= t1)
            if not mask.any():
                continue
            f = ((ys[mask] - t0) / max(t1 - t0, 1e-6))[:, None]
            f = f * f * (3.0 - 2.0 * f)
            cols[mask] = np.array(c0, np.float32) * (1 - f) + np.array(c1, np.float32) * f
        cols[ys > stops[-1][0]] = np.array(stops[-1][1], np.float32)
        self.img[:] = cols[:, None, :]

    def load(self, image):
        """Взять готовую картинку как основу (для панелей, вырезанных из фона)."""
        arr = np.asarray(image.convert("RGB").resize((self.W, self.H), Image.LANCZOS), np.float32) / 255.0
        self.img[:] = arr

    def glow(self, cx, cy, radius, color, strength=1.0, power=2.2):
        """Мягкое пятно света."""
        xs = (np.arange(self.W, dtype=np.float32) - cx * SS) / (radius * SS)
        ys = (np.arange(self.H, dtype=np.float32) - cy * SS) / (radius * SS)
        dist = np.sqrt(xs[None, :] ** 2 + ys[:, None] ** 2)
        falloff = np.clip(1.0 - dist, 0.0, 1.0) ** power * strength
        self.img = 1.0 - (1.0 - self.img) * (1.0 - falloff[..., None] * np.array(color, np.float32))

    def darken(self, amount):
        self.img *= (1.0 - amount)

    # -------------------------------------------------------------- слои

    def layer(self):
        return Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))

    def blit(self, layer, blur=0.0):
        if blur:
            layer = layer.filter(ImageFilter.GaussianBlur(blur * SS))
        arr = np.asarray(layer, np.float32) / 255.0
        alpha = arr[..., 3:4]
        self.img = self.img * (1.0 - alpha) + arr[..., :3] * alpha

    # ------------------------------------------------------------ фигуры

    def ridge(self, layer, base_y, amplitude, color, alpha=1.0, rough=0.55, step=9):
        """Гряда: ломаная со ступеньками, чтобы читалась «по-блочному»."""
        points = [self.rng.random(), self.rng.random()]
        scale = 1.0
        while len(points) < 129:
            grown = []
            for a, b in zip(points, points[1:]):
                grown.append(a)
                grown.append((a + b) / 2.0 + self.rng.uniform(-1, 1) * scale)
            grown.append(points[-1])
            points = grown
            scale *= rough
        lo, hi = min(points), max(points)
        points = [(p - lo) / max(hi - lo, 1e-6) for p in points]

        poly = []
        for i, p in enumerate(points):
            x = self.W * i / (len(points) - 1)
            y = (base_y - amplitude * p) * SS
            poly.append((x, round(y / (step * SS)) * step * SS))
        poly += [(self.W, self.H), (0, self.H)]
        ImageDraw.Draw(layer).polygon(poly, fill=tuple(int(c * 255) for c in color) + (int(alpha * 255),))

    def block(self, layer, x, y, w, h, color, top=0.30, side=-0.22, alpha=1.0):
        """Блок с подсветкой верхней грани и затенением правой."""
        draw = ImageDraw.Draw(layer)
        a = int(alpha * 255)

        def shade(amount):
            return tuple(int(max(0.0, min(1.0, c + amount)) * 255) for c in color) + (a,)

        x0, y0, x1, y1 = x * SS, y * SS, (x + w) * SS, (y + h) * SS
        lip = max(1.0, min(h, w) * 0.16) * SS
        draw.rectangle([x0, y0, x1, y1], fill=shade(0.0))
        draw.rectangle([x0, y0, x1, y0 + lip], fill=shade(top))
        draw.rectangle([x1 - lip, y0, x1, y1], fill=shade(side))

    def bar(self, layer, x, y, w, h, color, alpha=1.0):
        ImageDraw.Draw(layer).rectangle(
            [x * SS, y * SS, (x + w) * SS, (y + h) * SS],
            fill=tuple(int(c * 255) for c in color) + (int(alpha * 255),),
        )

    def paste_rotated(self, tile, angle, cx, cy):
        tile = tile.rotate(angle, resample=Image.BICUBIC, expand=True)
        out = self.layer()
        out.paste(tile, (int(cx * SS - tile.width / 2), int(cy * SS - tile.height / 2)), tile)
        return out

    def sword_tile(self, length, blade, steel, grip):
        """Меч остриём вверх: клинок, гарда, рукоять, навершие."""
        bw = int(11 * SS)
        L = int(length * SS)
        tile = Image.new("RGBA", (int(52 * SS), L + int(16 * SS)), (0, 0, 0, 0))
        draw = ImageDraw.Draw(tile)
        cx = tile.width // 2
        guard_y = L - int(34 * SS)

        def c(value):
            return tuple(int(v * 255) for v in value) + (255,)

        draw.polygon([(cx, 0), (cx + bw // 2, int(16 * SS)), (cx - bw // 2, int(16 * SS))], fill=c(blade))
        draw.rectangle([cx - bw // 2, int(15 * SS), cx + bw // 2, guard_y], fill=c(blade))
        draw.rectangle([cx - int(1.6 * SS), int(16 * SS), cx + int(1.6 * SS), guard_y], fill=c(steel))
        draw.rectangle([cx - int(19 * SS), guard_y, cx + int(19 * SS), guard_y + int(7 * SS)], fill=c(steel))
        draw.rectangle([cx - int(4 * SS), guard_y + int(7 * SS), cx + int(4 * SS), L], fill=c(grip))
        draw.rectangle([cx - int(7 * SS), L, cx + int(7 * SS), L + int(9 * SS)], fill=c(steel))
        return tile

    def stars(self, layer, count, top, bottom, color, size=1.4):
        draw = ImageDraw.Draw(layer)
        for _ in range(count):
            x = self.rng.uniform(0, self.w)
            y = self.rng.uniform(top, bottom)
            r = self.rng.uniform(0.5, size) * SS
            draw.ellipse(
                [x * SS - r, y * SS - r, x * SS + r, y * SS + r],
                fill=tuple(int(c * 255) for c in color) + (int(self.rng.uniform(0.25, 0.85) * 255),),
            )

    def padlock(self, layer, cx, cy, width, color, alpha=1.0):
        """Замок: дужка, корпус, скважина — как на референсе."""
        draw = ImageDraw.Draw(layer)
        fill = tuple(int(c * 255) for c in color) + (int(alpha * 255),)
        body_w = width
        body_h = width * 0.78
        body_x = cx - body_w / 2
        body_y = cy - body_h / 2 + width * 0.16

        shackle_w = width * 0.58
        thick = width * 0.15
        draw.arc(
            [(cx - shackle_w / 2) * SS, (body_y - shackle_w * 0.62) * SS,
             (cx + shackle_w / 2) * SS, (body_y + shackle_w * 0.38) * SS],
            start=180, end=360, fill=fill, width=int(thick * SS),
        )
        draw.rectangle(
            [(cx - shackle_w / 2) * SS, (body_y - shackle_w * 0.12) * SS,
             (cx - shackle_w / 2 + thick) * SS, body_y * SS], fill=fill,
        )
        draw.rectangle(
            [(cx + shackle_w / 2 - thick) * SS, (body_y - shackle_w * 0.12) * SS,
             (cx + shackle_w / 2) * SS, body_y * SS], fill=fill,
        )
        draw.rectangle(
            [body_x * SS, body_y * SS, (body_x + body_w) * SS, (body_y + body_h) * SS], fill=fill
        )
        hole = width * 0.13
        dark = (10, 7, 18, int(alpha * 255))
        draw.ellipse(
            [(cx - hole) * SS, (body_y + body_h * 0.28 - hole) * SS,
             (cx + hole) * SS, (body_y + body_h * 0.28 + hole) * SS], fill=dark,
        )
        draw.rectangle(
            [(cx - hole * 0.55) * SS, (body_y + body_h * 0.28) * SS,
             (cx + hole * 0.55) * SS, (body_y + body_h * 0.72) * SS], fill=dark,
        )

    # -------------------------------------------------------------- гранж

    def smears(self, count, color, alpha=0.20, size=(0.25, 0.8), seed_shift=0):
        """Крупные размытые разводы — основа затёртого фона."""
        layer = self.layer()
        draw = ImageDraw.Draw(layer)
        for _ in range(count):
            cx = self.rng.uniform(-0.1, 1.1) * self.w
            cy = self.rng.uniform(-0.1, 1.1) * self.h
            rx = self.rng.uniform(*size) * self.w * 0.5
            ry = self.rng.uniform(*size) * self.h * 0.5
            a = int(self.rng.uniform(0.4, 1.0) * alpha * 255)
            draw.ellipse(
                [(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS],
                fill=tuple(int(c * 255) for c in color) + (a,),
            )
        self.blit(layer, blur=14.0)

    def streaks(self, count, color, alpha=0.10):
        """Горизонтальные потёки — следы воды и грязи."""
        layer = self.layer()
        draw = ImageDraw.Draw(layer)
        for _ in range(count):
            y = self.rng.uniform(0, self.h)
            x0 = self.rng.uniform(-0.2, 0.9) * self.w
            length = self.rng.uniform(0.15, 0.7) * self.w
            thick = self.rng.uniform(0.6, 4.0)
            a = int(self.rng.uniform(0.3, 1.0) * alpha * 255)
            draw.rectangle(
                [x0 * SS, y * SS, (x0 + length) * SS, (y + thick) * SS],
                fill=tuple(int(c * 255) for c in color) + (a,),
            )
        self.blit(layer, blur=1.6)

    def scratches(self, count, color, alpha=0.30):
        """Тонкие царапины под разными углами."""
        layer = self.layer()
        draw = ImageDraw.Draw(layer)
        for _ in range(count):
            x = self.rng.uniform(0, self.w)
            y = self.rng.uniform(0, self.h)
            angle = self.rng.uniform(-0.5, 0.5) + self.rng.choice([0.0, math.pi / 2])
            length = self.rng.uniform(8, 120)
            a = int(self.rng.uniform(0.2, 1.0) * alpha * 255)
            draw.line(
                [x * SS, y * SS,
                 (x + math.cos(angle) * length) * SS, (y + math.sin(angle) * length) * SS],
                fill=tuple(int(c * 255) for c in color) + (a,),
                width=max(1, int(self.rng.uniform(0.6, 1.8) * SS)),
            )
        self.blit(layer, blur=0.4)

    def frame(self, color, alpha=1.0, thickness=1):
        """Тонкая рамка по краю картинки — вместо полосок в разметке."""
        layer = self.layer()
        draw = ImageDraw.Draw(layer)
        fill = tuple(int(c * 255) for c in color) + (int(alpha * 255),)
        t = thickness * SS
        draw.rectangle([0, 0, self.W, t], fill=fill)
        draw.rectangle([0, self.H - t, self.W, self.H], fill=fill)
        draw.rectangle([0, 0, t, self.H], fill=fill)
        draw.rectangle([self.W - t, 0, self.W, self.H], fill=fill)
        self.blit(layer)

    def fog(self, top, bottom, color, strength=0.35):
        ys = np.linspace(0.0, 1.0, self.H, dtype=np.float32)
        band = np.clip((ys - top) / max(bottom - top, 1e-6), 0.0, 1.0)
        band = np.sin(band * math.pi) * strength
        self.img = self.img * (1.0 - band[:, None, None]) + np.array(color, np.float32) * band[:, None, None]

    def scrim(self, start=0.56, strength=0.90, color="#0a0712"):
        """Затемнение низа — под подпись карточки."""
        ys = np.linspace(0.0, 1.0, self.H, dtype=np.float32)
        f = np.clip((ys - start) / max(1.0 - start, 1e-6), 0.0, 1.0)
        f = f * f * (3.0 - 2.0 * f) * strength
        self.img = self.img * (1.0 - f[:, None, None]) + np.array(rgb(color), np.float32) * f[:, None, None]

    def vignette(self, strength=0.45):
        xs = (np.arange(self.W, dtype=np.float32) / self.W - 0.5) * 2.0
        ys = (np.arange(self.H, dtype=np.float32) / self.H - 0.5) * 2.0
        d = np.sqrt(xs[None, :] ** 2 * 0.9 + ys[:, None] ** 2 * 0.75)
        f = np.clip(d - 0.55, 0.0, 1.0) ** 1.6 * strength
        self.img *= (1.0 - f[..., None])

    def grain(self, amount=0.018):
        noise = np.random.default_rng(1337).normal(0.0, 1.0, (self.h, self.w))
        noise = np.asarray(
            Image.fromarray(((np.clip(noise, -3, 3) / 6 + 0.5) * 255).astype(np.uint8)).resize(
                (self.W, self.H), Image.BILINEAR
            ), np.float32
        ) / 255.0
        self.img += (noise[..., None] - 0.5) * 2.0 * amount

    # -------------------------------------------------------------- вывод

    def finish(self):
        arr = np.clip(self.img, 0.0, 1.0)
        return Image.fromarray((arr * 255).astype(np.uint8), "RGB").resize((self.w, self.h), Image.LANCZOS)

    def save(self, name):
        img = self.finish()
        path = os.path.join(OUT_DIR, name)
        img.save(path, optimize=True)
        print("%-22s %4dx%-4d %6.1f КБ" % (name, self.w, self.h, os.path.getsize(path) / 1024))
        return img


# ---------------------------------------------------------------- надписи

def font(size, bold=True):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    for base in ("/usr/share/fonts/truetype/dejavu/", "/usr/share/fonts/dejavu/"):
        path = os.path.join(base, name)
        if os.path.exists(path):
            from PIL import ImageFont
            return ImageFont.truetype(path, size)
    from PIL import ImageFont
    return ImageFont.load_default()


def tracked_text(draw, xy, text, fnt, fill, tracking=0):
    """Текст с разрядкой: клиентский шрифт так не умеет, а картинка — да."""
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill)
        x += draw.textlength(ch, font=fnt) + tracking
    return x - tracking - xy[0]


def tracked_width(draw, text, fnt, tracking=0):
    return sum(draw.textlength(ch, font=fnt) for ch in text) + tracking * max(len(text) - 1, 0)


# ====================================================================== сцены


def make_background():
    """Фон окна: затёртая почти чёрная плита с фиолетовыми разводами."""
    s = Scene(WINDOW_W, WINDOW_H, seed=101)
    s.sky([
        (0.00, rgb("#050308")),
        (0.30, rgb("#0b0714")),
        (0.62, rgb("#0e0a19")),
        (1.00, rgb("#06040c")),
    ])
    s.smears(9, rgb("#2a1442"), alpha=0.20, size=(0.35, 1.0))
    s.smears(9, rgb("#030206"), alpha=0.38, size=(0.20, 0.7))
    s.smears(4, rgb("#4a2277"), alpha=0.10, size=(0.15, 0.45))
    s.streaks(90, rgb("#030208"), alpha=0.20)
    s.streaks(40, rgb("#3a2058"), alpha=0.05)
    s.glow(WINDOW_W * 0.5, WINDOW_H * 0.05, WINDOW_W * 0.40, rgb("#4a2878"), 0.20, power=2.6)
    s.scratches(240, rgb("#7a66a0"), alpha=0.08)
    s.scratches(120, rgb("#020106"), alpha=0.26)
    s.vignette(0.70)
    s.grain(0.016)
    return s.save("Background.png")


def crop_panel(background, key):
    """Кусок фона под панелью — чтобы панель легла на него без швов."""
    x, y, w, h = LAYOUT[key]
    return background.crop((x, y, x + w, y + h))


def card_minigames(background):
    """Большая карточка: арена, щит со скрещенными мечами, знамёна, жаровни."""
    x, y, w, h = LAYOUT["feature"]
    s = Scene(w, h, seed=11)
    s.sky([
        (0.00, rgb("#08061a")),
        (0.42, rgb("#120c28")),
        (0.63, rgb("#2b1a4a")),
        (0.72, rgb("#160e28")),
        (1.00, rgb("#0a0714")),
    ])
    s.glow(w * 0.5, h * 0.66, w * 0.58, rgb("#6b3fa8"), 0.42)

    far = s.layer()
    s.ridge(far, base_y=h * 0.66, amplitude=h * 0.13, color=rgb("#1a1430"), step=10)
    s.blit(far, blur=0.6)
    mid = s.layer()
    s.ridge(mid, base_y=h * 0.73, amplitude=h * 0.09, color=rgb("#120d22"), step=8)
    s.blit(mid)
    s.fog(0.58, 0.74, rgb("#31204f"), 0.24)

    arena = s.layer()
    for frac, top, height in ((0.17, 0.760, 0.032), (0.25, 0.792, 0.036), (0.34, 0.828, 0.042)):
        s.block(arena, w * (0.5 - frac), h * top, w * frac * 2, h * height,
                rgb("#251b3c"), top=0.14, side=-0.08)
    s.blit(arena)

    banners = s.layer()
    for bx in (w * 0.15, w * 0.85):
        s.bar(banners, bx - 2.5, h * 0.34, 5, h * 0.44, rgb("#1a1428"))
        s.block(banners, bx - 23, h * 0.36, 46, h * 0.21, rgb("#5d1a6e"), top=0.12, side=-0.12)
        s.bar(banners, bx - 23, h * 0.36, 46, 5, rgb("#9b3ec0"))
        ImageDraw.Draw(banners).polygon(
            [((bx - 23) * SS, h * 0.57 * SS), ((bx + 23) * SS, h * 0.57 * SS), (bx * SS, h * 0.532 * SS)],
            fill=col("#0a0714"),
        )
    s.blit(banners)

    sx, sy = w * 0.5, h * 0.45
    for angle in (42, -42):
        s.blit(s.paste_rotated(
            s.sword_tile(238, rgb("#3d3654"), rgb("#6f6790"), rgb("#2a1e2e")), angle, sx, sy))

    shield = s.layer()
    draw = ImageDraw.Draw(shield)
    hw, hh = 46.0, 54.0
    body = [
        ((sx - hw) * SS, (sy - hh) * SS), ((sx + hw) * SS, (sy - hh) * SS),
        ((sx + hw) * SS, (sy + hh * 0.30) * SS), (sx * SS, (sy + hh * 1.10) * SS),
        ((sx - hw) * SS, (sy + hh * 0.30) * SS),
    ]
    draw.polygon(body, fill=col("#251c3c"))
    draw.polygon([body[0], (sx * SS, (sy - hh) * SS), (sx * SS, (sy + hh * 1.10) * SS), body[4]],
                 fill=col("#302449"))
    draw.line(body + [body[0]], fill=col("#6a5b92"), width=int(2.4 * SS))
    s.blit(shield)

    crest = s.layer()
    s.bar(crest, sx - 5, sy - 44, 10, 84, rgb("#a35dff"))
    s.bar(crest, sx - 34, sy - 14, 68, 10, rgb("#a35dff"))
    s.blit(crest)
    s.glow(sx, sy, w * 0.30, rgb("#7a45c8"), 0.22, power=3.0)

    fires = s.layer()
    for fx in (w * 0.30, w * 0.70):
        s.block(fires, fx - 14, h * 0.742, 28, 24, rgb("#2a2140"), top=0.16, side=-0.10)
        s.bar(fires, fx - 8, h * 0.720, 16, 13, rgb("#e07022"))
    s.blit(fires)
    for fx in (w * 0.30, w * 0.70):
        s.glow(fx, h * 0.732, w * 0.24, rgb("#ff8a30"), 0.58, power=2.6)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.912, amplitude=h * 0.02, color=rgb("#080610"), step=7)
    s.blit(ground)

    s.scratches(60, rgb("#a48fd0"), alpha=0.07)
    s.scrim(0.58, 0.94)
    s.vignette(0.50)
    s.grain()
    s.darken(0.24)
    s.frame(rgb("#8a6fc4"), alpha=0.85)
    s.save("Card_Minigames.png")


def card_news(background):
    """Доска объявлений у деревни: фонарь, листки, тёплые окна."""
    x, y, w, h = LAYOUT["news"]
    s = Scene(w, h, seed=23)
    s.sky([
        (0.00, rgb("#08061a")),
        (0.30, rgb("#110b22")),
        (0.44, rgb("#2b1b30")),
        (0.56, rgb("#170e22")),
        (1.00, rgb("#0a0714")),
    ])
    s.glow(w * 0.5, h * 0.42, w * 1.05, rgb("#8a4a5c"), 0.40)
    s.glow(w * 0.5, h * 0.44, w * 0.75, rgb("#9a5220"), 0.30)

    far = s.layer()
    s.ridge(far, base_y=h * 0.36, amplitude=h * 0.075, color=rgb("#161029"), step=9)
    s.blit(far, blur=0.5)

    village = s.layer()
    draw = ImageDraw.Draw(village)
    dark = col("#0b0816")
    for hx, hw, hh in ((0.00, 0.18, 0.050), (0.23, 0.14, 0.036), (0.43, 0.20, 0.058), (0.73, 0.16, 0.042)):
        x0, x1 = w * hx, w * (hx + hw)
        top = h * 0.405 - h * hh
        draw.rectangle([x0 * SS, top * SS, x1 * SS, h * 0.41 * SS], fill=dark)
        draw.polygon([(x0 * SS - 4 * SS, top * SS), (x1 * SS + 4 * SS, top * SS),
                      ((x0 + x1) / 2 * SS, (top - h * 0.030) * SS)], fill=dark)
        wx = (x0 + x1) / 2
        draw.rectangle([(wx - 2.5) * SS, (top + h * 0.013) * SS, (wx + 2.5) * SS, (top + h * 0.028) * SS],
                       fill=col("#d9853a"))
    s.blit(village)
    s.fog(0.34, 0.46, rgb("#31203a"), 0.22)

    board = s.layer()
    for px in (w * 0.25, w * 0.75):
        s.block(board, px - 7, h * 0.660, 14, h * 0.250, rgb("#251a1c"), top=0.12, side=-0.10)
    s.block(board, w * 0.10, h * 0.470, w * 0.80, h * 0.215, rgb("#342624"), top=0.16, side=-0.12)
    for i in range(1, 4):
        s.bar(board, w * 0.10, h * (0.470 + 0.215 * i / 4), w * 0.80, 1.8, rgb("#191016"), alpha=0.8)
    s.block(board, w * 0.06, h * 0.444, w * 0.88, h * 0.028, rgb("#3e2d2a"), top=0.18, side=-0.12)
    s.blit(board)

    notes = s.layer()
    for nx, ny, nw, nh in ((0.16, 0.494, 0.26, 0.072), (0.49, 0.488, 0.30, 0.086), (0.20, 0.590, 0.32, 0.062)):
        s.block(notes, w * nx, h * ny, w * nw, h * nh, rgb("#a2947f"), top=0.08, side=-0.10)
        for line in range(3):
            s.bar(notes, w * nx + w * 0.025, h * (ny + 0.018 + line * 0.016),
                  w * nw - w * 0.05, 1.7, rgb("#5f5445"), alpha=0.75)
        s.bar(notes, w * (nx + nw / 2) - 2, h * ny + 2.5, 4, 4, rgb("#a35dff"))
    s.blit(notes)

    lamp = s.layer()
    lx, ly = w * 0.86, h * 0.492
    s.bar(lamp, lx - 1.5, h * 0.470, 3, h * 0.024, rgb("#150f14"))
    s.block(lamp, lx - 9, ly, 18, 25, rgb("#281d1c"), top=0.12, side=-0.10)
    s.bar(lamp, lx - 5, ly + 5, 10, 15, rgb("#ffb84a"))
    s.blit(lamp)
    s.glow(lx, ly + 12, w * 0.44, rgb("#ffa63c"), 0.58, power=2.4)

    crates = s.layer()
    s.block(crates, w * 0.09, h * 0.845, 32, 28, rgb("#251a1a"), top=0.12, side=-0.10)
    s.block(crates, w * 0.13, h * 0.818, 23, 27, rgb("#2d2120"), top=0.12, side=-0.10)
    s.block(crates, w * 0.66, h * 0.852, 36, 21, rgb("#231919"), top=0.12, side=-0.10)
    s.blit(crates)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.935, amplitude=h * 0.018, color=rgb("#080611"), step=6)
    s.blit(ground)

    s.scratches(40, rgb("#a48fd0"), alpha=0.07)
    s.scrim(0.64, 0.94)
    s.vignette(0.52)
    s.grain()
    s.darken(0.24)
    s.frame(rgb("#6b4fa0"), alpha=0.75)
    s.save("Card_News.png")


def card_rules(background):
    """Каменная скрижаль с высеченными строками и холодными огнями."""
    x, y, w, h = LAYOUT["rules"]
    s = Scene(w, h, seed=37)
    s.sky([
        (0.00, rgb("#08061a")),
        (0.46, rgb("#100b22")),
        (0.68, rgb("#20193a")),
        (0.76, rgb("#120d24")),
        (1.00, rgb("#090714")),
    ])
    s.glow(w * 0.5, h * 0.62, w * 1.00, rgb("#5a3a9c"), 0.42)

    far = s.layer()
    s.ridge(far, base_y=h * 0.62, amplitude=h * 0.12, color=rgb("#151029"), step=10)
    s.blit(far, blur=0.6)
    s.fog(0.56, 0.74, rgb("#241a40"), 0.24)

    pillars = s.layer()
    for px, pw, ph in ((0.00, 0.12, 0.28), (0.88, 0.13, 0.24), (0.74, 0.09, 0.16)):
        s.block(pillars, w * px, h * (0.75 - ph), w * pw, h * ph, rgb("#110c20"), top=0.06, side=-0.04)
    s.blit(pillars)

    plinth = s.layer()
    s.block(plinth, w * 0.175, h * 0.845, w * 0.65, h * 0.030, rgb("#1b1530"), top=0.14, side=-0.08)
    s.block(plinth, w * 0.120, h * 0.875, w * 0.76, h * 0.032, rgb("#16112a"), top=0.12, side=-0.08)
    s.blit(plinth)

    tablet = s.layer()
    tx, tw = w * 0.245, w * 0.51
    top_y, bottom_y = h * 0.400, h * 0.850
    draw = ImageDraw.Draw(tablet)
    stone = col("#221a36")
    stone_lit = col("#2b2244")
    shape = [
        (tx * SS, bottom_y * SS), (tx * SS, (top_y + h * 0.045) * SS),
        ((tx + tw * 0.28) * SS, top_y * SS), ((tx + tw * 0.72) * SS, top_y * SS),
        ((tx + tw) * SS, (top_y + h * 0.045) * SS), ((tx + tw) * SS, bottom_y * SS),
    ]
    draw.polygon(shape, fill=stone)
    s.blit(tablet)

    lit = s.layer()
    ImageDraw.Draw(lit).polygon(
        [(tx * SS, bottom_y * SS), (tx * SS, (top_y + h * 0.045) * SS),
         ((tx + tw * 0.28) * SS, top_y * SS), ((tx + tw * 0.44) * SS, top_y * SS),
         ((tx + tw * 0.44) * SS, bottom_y * SS)],
        fill=stone_lit[:3] + (130,),
    )
    s.blit(lit, blur=7.0)

    tablet = s.layer()
    draw = ImageDraw.Draw(tablet)
    for i in range(6):
        ly = top_y + h * (0.070 + i * 0.047)
        width = tw * (0.70 if i % 3 else 0.48)
        lx = tx + (tw - width) / 2
        s.bar(tablet, lx, ly, width, 4.5, rgb("#100b1d"))
        s.bar(tablet, lx, ly + 4.5, width, 1.6, rgb("#473a6a"), alpha=0.85)
    draw.polygon(
        [(w * 0.5 * SS, (top_y + h * 0.335) * SS), ((w * 0.5 + w * 0.075) * SS, (top_y + h * 0.380) * SS),
         (w * 0.5 * SS, (top_y + h * 0.425) * SS), ((w * 0.5 - w * 0.075) * SS, (top_y + h * 0.380) * SS)],
        fill=col("#8a5fd8"),
    )
    draw.line(
        [((tx + tw * 0.62) * SS, (top_y + h * 0.06) * SS), ((tx + tw * 0.70) * SS, (top_y + h * 0.20) * SS),
         ((tx + tw * 0.60) * SS, (top_y + h * 0.33) * SS)],
        fill=col("#0d0918"), width=int(1.8 * SS),
    )
    s.bar(tablet, tx, top_y + h * 0.045, 3, bottom_y - top_y - h * 0.045, rgb("#a98fe0"), alpha=0.50)
    s.blit(tablet)
    s.glow(w * 0.5, h * 0.40, w * 0.55, rgb("#7a52c8"), 0.22, power=3.0)

    lights = s.layer()
    for fx in (w * 0.135, w * 0.865):
        s.block(lights, fx - 10, h * 0.800, 20, 18, rgb("#1e1836"), top=0.14, side=-0.10)
        s.bar(lights, fx - 6, h * 0.784, 12, 10, rgb("#b98dff"))
    s.blit(lights)
    for fx in (w * 0.135, w * 0.865):
        s.glow(fx, h * 0.792, w * 0.32, rgb("#9a6cf0"), 0.48, power=2.6)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.932, amplitude=h * 0.015, color=rgb("#070511"), step=6)
    s.blit(ground)

    s.scratches(40, rgb("#a48fd0"), alpha=0.07)
    s.scrim(0.66, 0.94)
    s.vignette(0.52)
    s.grain()
    s.darken(0.24)
    s.frame(rgb("#6b4fa0"), alpha=0.75)
    s.save("Card_Rules.png")


def row_panel(background, key, name, locked):
    """Строка правой колонки: вырезанный кусок фона, рамка, замок для «скоро»."""
    _, _, w, h = LAYOUT[key]
    s = Scene(w, h, seed=61 + len(name))
    s.load(crop_panel(background, key))
    s.darken(0.42 if locked else 0.28)
    if not locked:
        s.glow(w * 0.16, h * 0.5, w * 0.55, rgb("#6b3fa8"), 0.22, power=2.6)
    lock = s.layer()
    if locked:
        s.padlock(lock, w * 0.135, h * 0.5, 40, rgb("#8b7fae"), alpha=0.92)
    else:
        s.padlock(lock, w * 0.135, h * 0.5, 0.1, rgb("#000000"), alpha=0.0)
    s.blit(lock)
    s.scratches(18, rgb("#a48fd0"), alpha=0.06)
    s.grain(0.012)
    s.frame(rgb("#4a3768") if locked else rgb("#8a6fc4"), alpha=0.85 if locked else 0.90)
    s.save(name)


def rail(background, text, name):
    """Вертикальная надпись на полосе между карточками."""
    _, _, w, h = LAYOUT["rail"]
    s = Scene(w, h, seed=77)
    s.load(crop_panel(background, "rail"))
    s.darken(0.35)

    # Текст рисуется горизонтально и поворачивается — в разметке поворота нет.
    fnt = font(int(21 * SS))
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    tracking = int(5 * SS)
    text_w = int(tracked_width(probe, text, fnt, tracking)) + 8 * SS
    text_h = int(34 * SS)
    tile = Image.new("RGBA", (text_w, text_h), (0, 0, 0, 0))
    tracked_text(ImageDraw.Draw(tile), (4 * SS, 0), text, fnt, col("#9d8cc4"), tracking)
    tile = tile.resize((int(text_w * 0.88), text_h), Image.LANCZOS)  # чуть уже — ближе к узкому шрифту
    tile = tile.rotate(90, expand=True)

    layer = s.layer()
    layer.paste(tile, (int(s.W / 2 - tile.width / 2), int(s.H / 2 - tile.height / 2)), tile)
    s.blit(layer)

    line = s.layer()
    s.bar(line, w * 0.5 - 0.5, 6, 1, h * 0.5 - tile.height / SS / 2 - 14, rgb("#4a3768"), alpha=0.9)
    s.bar(line, w * 0.5 - 0.5, h * 0.5 + tile.height / SS / 2 + 14, 1,
          h * 0.5 - tile.height / SS / 2 - 20, rgb("#4a3768"), alpha=0.9)
    s.blit(line)

    s.grain(0.012)
    s.save(name)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    background = make_background()
    card_minigames(background)
    card_news(background)
    card_rules(background)
    row_panel(background, "row1", "Row_Active.png", locked=False)
    row_panel(background, "row2", "Row_Locked_A.png", locked=True)
    row_panel(background, "row3", "Row_Locked_B.png", locked=True)
    rail(background, "РАЗДЕЛЫ", "Rail_ru.png")
    rail(background, "SECTIONS", "Rail_en.png")


if __name__ == "__main__":
    main()
