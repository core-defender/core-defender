"""Minecraft Java block model (elements JSON) preview renderer.

Usage: python render_model.py <model.json> <out.png>

- textures in the model must point to local PNG files, relative to the model file
  (e.g. "textures": {"wood": "textures/wood.png"}). Unknown textures render magenta.
- Supports elements from/to (any range), element rotation (origin/axis/angle),
  per-face uv (default uv when omitted), face rotation (0/90/180/270), alpha cutout.
- Output: 2x2 sheet (front-left, front-right, back-left, top) with MC-like face shading.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}
VIEW = 512
SCALE = 15  # pixels per model unit


def corners(face, x0, y0, z0, x1, y1, z1):
    """TL, TR, BR, BL (viewed from outside) for each face."""
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def default_uv(face, x0, y0, z0, x1, y1, z1):
    return {
        'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        'south': [x0, 16 - y1, x1, 16 - y0],
        'west': [z0, 16 - y1, z1, 16 - y0],
        'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        'up': [x0, z0, x1, z1],
        'down': [x0, 16 - z1, x1, 16 - z0],
    }[face]


def rotate_point(p, rotation):
    if not rotation:
        return p
    ox, oy, oz = rotation.get('origin', [8, 8, 8])
    a = math.radians(rotation.get('angle', 0))
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    c, s = math.cos(a), math.sin(a)
    axis = rotation.get('axis', 'y')
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def load_textures(model, base):
    textures = {}
    refs = model.get('textures', {})

    def resolve(key, depth=0):
        value = refs.get(key)
        if value is None or depth > 8:
            return None
        if value.startswith('#'):
            return resolve(value[1:], depth + 1)
        path = os.path.join(base, value if value.endswith('.png') else value + '.png')
        if os.path.exists(path):
            return Image.open(path).convert('RGBA')
        return None

    for key in refs:
        textures[key] = resolve(key)
    return textures


def collect_quads(model, textures, yaw, pitch):
    quads = []
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(-pitch)), math.sin(math.radians(-pitch))

    def project(p):
        x, y, z = p[0] - 8, p[1] - 13, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (VIEW / 2 + x * SCALE, VIEW / 2 - y * SCALE, z)

    for element in model.get('elements', []):
        x0, y0, z0 = element['from']
        x1, y1, z1 = element['to']
        for face, info in element.get('faces', {}).items():
            tex_key = info.get('texture', '').lstrip('#')
            tex = textures.get(tex_key)
            uv = info.get('uv') or default_uv(face, x0, y0, z0, x1, y1, z1)
            u1, v1, u2, v2 = uv
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            k = (info.get('rotation', 0) // 90) % 4
            uvs = uvs[-k:] + uvs[:-k] if k else uvs
            pts = [rotate_point(p, element.get('rotation')) for p in corners(face, x0, y0, z0, x1, y1, z1)]
            shade = SHADE[face] if element.get('shade', True) else 1.0
            tw, th = (tex.size if tex else (16, 16))
            nu = max(1, min(64, round(abs(u2 - u1) / 16 * tw)))
            nv = max(1, min(64, round(abs(v2 - v1) / 16 * th)))

            def lerp3(a, b, t):
                return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))

            def lerp2(a, b, t):
                return tuple(a[i] + (b[i] - a[i]) * t for i in range(2))

            def at(s, t):
                top = lerp3(pts[0], pts[1], s)
                bottom = lerp3(pts[3], pts[2], s)
                return lerp3(top, bottom, t)

            def uv_at(s, t):
                top = lerp2(uvs[0], uvs[1], s)
                bottom = lerp2(uvs[3], uvs[2], s)
                return lerp2(top, bottom, t)

            for i in range(nu):
                for j in range(nv):
                    s0, s1, t0, t1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                    u, v = uv_at((s0 + s1) / 2, (t0 + t1) / 2)
                    if tex:
                        px = min(tw - 1, max(0, int(u / 16 * tw)))
                        py = min(th - 1, max(0, int(v / 16 * th)))
                        r, g, b, a = tex.getpixel((px, py))
                        if a < 128:
                            continue
                    else:
                        r, g, b = 255, 0, 255
                    color = (int(r * shade), int(g * shade), int(b * shade))
                    poly3 = [at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)]
                    poly = [project(p) for p in poly3]
                    depth = sum(p[2] for p in poly) / 4
                    quads.append((depth, [(p[0], p[1]) for p in poly], color))
    quads.sort(key=lambda q: -q[0])
    return quads


def render_view(model, textures, yaw, pitch, label):
    image = Image.new('RGB', (VIEW, VIEW), (40, 44, 52))
    draw = ImageDraw.Draw(image)
    # 16x16 grid floor guide (one block footprint)
    for quad_depth, poly, color in collect_quads(model, textures, yaw, pitch):
        draw.polygon(poly, fill=color, outline=color)
    draw.text((8, 8), label, fill=(220, 220, 220))
    return image


def main():
    model_path, out_path = sys.argv[1], sys.argv[2]
    model = json.load(open(model_path, encoding='utf-8'))
    textures = load_textures(model, os.path.dirname(os.path.abspath(model_path)))
    views = [(-35, 30, 'front-left (north side)'), (35, 30, 'front-right'),
             (145, 30, 'back'), (-35, 75, 'top')]
    sheet = Image.new('RGB', (VIEW * 2, VIEW * 2))
    for index, (yaw, pitch, label) in enumerate(views):
        sheet.paste(render_view(model, textures, yaw, pitch, label), ((index % 2) * VIEW, (index // 2) * VIEW))
    sheet.save(out_path)
    print('saved', out_path)


if __name__ == '__main__':
    main()
