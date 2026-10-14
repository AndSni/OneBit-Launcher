#!/usr/bin/env python3
"""Packs the bundled pixel-icon libraries into 1-bit asset files for OneBit.

  python3 -I tools/make_glyphs.py <pixelarticons package/svg dir> <kenney Tilesheet/monochrome-transparent_packed.png> <out dir>

Writes, into <out dir>:
  pixelarticons.bin / .txt  24x24 icons, 72 bytes each (row-major, 3 bytes a row,
                            high bit = left pixel); the .txt has one name per icon, same order.
  kenney.bin / .txt         16x16 tiles, 32 bytes each; the .txt has each tile's
                            index in the sheet (empty tiles are left out).

pixelarticons (MIT, github.com/halfmage/pixelarticons) are drawn on a 24x24 grid with
axis-aligned paths only, so they are rasterised exactly here (pixel centres, nonzero fill),
no anti-aliasing. Kenney's 1-Bit Pack (CC0, kenney.nl) is a tile sheet already.
"""
import os
import re
import sys

from PIL import Image


def path_polygons(d):
    """Subpaths of an SVG path using only M m H h V v L l Z z, as point lists."""
    polys, cur = [], []
    x = y = sx = sy = 0.0
    for cmd, args in re.findall(r"([MmHhVvLlZz])([^MmHhVvLlZz]*)", d):
        nums = [float(n) for n in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", args)]
        if cmd in "Mm":
            if cur:
                polys.append(cur)
            for i in range(0, len(nums), 2):
                nx, ny = nums[i], nums[i + 1]
                if cmd == "m":
                    nx, ny = x + nx, y + ny
                x, y = nx, ny
                if i == 0:
                    cur, sx, sy = [(x, y)], x, y
                else:  # implicit lineto
                    cur.append((x, y))
        elif cmd in "Hh":
            for n in nums:
                x = x + n if cmd == "h" else n
                cur.append((x, y))
        elif cmd in "Vv":
            for n in nums:
                y = y + n if cmd == "v" else n
                cur.append((x, y))
        elif cmd in "Ll":
            for i in range(0, len(nums), 2):
                nx, ny = nums[i], nums[i + 1]
                if cmd == "l":
                    nx, ny = x + nx, y + ny
                x, y = nx, ny
                cur.append((x, y))
        else:  # Z: close, and the pen returns to the subpath start
            if cur:
                polys.append(cur)
            cur, x, y = [], sx, sy
    if cur:
        polys.append(cur)
    return polys


def winding(polys, px, py):
    w = 0
    for poly in polys:
        n = len(poly)
        for i in range(n):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % n]
            if y1 <= py < y2 or y2 <= py < y1:
                xi = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
                if xi > px:
                    w += 1 if y2 > y1 else -1
    return w


def pack(bits, side):
    out = bytearray()
    for y in range(side):
        row = 0
        for x in range(side):
            row = (row << 1) | (1 if bits[y][x] else 0)
        out += row.to_bytes(side // 8, "big")
    return bytes(out)


def main(svg_dir, kenney_png, out_dir):
    names, blob = [], bytearray()
    for f in sorted(os.listdir(svg_dir)):
        if not f.endswith(".svg"):
            continue
        svg = open(os.path.join(svg_dir, f)).read()
        polys = [p for d in re.findall(r'\sd="([^"]+)"', svg) for p in path_polygons(d)]
        bits = [[winding(polys, x + 0.5, y + 0.5) != 0 for x in range(24)] for y in range(24)]
        names.append(f[:-4])
        blob += pack(bits, 24)
    open(os.path.join(out_dir, "pixelarticons.bin"), "wb").write(blob)
    open(os.path.join(out_dir, "pixelarticons.txt"), "w").write("\n".join(names) + "\n")
    print(f"pixelarticons: {len(names)} icons")

    sheet = Image.open(kenney_png).convert("RGBA")
    cols, rows = sheet.width // 16, sheet.height // 16
    idx, blob = [], bytearray()
    for t in range(cols * rows):
        tx, ty = (t % cols) * 16, (t // cols) * 16
        bits = [[sheet.getpixel((tx + x, ty + y))[3] > 127 for x in range(16)] for y in range(16)]
        if not any(any(r) for r in bits):
            continue
        idx.append(str(t))
        blob += pack(bits, 16)
    open(os.path.join(out_dir, "kenney.bin"), "wb").write(blob)
    open(os.path.join(out_dir, "kenney.txt"), "w").write("\n".join(idx) + "\n")
    print(f"kenney: {len(idx)} tiles of {cols * rows}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
