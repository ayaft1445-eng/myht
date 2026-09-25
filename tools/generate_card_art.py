#!/usr/bin/env python3
"""
Генератор фоновых картинок для карточек меню HUB.

Каждая карточка главного окна получает свою сцену в духе Hytale:
блочные силуэты, тёплый свет факелов, дымка у горизонта и тёмная
подложка внизу, чтобы белый капс на карточке оставался читаемым.

Запуск:  python3 tools/generate_card_art.py
Выход:   src/main/resources/Common/UI/Custom/HubMenu/Assets/*.png

Картинки лежат в репозитории, пересобирать их для обычной сборки мода
не нужно — скрипт нужен, только если хочешь поменять цвета или сцены.
"""

import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# Во сколько раз сцена рисуется крупнее итоговой картинки.
# Потом всё уменьшается с сглаживанием — так края получаются мягкими,
# а не «лесенкой».
SS = 3

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "Common", "UI", "Custom", "HubMenu", "Assets",
)


def rgb(value):
    """#rrggbb -> (r, g, b) в долях единицы."""
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


class Scene:
    """Холст сцены: небо, силуэты, блоки, свет."""

    def __init__(self, width, height, seed):
        self.w = width
        self.h = height
        self.W = width * SS
        self.H = height * SS
        self.rng = random.Random(seed)
        self.img = np.zeros((self.H, self.W, 3), np.float32)

    # ---------------------------------------------------------------- фон

    def sky(self, stops):
        """Вертикальный градиент неба по опорным точкам [(доля, цвет), ...]."""
        ys = np.linspace(0.0, 1.0, self.H, dtype=np.float32)
        cols = np.zeros((self.H, 3), np.float32)
        stops = sorted(stops, key=lambda s: s[0])

        cols[:] = np.array(stops[0][1], np.float32)
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            mask = (ys >= t0) & (ys <= t1)
            if not mask.any():
                continue
            f = ((ys[mask] - t0) / max(t1 - t0, 1e-6))[:, None]
            f = f * f * (3.0 - 2.0 * f)  # плавное затухание на краях
            cols[mask] = np.array(c0, np.float32) * (1 - f) + np.array(c1, np.float32) * f
        cols[ys > stops[-1][0]] = np.array(stops[-1][1], np.float32)

        self.img[:] = cols[:, None, :]

    def glow(self, cx, cy, radius, color, strength=1.0, power=2.2):
        """Мягкое пятно света: свет факела, зарево над горизонтом."""
        xs = (np.arange(self.W, dtype=np.float32) - cx * SS) / (radius * SS)
        ys = (np.arange(self.H, dtype=np.float32) - cy * SS) / (radius * SS)
        dist = np.sqrt(xs[None, :] ** 2 + ys[:, None] ** 2)
        falloff = np.clip(1.0 - dist, 0.0, 1.0) ** power * strength
        tint = np.array(color, np.float32)
        # Экранное смешивание — свет добавляется, но не выбеливает картинку.
        self.img = 1.0 - (1.0 - self.img) * (1.0 - falloff[..., None] * tint)

    # -------------------------------------------------------------- слои

    def layer(self):
        """Пустой прозрачный слой для рисования фигур."""
        return Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))

    def blit(self, layer, blur=0.0):
        """Положить слой поверх сцены, при желании размыв его."""
        if blur:
            layer = layer.filter(ImageFilter.GaussianBlur(blur * SS))
        arr = np.asarray(layer, np.float32) / 255.0
        alpha = arr[..., 3:4]
        self.img = self.img * (1.0 - alpha) + arr[..., :3] * alpha

    # ------------------------------------------------------------ фигуры

    def ridge(self, layer, base_y, amplitude, color, alpha=1.0, rough=0.55, step=9):
        """Гряда гор: ломаная линия, ступенчатая — чтобы читалась «по-блочному»."""
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
            y = round(y / (step * SS)) * step * SS  # ступеньки вместо плавной линии
            poly.append((x, y))
        poly += [(self.W, self.H), (0, self.H)]

        fill = tuple(int(c * 255) for c in color) + (int(alpha * 255),)
        ImageDraw.Draw(layer).polygon(poly, fill=fill)

    def block(self, layer, x, y, w, h, color, top=0.30, side=-0.22, alpha=1.0):
        """Блок с подсветкой верхней грани и затенением правой — объём кубика."""
        draw = ImageDraw.Draw(layer)
        a = int(alpha * 255)

        def shade(amount):
            return tuple(
                int(max(0.0, min(1.0, c + amount)) * 255) for c in color
            ) + (a,)

        x0, y0 = x * SS, y * SS
        x1, y1 = (x + w) * SS, (y + h) * SS
        lip = max(1.0, min(h, w) * 0.16) * SS

        draw.rectangle([x0, y0, x1, y1], fill=shade(0.0))
        draw.rectangle([x0, y0, x1, y0 + lip], fill=shade(top))
        draw.rectangle([x1 - lip, y0, x1, y1], fill=shade(side))

    def bar(self, layer, x, y, w, h, color, alpha=1.0):
        """Плоский прямоугольник без объёма."""
        fill = tuple(int(c * 255) for c in color) + (int(alpha * 255),)
        ImageDraw.Draw(layer).rectangle(
            [x * SS, y * SS, (x + w) * SS, (y + h) * SS], fill=fill
        )

    def rotated(self, w, h, color, angle, cx, cy, alpha=1.0, tip=0.0):
        """Повёрнутая деталь (клинок, древко копья) — рисуется отдельно и вклеивается."""
        pad = int(max(w, h) * SS)
        tile = Image.new("RGBA", (int(w * SS) + pad, int(h * SS) + pad), (0, 0, 0, 0))
        draw = ImageDraw.Draw(tile)
        fill = tuple(int(c * 255) for c in color) + (int(alpha * 255),)
        ox, oy = pad // 2, pad // 2
        draw.rectangle([ox, oy + tip * SS, ox + w * SS, oy + h * SS], fill=fill)
        if tip:
            draw.polygon(
                [
                    (ox, oy + tip * SS),
                    (ox + w * SS, oy + tip * SS),
                    (ox + w * SS / 2, oy),
                ],
                fill=fill,
            )
        tile = tile.rotate(angle, resample=Image.BICUBIC, expand=True)
        out = self.layer()
        out.paste(tile, (int(cx * SS - tile.width / 2), int(cy * SS - tile.height / 2)), tile)
        return out

    def paste_rotated(self, tile, angle, cx, cy):
        """Вклеить готовый спрайт, повернув его вокруг своего центра."""
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

        def col(c, a=255):
            return tuple(int(v * 255) for v in c) + (a,)

        draw.polygon(  # остриё
            [(cx, 0), (cx + bw // 2, int(16 * SS)), (cx - bw // 2, int(16 * SS))], fill=col(blade)
        )
        draw.rectangle([cx - bw // 2, int(15 * SS), cx + bw // 2, guard_y], fill=col(blade))
        draw.rectangle([cx - int(1.6 * SS), int(16 * SS), cx + int(1.6 * SS), guard_y], fill=col(steel))
        draw.rectangle(  # гарда
            [cx - int(19 * SS), guard_y, cx + int(19 * SS), guard_y + int(7 * SS)], fill=col(steel)
        )
        draw.rectangle(  # рукоять
            [cx - int(4 * SS), guard_y + int(7 * SS), cx + int(4 * SS), L], fill=col(grip)
        )
        draw.rectangle(  # навершие
            [cx - int(7 * SS), L, cx + int(7 * SS), L + int(9 * SS)], fill=col(steel)
        )
        return tile

    def stars(self, layer, count, top, bottom, color, size=1.4):
        draw = ImageDraw.Draw(layer)
        for _ in range(count):
            x = self.rng.uniform(0, self.w)
            y = self.rng.uniform(top, bottom)
            r = self.rng.uniform(0.5, size) * SS
            a = int(self.rng.uniform(0.25, 0.85) * 255)
            fill = tuple(int(c * 255) for c in color) + (a,)
            draw.ellipse([x * SS - r, y * SS - r, x * SS + r, y * SS + r], fill=fill)

    # -------------------------------------------------------------- финал

    def fog(self, top, bottom, color, strength=0.35):
        """Дымка на дальнем плане — разделяет слои по глубине."""
        ys = np.linspace(0.0, 1.0, self.H, dtype=np.float32)
        band = np.clip((ys - top) / max(bottom - top, 1e-6), 0.0, 1.0)
        band = np.sin(band * math.pi) * strength  # мягкая полоса
        tint = np.array(color, np.float32)
        self.img = self.img * (1.0 - band[:, None, None]) + tint * band[:, None, None]

    def scrim(self, start=0.56, strength=0.90, color="#0b0b0c"):
        """Затемнение низа карточки — под подпись («МИНИ-ИГРЫ» и т.д.)."""
        ys = np.linspace(0.0, 1.0, self.H, dtype=np.float32)
        f = np.clip((ys - start) / max(1.0 - start, 1e-6), 0.0, 1.0)
        f = f * f * (3.0 - 2.0 * f) * strength
        tint = np.array(rgb(color), np.float32)
        self.img = self.img * (1.0 - f[:, None, None]) + tint * f[:, None, None]

    def vignette(self, strength=0.45):
        xs = (np.arange(self.W, dtype=np.float32) / self.W - 0.5) * 2.0
        ys = (np.arange(self.H, dtype=np.float32) / self.H - 0.5) * 2.0
        d = np.sqrt(xs[None, :] ** 2 * 0.9 + ys[:, None] ** 2 * 0.75)
        f = np.clip(d - 0.55, 0.0, 1.0) ** 1.6 * strength
        self.img *= (1.0 - f[..., None])

    def grain(self, amount=0.018):
        noise = np.random.default_rng(1337).normal(0.0, amount, (self.h, self.w, 1))
        noise = np.asarray(
            Image.fromarray(((noise[..., 0] * 0.5 + 0.5) * 255).astype(np.uint8)).resize(
                (self.W, self.H), Image.BILINEAR
            ),
            np.float32,
        ) / 255.0
        self.img += (noise[..., None] - 0.5) * 2.0 * amount

    def save(self, name):
        arr = np.clip(self.img, 0.0, 1.0)
        img = Image.fromarray((arr * 255).astype(np.uint8), "RGB")
        img = img.resize((self.w, self.h), Image.LANCZOS)
        path = os.path.join(OUT_DIR, name)
        img.save(path, optimize=True)
        print("%-16s %dx%d  %.1f КБ" % (name, self.w, self.h, os.path.getsize(path) / 1024))


# ====================================================================== сцены


def card_minigames(w=520, h=470):
    """Арена на закате: знамёна, щит со скрещенными мечами, жаровни."""
    s = Scene(w, h, seed=11)
    s.sky([
        (0.00, rgb("#080911")),
        (0.42, rgb("#0f1223")),
        (0.63, rgb("#2b1f3d")),
        (0.72, rgb("#16111f")),
        (1.00, rgb("#0a0a0e")),
    ])
    s.glow(w * 0.5, h * 0.66, w * 0.58, rgb("#563878"), 0.50)

    far = s.layer()
    s.ridge(far, base_y=h * 0.66, amplitude=h * 0.13, color=rgb("#171b2a"), step=10)
    s.blit(far, blur=0.6)

    mid = s.layer()
    s.ridge(mid, base_y=h * 0.73, amplitude=h * 0.09, color=rgb("#10131d"), step=8)
    s.blit(mid)

    s.fog(0.58, 0.74, rgb("#2e2340"), 0.24)

    # Ступени арены.
    arena = s.layer()
    for frac, top, height in ((0.17, 0.770, 0.032), (0.25, 0.802, 0.036), (0.34, 0.838, 0.042)):
        s.block(
            arena,
            w * (0.5 - frac), h * top, w * frac * 2, h * height,
            rgb("#232833"), top=0.14, side=-0.08,
        )
    s.blit(arena)

    # Знамёна по краям.
    banners = s.layer()
    for bx in (w * 0.16, w * 0.84):
        s.bar(banners, bx - 2.5, h * 0.36, 5, h * 0.44, rgb("#181b23"))
        s.block(banners, bx - 23, h * 0.38, 46, h * 0.21, rgb("#6b1226"), top=0.12, side=-0.12)
        s.bar(banners, bx - 23, h * 0.38, 46, 5, rgb("#9c2036"))
        ImageDraw.Draw(banners).polygon(
            [
                ((bx - 23) * SS, h * 0.59 * SS),
                ((bx + 23) * SS, h * 0.59 * SS),
                (bx * SS, h * 0.552 * SS),
            ],
            fill=(int(0.045 * 255), int(0.045 * 255), int(0.06 * 255), 255),
        )
    s.blit(banners)

    sx, sy = w * 0.5, h * 0.47

    # Мечи — за щитом, крест-накрест.
    for angle in (42, -42):
        tile = s.sword_tile(248, rgb("#3b4354"), rgb("#697284"), rgb("#2a1e19"))
        s.blit(s.paste_rotated(tile, angle, sx, sy))

    # Щит.
    shield = s.layer()
    draw = ImageDraw.Draw(shield)
    hw, hh = 46.0, 54.0
    body = [
        ((sx - hw) * SS, (sy - hh) * SS),
        ((sx + hw) * SS, (sy - hh) * SS),
        ((sx + hw) * SS, (sy + hh * 0.30) * SS),
        (sx * SS, (sy + hh * 1.10) * SS),
        ((sx - hw) * SS, (sy + hh * 0.30) * SS),
    ]
    draw.polygon(body, fill=(int(0.16 * 255), int(0.18 * 255), int(0.24 * 255), 255))
    draw.polygon(  # левая половина светлее — объём
        [body[0], (sx * SS, (sy - hh) * SS), (sx * SS, (sy + hh * 1.10) * SS), body[4]],
        fill=(int(0.21 * 255), int(0.23 * 255), int(0.30 * 255), 255),
    )
    draw.line(body + [body[0]], fill=(int(0.36 * 255), int(0.39 * 255), int(0.48 * 255), 255), width=int(2.4 * SS))
    s.blit(shield)

    # Эмблема на щите.
    crest = s.layer()
    s.bar(crest, sx - 5, sy - 44, 10, 84, rgb("#8e1a2c"))
    s.bar(crest, sx - 34, sy - 14, 68, 10, rgb("#8e1a2c"))
    s.blit(crest)

    # Жаровни.
    fires = s.layer()
    for fx in (w * 0.31, w * 0.69):
        s.block(fires, fx - 14, h * 0.752, 28, 24, rgb("#272c37"), top=0.16, side=-0.10)
        s.bar(fires, fx - 8, h * 0.730, 16, 13, rgb("#e07022"))
    s.blit(fires)
    for fx in (w * 0.31, w * 0.69):
        s.glow(fx, h * 0.742, w * 0.22, rgb("#ff8a30"), 0.60, power=2.6)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.918, amplitude=h * 0.02, color=rgb("#08080c"), step=7)
    s.blit(ground)

    s.scrim(0.63, 0.88)
    s.vignette(0.48)
    s.grain()
    s.save("Card_Minigames.png")


def card_news(w=240, h=470):
    """Доска объявлений у деревни: фонарь, листки, тёплый свет окон."""
    s = Scene(w, h, seed=23)
    s.sky([
        (0.00, rgb("#08090f")),
        (0.30, rgb("#0f1119")),
        (0.44, rgb("#2d201a")),
        (0.56, rgb("#150f0f")),
        (1.00, rgb("#0a0a0c")),
    ])
    s.glow(w * 0.5, h * 0.42, w * 1.05, rgb("#9a5220"), 0.46)

    far = s.layer()
    s.ridge(far, base_y=h * 0.36, amplitude=h * 0.075, color=rgb("#13151d"), step=9)
    s.blit(far, blur=0.5)

    # Крыши деревни у горизонта — над доской, чтобы их было видно.
    village = s.layer()
    draw = ImageDraw.Draw(village)
    dark = (int(0.06 * 255), int(0.065 * 255), int(0.085 * 255), 255)
    for hx, hw, hh in ((0.00, 0.18, 0.050), (0.23, 0.14, 0.036), (0.43, 0.20, 0.058), (0.73, 0.16, 0.042)):
        x0, x1 = w * hx, w * (hx + hw)
        top = h * 0.405 - h * hh
        draw.rectangle([x0 * SS, top * SS, x1 * SS, h * 0.41 * SS], fill=dark)
        draw.polygon(
            [(x0 * SS - 4 * SS, top * SS), (x1 * SS + 4 * SS, top * SS),
             ((x0 + x1) / 2 * SS, (top - h * 0.030) * SS)],
            fill=dark,
        )
        wx = (x0 + x1) / 2
        draw.rectangle(
            [(wx - 2.5) * SS, (top + h * 0.013) * SS, (wx + 2.5) * SS, (top + h * 0.028) * SS],
            fill=(int(0.85 * 255), int(0.52 * 255), int(0.20 * 255), 230),
        )
    s.blit(village)
    s.fog(0.34, 0.46, rgb("#31221b"), 0.22)

    # Доска.
    board = s.layer()
    for px in (w * 0.25, w * 0.75):
        s.block(board, px - 7, h * 0.660, 14, h * 0.250, rgb("#231a12"), top=0.12, side=-0.10)
    s.block(board, w * 0.10, h * 0.470, w * 0.80, h * 0.215, rgb("#33271a"), top=0.16, side=-0.12)
    for i in range(1, 4):
        s.bar(board, w * 0.10, h * (0.470 + 0.215 * i / 4), w * 0.80, 1.8, rgb("#18110a"), alpha=0.8)
    s.block(board, w * 0.06, h * 0.444, w * 0.88, h * 0.028, rgb("#3d2e1e"), top=0.18, side=-0.12)
    s.blit(board)

    # Приколотые листки.
    notes = s.layer()
    for nx, ny, nw, nh in (
        (0.16, 0.494, 0.26, 0.072),
        (0.49, 0.488, 0.30, 0.086),
        (0.20, 0.590, 0.32, 0.062),
    ):
        s.block(notes, w * nx, h * ny, w * nw, h * nh, rgb("#a2967c"), top=0.08, side=-0.10)
        for line in range(3):
            s.bar(
                notes,
                w * nx + w * 0.025, h * (ny + 0.018 + line * 0.016),
                w * nw - w * 0.05, 1.7, rgb("#615840"), alpha=0.75,
            )
        s.bar(notes, w * (nx + nw / 2) - 2, h * ny + 2.5, 4, 4, rgb("#a3202f"))
    s.blit(notes)

    # Фонарь на цепочке под козырьком.
    lamp = s.layer()
    lx, ly = w * 0.86, h * 0.492
    s.bar(lamp, lx - 1.5, h * 0.470, 3, h * 0.024, rgb("#150f0a"))
    s.block(lamp, lx - 9, ly, 18, 25, rgb("#261d14"), top=0.12, side=-0.10)
    s.bar(lamp, lx - 5, ly + 5, 10, 15, rgb("#ffb84a"))
    s.blit(lamp)
    s.glow(lx, ly + 12, w * 0.44, rgb("#ffa63c"), 0.60, power=2.4)

    # Ящики у основания — чтобы низ не был пустым.
    crates = s.layer()
    s.block(crates, w * 0.09, h * 0.845, 32, 28, rgb("#241b13"), top=0.12, side=-0.10)
    s.block(crates, w * 0.13, h * 0.818, 23, 27, rgb("#2c2118"), top=0.12, side=-0.10)
    s.block(crates, w * 0.66, h * 0.852, 36, 21, rgb("#221a12"), top=0.12, side=-0.10)
    s.blit(crates)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.935, amplitude=h * 0.018, color=rgb("#08070a"), step=6)
    s.blit(ground)

    s.scrim(0.72, 0.90)
    s.vignette(0.50)
    s.grain()
    s.save("Card_News.png")


def card_rules(w=240, h=470):
    """Каменная скрижаль с высеченными строками, копья стражи, холодный свет."""
    s = Scene(w, h, seed=37)
    s.sky([
        (0.00, rgb("#070a10")),
        (0.46, rgb("#0c111b")),
        (0.68, rgb("#1b2634")),
        (0.76, rgb("#0e141b")),
        (1.00, rgb("#08090c")),
    ])
    s.glow(w * 0.5, h * 0.62, w * 1.00, rgb("#356a98"), 0.54)

    far = s.layer()
    s.ridge(far, base_y=h * 0.62, amplitude=h * 0.12, color=rgb("#121822"), step=10)
    s.blit(far, blur=0.6)
    s.fog(0.56, 0.74, rgb("#1d2b3b"), 0.24)

    # Скальные столбы по краям.
    pillars = s.layer()
    for px, pw, ph in ((0.00, 0.12, 0.28), (0.88, 0.13, 0.24), (0.74, 0.09, 0.16)):
        s.block(pillars, w * px, h * (0.75 - ph), w * pw, h * ph, rgb("#0e1219"), top=0.06, side=-0.04)
    s.blit(pillars)

    # Постамент под скрижалью.
    plinth = s.layer()
    s.block(plinth, w * 0.175, h * 0.845, w * 0.65, h * 0.030, rgb("#171c24"), top=0.14, side=-0.08)
    s.block(plinth, w * 0.120, h * 0.875, w * 0.76, h * 0.032, rgb("#131820"), top=0.12, side=-0.08)
    s.blit(plinth)

    # Скрижаль.
    tablet = s.layer()
    tx, tw = w * 0.245, w * 0.51
    top_y, bottom_y = h * 0.400, h * 0.850
    draw = ImageDraw.Draw(tablet)
    stone = (int(0.13 * 255), int(0.15 * 255), int(0.19 * 255), 255)
    stone_lit = (int(0.16 * 255), int(0.185 * 255), int(0.235 * 255), 255)
    draw.polygon(  # корпус со скруглённым верхом
        [
            (tx * SS, bottom_y * SS),
            (tx * SS, (top_y + h * 0.045) * SS),
            ((tx + tw * 0.28) * SS, top_y * SS),
            ((tx + tw * 0.72) * SS, top_y * SS),
            ((tx + tw) * SS, (top_y + h * 0.045) * SS),
            ((tx + tw) * SS, bottom_y * SS),
        ],
        fill=stone,
    )
    s.blit(tablet)

    # Мягкая подсветка слева — вместо резкой границы половин.
    lit = s.layer()
    ImageDraw.Draw(lit).polygon(
        [
            (tx * SS, bottom_y * SS),
            (tx * SS, (top_y + h * 0.045) * SS),
            ((tx + tw * 0.28) * SS, top_y * SS),
            ((tx + tw * 0.44) * SS, top_y * SS),
            ((tx + tw * 0.44) * SS, bottom_y * SS),
        ],
        fill=stone_lit[:3] + (130,),
    )
    s.blit(lit, blur=7.0)

    tablet = s.layer()
    draw = ImageDraw.Draw(tablet)

    # Высеченные строки.
    for i in range(6):
        ly = top_y + h * (0.070 + i * 0.047)
        width = tw * (0.70 if i % 3 else 0.48)
        lx = tx + (tw - width) / 2
        s.bar(tablet, lx, ly, width, 4.5, rgb("#0d1117"))
        s.bar(tablet, lx, ly + 4.5, width, 1.6, rgb("#3c4653"), alpha=0.85)
    # Знак-ромб.
    draw.polygon(
        [
            (w * 0.5 * SS, (top_y + h * 0.335) * SS),
            ((w * 0.5 + w * 0.075) * SS, (top_y + h * 0.380) * SS),
            (w * 0.5 * SS, (top_y + h * 0.425) * SS),
            ((w * 0.5 - w * 0.075) * SS, (top_y + h * 0.380) * SS),
        ],
        fill=(int(0.26 * 255), int(0.40 * 255), int(0.53 * 255), 240),
    )
    # Трещина и холодная кромка света слева.
    draw.line(
        [((tx + tw * 0.62) * SS, (top_y + h * 0.06) * SS),
         ((tx + tw * 0.70) * SS, (top_y + h * 0.20) * SS),
         ((tx + tw * 0.60) * SS, (top_y + h * 0.33) * SS)],
        fill=(int(0.07 * 255), int(0.09 * 255), int(0.12 * 255), 235), width=int(1.8 * SS),
    )
    s.bar(tablet, tx, top_y + h * 0.045, 3, bottom_y - top_y - h * 0.045, rgb("#6d9ac2"), alpha=0.50)
    s.blit(tablet)

    s.glow(w * 0.5, h * 0.40, w * 0.55, rgb("#4a86b8"), 0.22, power=3.0)

    # Синие огни по бокам.
    lights = s.layer()
    for fx in (w * 0.135, w * 0.865):
        s.block(lights, fx - 10, h * 0.800, 20, 18, rgb("#1a212b"), top=0.14, side=-0.10)
        s.bar(lights, fx - 6, h * 0.784, 12, 10, rgb("#5fa8d8"))
    s.blit(lights)
    for fx in (w * 0.135, w * 0.865):
        s.glow(fx, h * 0.792, w * 0.32, rgb("#4f9ad4"), 0.50, power=2.6)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.932, amplitude=h * 0.015, color=rgb("#07080b"), step=6)
    s.blit(ground)

    s.scrim(0.69, 0.90)
    s.vignette(0.50)
    s.grain()
    s.save("Card_Rules.png")


def card_discord(w=240, h=470):
    """Кристалл связи над парящим островом: реплики, искры, фиолетовое свечение."""
    s = Scene(w, h, seed=53)
    s.sky([
        (0.00, rgb("#090616")),
        (0.50, rgb("#120b22")),
        (0.74, rgb("#190f2d")),
        (0.84, rgb("#0e0918")),
        (1.00, rgb("#090612")),
    ])

    sky_layer = s.layer()
    s.stars(sky_layer, 70, 0, h * 0.62, rgb("#cfc4ff"), size=1.6)
    s.blit(sky_layer)

    s.glow(w * 0.5, h * 0.54, w * 0.85, rgb("#523499"), 0.36)

    far = s.layer()
    s.ridge(far, base_y=h * 0.80, amplitude=h * 0.07, color=rgb("#110c1e"), step=9)
    s.blit(far, blur=0.7)

    # Парящий остров: стопка сужающихся блоков.
    island = s.layer()
    for i, (frac, height) in enumerate(((0.62, 0.030), (0.50, 0.026), (0.36, 0.024), (0.20, 0.022))):
        s.block(
            island,
            w * (0.5 - frac / 2), h * (0.735 + i * 0.026), w * frac, h * height,
            rgb("#161027"), top=0.10 if i == 0 else 0.05, side=-0.06,
        )
    s.blit(island)

    # Кристалл.
    crystal = s.layer()
    draw = ImageDraw.Draw(crystal)
    cx, cy = w * 0.5, h * 0.565
    half_w, half_h = w * 0.17, h * 0.105
    body = [
        (cx * SS, (cy - half_h) * SS),
        ((cx + half_w) * SS, cy * SS),
        (cx * SS, (cy + half_h) * SS),
        ((cx - half_w) * SS, cy * SS),
    ]
    draw.polygon(body, fill=(int(0.19 * 255), int(0.13 * 255), int(0.39 * 255), 255))
    draw.polygon(
        [body[0], body[3], body[2], (cx * SS, (cy + half_h * 0.2) * SS)],
        fill=(int(0.28 * 255), int(0.21 * 255), int(0.55 * 255), 255),
    )
    draw.line(body + [body[0]], fill=(int(0.55 * 255), int(0.45 * 255), int(0.88 * 255), 235), width=int(2.2 * SS))
    draw.line([body[0], body[2]], fill=(int(0.62 * 255), int(0.55 * 255), int(0.95 * 255), 175), width=int(1.4 * SS))
    s.blit(crystal)
    s.glow(cx, cy, w * 0.50, rgb("#7354d2"), 0.40, power=2.6)

    # Искры вокруг кристалла.
    sparks = s.layer()
    for i in range(7):
        angle = math.tau * i / 7 + 0.3
        r = w * 0.30 + s.rng.uniform(-6, 10)
        size = s.rng.uniform(3.5, 7.0)
        s.block(
            sparks, cx + math.cos(angle) * r - size / 2, cy + math.sin(angle) * r * 0.75 - size / 2,
            size, size, rgb("#523a99"), top=0.22, side=-0.10, alpha=0.9,
        )
    s.blit(sparks)

    # Реплики чата.
    bubbles = s.layer()
    for bx, by, bw, bh in ((0.10, 0.215, 0.50, 0.078), (0.42, 0.315, 0.48, 0.066)):
        x0, y0 = w * bx, h * by
        x1, y1 = w * (bx + bw), h * (by + bh)
        draw = ImageDraw.Draw(bubbles)
        draw.rectangle(
            [x0 * SS, y0 * SS, x1 * SS, y1 * SS],
            fill=(int(0.13 * 255), int(0.10 * 255), int(0.25 * 255), 245),
            outline=(int(0.40 * 255), int(0.32 * 255), int(0.70 * 255), 235),
            width=int(1.8 * SS),
        )
        tail_x = x0 + (x1 - x0) * (0.25 if bx < 0.3 else 0.7)
        draw.polygon(
            [
                (tail_x * SS, y1 * SS),
                ((tail_x + w * 0.07) * SS, y1 * SS),
                ((tail_x + w * 0.02) * SS, (y1 + h * 0.024) * SS),
            ],
            fill=(int(0.13 * 255), int(0.10 * 255), int(0.25 * 255), 245),
        )
        for d in range(3):
            dx = x0 + (x1 - x0) * (0.26 + d * 0.22)
            dy = (y0 + y1) / 2
            r = 2.6 * SS
            draw.ellipse(
                [dx * SS - r, dy * SS - r, dx * SS + r, dy * SS + r],
                fill=(int(0.58 * 255), int(0.50 * 255), int(0.92 * 255), 235),
            )
    s.blit(bubbles)

    ground = s.layer()
    s.ridge(ground, base_y=h * 0.945, amplitude=h * 0.014, color=rgb("#070511"), step=6)
    s.blit(ground)

    s.scrim(0.70, 0.90)
    s.vignette(0.50)
    s.grain()
    s.save("Card_Discord.png")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    card_minigames()
    card_news()
    card_rules()
    card_discord()


if __name__ == "__main__":
    main()
