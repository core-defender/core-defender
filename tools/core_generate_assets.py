"""Generate core assets. Requires Pillow; optional --renderer points to render_model.py.

python tools/core_generate_assets.py --renderer <shared/render_model.py>
"""
import argparse
import importlib.util
import json
import math
import os
from pathlib import Path
import random

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUT = ROOT / 'outputs/models/core'
TEXTURES = ASSETS / 'textures/block'


def generate():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rng = random.Random(41)
    palettes = {
        'wood': [(115, 73, 37), (143, 96, 49), (128, 82, 40), (91, 55, 29)],
        'iron': [(61, 69, 73), (107, 119, 121), (71, 80, 83), (49, 57, 63)],
        'steel': [(147, 160, 162), (195, 204, 199), (131, 145, 150), (165, 176, 174)],
        'energy': [(33, 125, 133), (64, 187, 179), (120, 231, 208), (207, 255, 225)],
    }
    for name, palette in palettes.items():
        im = Image.new('RGB', (16, 16))
        for y in range(16):
            for x in range(16):
                if name == 'wood':
                    c = palette[(x // 3 + (1 if y in (5, 12) and x % 4 == 0 else 0)) % 4]
                elif name == 'energy':
                    c = palette[max(0, [0, 1, 3, 2, 1, 0][x % 6] - (1 if y > 4 else 0))]
                else:
                    c = rng.choices(palette, weights=[7, 1, 1, 1])[0]
                im.putpixel((x, y), c)
        d = ImageDraw.Draw(im)
        if name == 'wood':
            for x, y, length in [(2, 1, 6), (7, 7, 6), (12, 2, 8), (4, 11, 4)]:
                d.line((x, y, x, y + length), fill=palette[-1])
        elif name != 'energy':
            d.line((0, 0, 15, 0), fill=palette[1])
            d.line((0, 0, 0, 15), fill=palette[1])
        im.save(TEXTURES / f'core_{name}.png')

    elements = []

    def box(name, a, b, texture, rotation=None, glow=False):
        dx, dy, dz = [b[i] - a[i] for i in range(3)]
        dims = {'north': (dx, dy), 'south': (dx, dy), 'west': (dz, dy),
                'east': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
        faces = {face: {'texture': '#' + texture, 'uv': [0, 0, w, h]}
                 for face, (w, h) in dims.items()}
        e = {'name': name, 'from': a, 'to': b, 'faces': faces}
        if rotation:
            e['rotation'] = {'origin': [8, 11, 8], 'axis': rotation[0], 'angle': rotation[1]}
        if glow:
            e['shade'] = False
            e['neoforge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
        elements.append(e)

    box('iron foundation', [0, 0, 0], [16, 1, 16], 'iron')
    box('oak reliquary pedestal', [1, 1, 1], [15, 5, 15], 'wood')
    box('upper iron rim', [0, 5, 0], [16, 6, 16], 'iron')
    for x in (0, 13):
        for z in (0, 13):
            box('corner iron strap', [x, 1, z], [x + 3, 5, z + 3], 'iron')
            box('corner rivet', [x + 1, 6, z + 1], [x + 2, 7, z + 2], 'steel')
    box('crystal socket', [4, 6, 4], [12, 7, 12], 'steel')
    box('socket inset', [5, 7, 5], [11, 8, 11], 'iron')
    # 보석은 CoreRenderer 가 띄워 회전시키므로 별도 모델로 분리한다.
    base_count = len(elements)
    box('diamond heart', [5, 8, 5], [11, 14, 11], 'energy', ('y', 45), True)
    box('crystal crown', [6, 14, 6], [10, 15, 10], 'energy', ('y', 45), True)
    box('crystal tip', [7, 15, 7], [9, 16, 9], 'energy', ('y', 45), True)
    crystal = elements[base_count:]
    del elements[base_count:]
    for x in (2, 12):
        for z in (2, 12):
            box('oak guardian pillar', [x, 6, z], [x + 2, 10, z + 2], 'wood')
            box('pillar cap', [x, 10, z], [x + 2, 11, z + 2], 'iron')
    # Rune strokes are geometry, so their individual faces can be full-bright.
    for side in range(4):
        for a, b in [([7, 2, .75], [8, 5, 1]), ([8, 3, .75], [10, 4, 1]),
                     ([9, 4, .75], [10, 5, 1])]:
            for _ in range(side):
                a, b = [16 - b[2], a[1], a[0]], [16 - a[2], b[1], b[0]]
            box('ward rune', a, b, 'energy', glow=True)

    model = {'parent': 'minecraft:block/block', 'ambientocclusion': True,
             'textures': {k: f'coredefender:block/core_{k}' for k in palettes},
             'elements': elements}
    model['textures']['particle'] = '#wood'
    for e in elements:
        assert all(0 <= v <= 16 for p in ('from', 'to') for v in e[p])
        if 'rotation' in e:
            assert e['rotation']['angle'] in (-45, -22.5, 0, 22.5, 45)
            for x in (e['from'][0], e['to'][0]):
                for z in (e['from'][2], e['to'][2]):
                    a = math.radians(e['rotation']['angle'])
                    assert 0 <= 8 + (x - 8) * math.cos(a) - (z - 8) * math.sin(a) <= 16
                    assert 0 <= 8 + (x - 8) * math.sin(a) + (z - 8) * math.cos(a) <= 16
    (ASSETS / 'models/block/core.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    (ASSETS / 'models/block/core_crystal.json').write_text(json.dumps({
        'textures': {'energy': 'coredefender:block/core_energy', 'particle': '#energy'},
        'elements': crystal,
    }, indent=2) + '\n', encoding='utf-8')
    (ASSETS / 'models/item/core.json').write_text(json.dumps({
        'parent': 'minecraft:block/block',
        'textures': model['textures'],
        'elements': elements + crystal,
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.65, .65, .65]}}
    }, indent=2) + '\n', encoding='utf-8')
    return {**model, 'elements': elements + crystal}


def preview(model, renderer):
    OUT.mkdir(parents=True, exist_ok=True)
    model['textures'] = {k: os.path.relpath(TEXTURES / (v.split('/')[-1] + '.png'), OUT).replace('\\', '/')
                         if not v.startswith('#') else v for k, v in model['textures'].items()}
    (OUT / 'core_preview_model.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    spec = importlib.util.spec_from_file_location('core_preview_renderer', renderer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    textures = module.load_textures(model, str(OUT))
    sheet = Image.new('RGB', (1024, 1024))
    for i, (name, yaw) in enumerate([('north', -35), ('east', 55), ('south', 145), ('west', 235)]):
        im = module.render_view(model, textures, yaw, 30, 'CORE / ' + name)
        im.save(OUT / f'core_{name}.png')
        sheet.paste(im, ((i % 2) * 512, (i // 2) * 512))
    sheet.save(OUT / 'core_preview.png')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--renderer', type=Path)
    args = parser.parse_args()
    model = generate()
    if args.renderer:
        preview(model, args.renderer)
    print(f'Generated core model: {len(model["elements"])} elements, four 16x16 textures')
