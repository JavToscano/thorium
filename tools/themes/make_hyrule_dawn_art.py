#!/usr/bin/env python3
"""Draws the artwork of the "Hyrule Dawn" theme: an original fantasy-adventure scene and tab icons.

It is inspired by the mood of classic adventure games (dawn over green hills, a far-off castle, a
sword resting in its pedestal) but copies no artwork, logo or symbol from any game.
Output goes to app/src/main/assets/themes/hyrule-dawn/{images,icons}.
Usage: python3 tools/themes/make_hyrule_dawn_art.py   (needs Pillow and numpy)
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "themes", "hyrule-dawn")


def blur(img, r):
    return img.filter(ImageFilter.GaussianBlur(r))


def gradient(stops):
    ys = np.linspace(0, 1, H)
    cols = np.stack([np.interp(ys, [s[0] for s in stops], [s[1][c] for s in stops]) for c in range(3)], axis=1)
    return Image.fromarray(np.repeat(cols[:, None, :], W, axis=1).astype("uint8"))


def noise_ridge(base, amp, seed, octaves=5):
    rnd = random.Random(seed)
    ph = [rnd.random() * 6.28 for _ in range(octaves)]
    xs = np.arange(0, W + 1, 4)
    ys = np.zeros_like(xs, dtype=float)
    for k in range(octaves):
        ys += (amp / (1.7 ** k)) * np.sin(2 * math.pi * (1.8 ** k) * xs / W + ph[k])
    return xs, base + ys


def fill(layer, xs, ys, color):
    ImageDraw.Draw(layer).polygon(list(zip(xs.tolist(), ys.tolist())) + [(W, H), (0, H)], fill=color)


def haze(y, color, strength, spread=80):
    a = np.zeros((H, W, 4), dtype="uint8")
    a[..., 0], a[..., 1], a[..., 2] = color
    a[..., 3] = np.clip(np.exp(-((np.arange(H) - y) / spread) ** 2) * strength * 3.0, 0, 255)[:, None]
    return blur(Image.fromarray(a), 12)


def make_sky():
    rnd = random.Random(4)
    img = gradient([(0, (28, 78, 98)), (0.35, (70, 140, 150)), (0.6, (170, 205, 170)), (0.78, (248, 222, 150)), (1, (255, 190, 110))]).convert("RGBA")
    # rising sun behind the hills with long soft rays
    sx, sy = 1280, 690
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r, a in ((520, 40), (340, 70), (190, 130), (96, 230)):
        gd.ellipse([sx - r, sy - r, sx + r, sy + r], fill=(255, 226, 140, a))
    img = Image.alpha_composite(img, blur(glow, 60))
    rays = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rays)
    for i in range(16):
        a0 = math.radians(200 + i * 8.5 + rnd.uniform(-2, 2))
        w = math.radians(2.2)
        L = 1700
        rd.polygon([(sx, sy), (sx + math.cos(a0 - w) * L, sy + math.sin(a0 - w) * L), (sx + math.cos(a0 + w) * L, sy + math.sin(a0 + w) * L)], fill=(255, 240, 180, 26))
    img = Image.alpha_composite(img, blur(rays, 7))
    sun = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sun).ellipse([sx - 62, sy - 62, sx + 62, sy + 62], fill=(255, 250, 225, 255))
    img = Image.alpha_composite(img, blur(sun, 2.2))
    # layered clouds, lit warm from below
    clouds = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(clouds)
    for _ in range(26):
        x, y = rnd.randint(-100, W), rnd.randint(60, 560)
        w = rnd.randint(260, 700)
        for k in range(7):
            ox, oy = rnd.randint(-w // 3, w // 3), rnd.randint(-20, 14)
            r = rnd.randint(40, 110)
            cd.ellipse([x + ox - r, y + oy - r * 0.45, x + ox + r, y + oy + r * 0.45], fill=(255, 246, 228, rnd.randint(34, 70)))
    img = Image.alpha_composite(img, blur(clouds, 18))
    warm = Image.new("RGBA", (W, H), (255, 190, 120, 0))
    return img.convert("RGB")


def castle(draw, x, y, s, color):
    """A far-off fantasy castle silhouette: curtain wall, towers with cone roofs, flags."""
    def tower(cx, w, h, roof):
        draw.rectangle([cx - w / 2, y - h, cx + w / 2, y], fill=color)
        draw.polygon([(cx - w / 2 - 3 * s, y - h), (cx, y - h - roof), (cx + w / 2 + 3 * s, y - h)], fill=color)
        draw.line([cx, y - h - roof, cx, y - h - roof - 16 * s], fill=color, width=max(1, int(2 * s)))
        draw.polygon([(cx, y - h - roof - 16 * s), (cx + 14 * s, y - h - roof - 11 * s), (cx, y - h - roof - 6 * s)], fill=color)
    draw.rectangle([x - 150 * s, y - 52 * s, x + 150 * s, y], fill=color)
    for i in range(-7, 8):  # battlements
        draw.rectangle([x + i * 20 * s - 6 * s, y - 62 * s, x + i * 20 * s + 6 * s, y - 52 * s], fill=color)
    tower(x - 120 * s, 30 * s, 110 * s, 52 * s)
    tower(x + 120 * s, 30 * s, 110 * s, 52 * s)
    tower(x - 50 * s, 26 * s, 150 * s, 62 * s)
    tower(x + 52 * s, 26 * s, 138 * s, 58 * s)
    tower(x, 40 * s, 210 * s, 84 * s)


def make_mountains():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    far = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    xs, ys = noise_ridge(H * 0.67, 46, 2, 4)
    for i, x in enumerate(xs):  # a plateau for the castle on the left
        d = abs(x - 520) / 330
        if d < 1:
            ys[i] = min(ys[i], H * 0.70 - 60 * (1 - d ** 2.5))
    fill(far, xs, ys, (78, 128, 150, 255))
    cd = ImageDraw.Draw(far)
    castle(cd, 520, int(np.interp(520, xs, ys)) + 6, 0.9, (66, 112, 138, 255))
    img = Image.alpha_composite(img, far)
    img = Image.alpha_composite(img, haze(H * 0.68, (255, 235, 190), 36))
    mid = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    xs, ys = noise_ridge(H * 0.76, 40, 6, 5)
    fill(mid, xs, ys, (60, 132, 98, 255))
    img = Image.alpha_composite(img, mid)
    img = Image.alpha_composite(img, haze(H * 0.76, (255, 230, 170), 30))
    near = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    xs, ys = noise_ridge(H * 0.86, 34, 9, 5)
    fill(near, xs, ys, (30, 94, 66, 255))
    nd = ImageDraw.Draw(near)
    rnd = random.Random(8)
    for _ in range(150):  # soft lighter tufts on the hill
        x = rnd.randint(0, W)
        y = int(np.interp(x, xs, ys)) + rnd.randint(6, 90)
        nd.ellipse([x - 22, y - 5, x + 22, y + 5], fill=(54, 128, 84, 120))
    img = Image.alpha_composite(img, near)
    return img


def tree(draw, x, y, s, trunk, leaf, rnd):
    draw.polygon([(x - 7 * s, y), (x - 3 * s, y - 70 * s), (x + 3 * s, y - 70 * s), (x + 7 * s, y)], fill=trunk)
    for _ in range(14):
        cx, cy, r = x + rnd.randint(-46, 46) * s, y - 92 * s + rnd.randint(-34, 26) * s, rnd.randint(24, 44) * s
        draw.ellipse([cx - r, cy - r * 0.8, cx + r, cy + r * 0.8], fill=leaf)


def make_front():
    rnd = random.Random(31)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # a great tree on the right, cut by the edge
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    tree(d, 1740, 1010, 3.2, (28, 22, 24, 255), (22, 74, 52, 255), rnd)
    tree(d, 1740, 1010, 3.2, (28, 22, 24, 255), (30, 96, 64, 255), random.Random(5))
    img = Image.alpha_composite(img, lay)
    # sword resting in a stone pedestal, lower left
    sd = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(sd)
    px, py = 1090, 900
    g.polygon([(px - 120, 1080), (px - 96, py + 30), (px - 56, py - 6), (px + 56, py - 6), (px + 96, py + 30), (px + 120, 1080)], fill=(52, 58, 60, 255))
    g.polygon([(px - 96, py + 30), (px - 56, py - 6), (px - 20, py - 6), (px - 40, py + 60)], fill=(74, 82, 84, 255))
    g.polygon([(px - 15, py - 8), (px + 15, py - 8), (px + 11, py - 330), (px, py - 372), (px - 11, py - 330)], fill=(214, 228, 232, 255))  # blade
    g.line([px, py - 330, px, py - 20], fill=(150, 176, 186, 255), width=3)
    g.rounded_rectangle([px - 62, py - 40, px + 62, py - 22], radius=8, fill=(222, 178, 70, 255))  # guard
    g.polygon([(px - 62, py - 40), (px - 74, py - 52), (px - 50, py - 40)], fill=(222, 178, 70, 255))
    g.polygon([(px + 62, py - 40), (px + 74, py - 52), (px + 50, py - 40)], fill=(222, 178, 70, 255))
    g.rounded_rectangle([px - 9, py - 22, px + 9, py + 22], radius=4, fill=(56, 90, 120, 255))  # grip
    g.ellipse([px - 15, py + 20, px + 15, py + 50], fill=(222, 178, 70, 255))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gg = ImageDraw.Draw(glow)
    gg.ellipse([px - 150, py - 400, px + 150, py - 40], fill=(180, 240, 255, 55))
    img = Image.alpha_composite(img, blur(glow, 40))
    img = Image.alpha_composite(img, sd)
    # grass blades along the bottom edge
    gr = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gr)
    for _ in range(420):
        x = rnd.randint(-10, W + 10)
        h = rnd.randint(38, 120)
        lean = rnd.randint(-22, 22)
        c = (rnd.randint(14, 30), rnd.randint(60, 104), rnd.randint(34, 62), 255)
        gd.polygon([(x - 6, H + 4), (x + lean, H - h), (x + 6, H + 4)], fill=c)
    img = Image.alpha_composite(img, gr)
    # little flowers
    fl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fl)
    for _ in range(46):
        x, y = rnd.randint(40, W - 40), rnd.randint(H - 90, H - 22)
        col = rnd.choice([(255, 236, 160), (255, 255, 240), (150, 214, 255)])
        for a in range(5):
            ang = a * 1.2566
            fd.ellipse([x + math.cos(ang) * 6 - 4, y + math.sin(ang) * 6 - 4, x + math.cos(ang) * 6 + 4, y + math.sin(ang) * 6 + 4], fill=col + (255,))
        fd.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(240, 170, 60, 255))
    return Image.alpha_composite(img, fl)


# ---------------------------------------------------------------- icons (white on transparent)
SS, SIZE = 4, 128


def icon(fn):
    big = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    fn(ImageDraw.Draw(big), SIZE * SS / 128)
    out = Image.new("RGBA", (SIZE, SIZE), (255, 255, 255, 0))
    out.putalpha(big.resize((SIZE, SIZE), Image.LANCZOS))
    return out


def tower_icon(d, u):
    d.rectangle([40 * u, 52 * u, 88 * u, 112 * u], fill=255)
    d.polygon([(34 * u, 52 * u), (64 * u, 12 * u), (94 * u, 52 * u)], fill=255)
    for i in range(4):
        d.rectangle([(36 + i * 14) * u, 44 * u, (42 + i * 14) * u, 52 * u], fill=255)
    d.rectangle([56 * u, 80 * u, 72 * u, 112 * u], fill=0)
    d.rectangle([58 * u, 64 * u, 70 * u, 74 * u], fill=0)


def shield_icon(d, u):
    d.polygon([(20 * u, 18 * u), (64 * u, 8 * u), (108 * u, 18 * u), (108 * u, 62 * u), (64 * u, 118 * u), (20 * u, 62 * u)], fill=255)
    d.polygon([(64 * u, 22 * u), (92 * u, 29 * u), (92 * u, 60 * u), (64 * u, 98 * u), (36 * u, 60 * u), (36 * u, 29 * u)], fill=0)
    d.rectangle([60 * u, 34 * u, 68 * u, 84 * u], fill=255)
    d.rectangle([48 * u, 46 * u, 80 * u, 54 * u], fill=255)


def heart_icon(d, u):
    d.ellipse([14 * u, 22 * u, 66 * u, 74 * u], fill=255)
    d.ellipse([62 * u, 22 * u, 114 * u, 74 * u], fill=255)
    d.polygon([(18 * u, 56 * u), (110 * u, 56 * u), (64 * u, 114 * u)], fill=255)


def download_icon(d, u):
    d.rectangle([54 * u, 14 * u, 74 * u, 62 * u], fill=255)
    d.polygon([(30 * u, 58 * u), (98 * u, 58 * u), (64 * u, 96 * u)], fill=255)
    d.rounded_rectangle([18 * u, 100 * u, 110 * u, 114 * u], radius=6 * u, fill=255)


def gear_icon(d, u):
    cx = cy = 64 * u
    for i in range(8):
        a = i * math.pi / 4
        c, s = math.cos(a), math.sin(a)
        w, r0, r1 = 11 * u, 36 * u, 56 * u
        d.polygon([(cx + c * r0 - s * w, cy + s * r0 + c * w), (cx + c * r1 - s * w * .8, cy + s * r1 + c * w * .8),
                   (cx + c * r1 + s * w * .8, cy + s * r1 - c * w * .8), (cx + c * r0 + s * w, cy + s * r0 - c * w)], fill=255)
    d.ellipse([cx - 42 * u, cy - 42 * u, cx + 42 * u, cy + 42 * u], fill=255)
    d.ellipse([cx - 17 * u, cy - 17 * u, cx + 17 * u, cy + 17 * u], fill=0)


def main():
    for sub in ("images", "icons"):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    for name, fn in (("bg_sky", make_sky), ("bg_hills", make_mountains), ("bg_front", make_front)):
        path = os.path.join(OUT, "images", name + ".webp")
        fn().save(path, "WEBP", quality=88, method=6)
        print(name, os.path.getsize(path) // 1024, "KB")
    for name, fn in (("home", tower_icon), ("systems", shield_icon), ("favorites", heart_icon), ("downloads", download_icon), ("settings", gear_icon)):
        icon(fn).save(os.path.join(OUT, "icons", name + ".png"), optimize=True)


if __name__ == "__main__":
    main()
