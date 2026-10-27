"""Generate only oil turret assets. Requires Python 3 and Pillow.

python tools/generate-oil-turret-assets.py [--renderer /path/to/render_model.py]
The preview models use paths relative to outputs/turret-models/oil.
"""
import argparse
import copy
import importlib.util
import json
import os
from pathlib import Path
import random

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUT = ROOT / 'outputs/turret-models/oil'
TEXTURES = ASSETS / 'textures/block'
PALETTES = {
    'oak': [(115, 73, 37), (143, 96, 49), (128, 82, 40), (91, 55, 29)],
    'heartwood': [(76, 47, 28), (101, 65, 35), (88, 54, 30), (61, 38, 26)],
    'iron': [(61, 69, 73), (107, 119, 121), (71, 80, 83), (49, 57, 63)],
    'steel': [(147, 160, 162), (195, 204, 199), (131, 145, 150), (165, 176, 174)],
    'oil': [(39, 30, 18), (85, 65, 28), (51, 40, 19), (33, 27, 19)],
    'red': [(139, 39, 29), (222, 87, 49), (111, 30, 25), (158, 48, 30)],
    'amber': [(185, 119, 35), (244, 186, 72), (147, 85, 23), (203, 141, 41)],
    'green': [(49, 116, 72), (103, 179, 106), (36, 85, 60), (65, 139, 79)],
    'gray': [(70, 76, 77), (110, 116, 113), (52, 60, 63), (83, 89, 88)],
}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def texture(name, palette):
    rng = random.Random(41)
    im = Image.new('RGB', (16, 16))
    wood = name in ('oak', 'heartwood')
    for y in range(16):
        for x in range(16):
            if wood:
                color = palette[(x // 3 + (1 if y in (5, 12) and x % 4 == 0 else 0)) % 4]
                delta = rng.choice([-3, 0, 0, 0, 3])
                color = tuple(v + delta for v in color)
            else:
                color = rng.choices(palette, weights=[7, 1, 1, 1])[0]
            im.putpixel((x, y), color)
    draw = ImageDraw.Draw(im)
    if wood:
        for x, y, length in [(2, 1, 6), (7, 7, 6), (12, 2, 8), (4, 11, 4)]:
            draw.line((x, y, x, y + length), fill=palette[-1])
    elif name in ('iron', 'steel'):
        draw.line((0, 0, 15, 0), fill=palette[1])
        draw.line((0, 1, 0, 15), fill=palette[1])
    elif name == 'oil':
        for a, b in [((3, 4), (6, 4)), ((10, 10), (12, 10)), ((8, 7), (9, 7))]:
            draw.line((*a, *b), fill=palette[1])
    im.save(TEXTURES / f'oil_turret_{name}.png')


def box(dst, name, a, b, tex, grain=None):
    dx, dy, dz = [round(b[i] - a[i], 4) for i in range(3)]
    dims = dict(north=(dx, dy), south=(dx, dy), west=(dz, dy), east=(dz, dy), up=(dx, dz), down=(dx, dz))
    faces = {}
    for face, (w, h) in dims.items():
        rotate = (grain == 'x' and face in ('north', 'south', 'up', 'down')) or (grain == 'z' and face in ('east', 'west'))
        if rotate:
            w, h = h, w
        faces[face] = {'uv': [0, 0, w, h], 'texture': '#' + tex}
        if rotate:
            faces[face]['rotation'] = 90
    dst.append({'name': name, 'from': a, 'to': b, 'faces': faces})


def model(elements):
    return {'parent': 'minecraft:block/block', 'ambientocclusion': True,
            'textures': {'particle': '#iron', **{n: f'coredefender:block/oil_turret_{n}' for n in PALETTES}},
            'elements': elements}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--renderer', type=Path)
    args = parser.parse_args()
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, palette in PALETTES.items():
        texture(name, palette)
    base, yoke, pot, oil = [], [], [], []
    for x in (1, 12):
        box(base, 'timber runner', [x, 0, 1], [x + 3, 2, 15], 'heartwood', 'z')
        for z in (1, 13):
            box(base, 'iron shoe', [x - .15, 0, z], [x + 3.15, 2.2, z + 2], 'iron')
            box(base, 'shoe rivet', [x + 1, 2.2, z + .5], [x + 2, 2.5, z + 1.5], 'steel')
    for z in (4, 10):
        box(base, 'cross sill', [1, 2, z], [15, 4, z + 2], 'oak', 'x')
    box(base, 'timber pedestal', [5, 4, 5], [11, 11, 11], 'oak')
    for y in (4, 9):
        box(base, 'column collar', [4.75, y, 4.75], [11.25, y + 1, 11.25], 'iron')
        box(base, 'collar rivet', [7.5, y + .1, 4.5], [8.5, y + .9, 4.75], 'steel')
    box(base, 'bearing seat', [4, 11, 4], [12, 12, 12], 'heartwood')
    box(base, 'fixed bearing', [5, 12, 5], [11, 14.3, 11], 'iron')
    box(base, 'status bezel', [5.8, 3, 1.6], [10.2, 6, 3.1], 'iron')
    # Moving parts are authored around zero: the renderer supplies (8, 20.8, 8).
    box(yoke, 'rotating saddle', [-5, -6.5, -3], [5, -5.5, 3], 'heartwood', 'x')
    for side in (-1, 1):
        x = -8 if side < 0 else 6.75
        box(yoke, 'trunnion support', [x, -5.5, -1.5], [x + 1.25, 1.5, 1.5], 'iron')
        box(yoke, 'axle cap', [x - .15, -.7, -.7], [x + 1.4, .7, .7], 'steel')
    box(pot, 'heavy cauldron floor', [-5, -2.4, -4.5], [5, -.9, 4.5], 'iron')
    for x in (-6, 4.75):
        box(pot, 'cauldron side', [x, -1.5, -5], [x + 1.25, 5, 5], 'iron')
    box(pot, 'cauldron back', [-4.75, -1.5, -5], [4.75, 5, -3.75], 'iron')
    box(pot, 'low pouring wall', [-4.75, -1.5, 3.75], [4.75, 2.5, 5], 'iron')
    for x in (-6.25, 4.5):
        box(pot, 'rolled side rim', [x, 4.5, -5.25], [x + 1.75, 5.5, 5.25], 'steel')
    box(pot, 'rolled rear rim', [-4.5, 4.5, -5.25], [4.5, 5.5, -3.75], 'steel')
    box(pot, 'pouring lip', [-3, 2, 5], [3, 2.75, 8], 'iron')
    for x in (-3, 2.25):
        box(pot, 'spout edge', [x, 2.75, 4.75], [x + .75, 3.5, 8], 'steel')
    for x in (-6.3, 6):
        for z in (-3, 2):
            box(pot, 'cauldron rivet', [x, 1, z], [x + .3, 2, z + 1], 'steel')
    box(oil, 'oil surface', [-4.7, 1.8, -3.7], [4.7, 2.05, 3.7], 'oil')
    parts = {'oil_turret': base, 'oil_turret_yoke': yoke, 'oil_turret_pot': pot, 'oil_turret_oil': oil}
    for color in ('red', 'amber', 'green', 'gray'):
        lamp = []
        box(lamp, 'status inset', [6.1, 3.2, 1.5], [9.9, 5.8, 1.7], color)
        parts['oil_turret_lamp_' + color] = lamp
    for name, elements in parts.items():
        write(ASSETS / 'models/block' / (name + '.json'), model(elements))
    assembled = copy.deepcopy(base + parts['oil_turret_lamp_green'])
    for element in copy.deepcopy(yoke + pot + oil):
        for key in ('from', 'to'):
            element[key] = [round(v + shift, 4) for v, shift in zip(element[key], [8, 20.8, 8])]
        assembled.append(element)
    item = model(assembled)
    item['display'] = {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': [.48, .48, .48]},
        'ground': {'translation': [0, 2, 0], 'scale': [.25, .25, .25]},
        'fixed': {'scale': [.5, .5, .5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.3, .3, .3]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.35, .35, .35]},
    }
    write(ASSETS / 'models/item/oil_turret.json', item)
    for elements in [*parts.values(), assembled]:
        for element in elements:
            assert all(-16 <= v <= 32 for key in ('from', 'to') for v in element[key])
            for face in element['faces'].values():
                assert all(0 <= v <= 16 for v in face['uv'])
    preview = model(assembled)
    preview['textures'].update({n: Path(os.path.relpath(TEXTURES / f'oil_turret_{n}.png', OUT)).as_posix() for n in PALETTES})
    write(OUT / 'assembled.json', preview)
    pouring = copy.deepcopy(preview)
    for element in pouring['elements'][-len(pot + oil):]:
        element['rotation'] = {'origin': [8, 20.8, 8], 'axis': 'x', 'angle': 45}
    write(OUT / 'pouring.json', pouring)
    if args.renderer:
        spec = importlib.util.spec_from_file_location('oil_preview_renderer', args.renderer)
        renderer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(renderer)
        # The supplied renderer frames a one-block object; this turret is taller.
        renderer.SCALE = 11
        for name, scene in [('preview', preview), ('pouring', pouring)]:
            textures = renderer.load_textures(scene, str(OUT))
            sheet = Image.new('RGB', (1024, 1024))
            for i, (yaw, pitch, label) in enumerate([(-35, 30, 'north-left'), (35, 30, 'north-right'),
                                                    (145, 30, 'pouring side (+Z)'), (-35, 75, 'top')]):
                sheet.paste(renderer.render_view(scene, textures, yaw, pitch, label), ((i % 2) * 512, (i // 2) * 512))
            sheet.save(OUT / (name + '.png'))
    print(f'Generated {len(parts)} block models, item model and {len(PALETTES)} textures; validated bounds and UV density.')


if __name__ == '__main__':
    main()
