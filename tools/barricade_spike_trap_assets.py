"""Generate killzone assets with Pillow; optionally render using --renderer PATH.

Run from any directory: python tools/barricade_spike_trap_assets.py
The supplied Minecraft JSON preview renderer is imported without modification.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import random

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUT = ROOT / 'outputs/models/killzone'
PALETTES = {
    'wood': [(115, 73, 37), (143, 96, 49), (128, 82, 40), (91, 55, 29)],
    'end': [(115, 73, 37), (143, 96, 49), (128, 82, 40), (91, 55, 29)],
    'iron': [(61, 69, 73), (107, 119, 121), (71, 80, 83), (49, 57, 63)],
    'steel': [(147, 160, 162), (195, 204, 199), (131, 145, 150), (165, 176, 174)],
}


def texture(target, kind):
    rng = random.Random(41)
    palette = PALETTES[kind]
    im = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            if kind == 'wood':
                color = palette[(x // 3 + (y in (5, 12) and x % 4 == 0)) % 4]
                delta = rng.choice([-3, 0, 0, 0, 3])
                color = tuple(v + delta for v in color)
            elif kind == 'end':
                color = palette[(max(abs(x - 7), abs(y - 7)) // 2) % 4]
            else:
                color = rng.choices(palette, weights=[7, 1, 1, 1])[0]
            im.putpixel((x, y), color)
    draw = ImageDraw.Draw(im)
    if kind == 'wood':
        for x, y, length in [(2, 1, 6), (7, 7, 6), (12, 2, 8), (4, 11, 4)]:
            draw.line((x, y, x, y + length), fill=palette[-1])
    if kind in ('iron', 'steel'):
        draw.line((0, 0, 15, 0), fill=palette[1])
        draw.line((0, 1, 0, 15), fill=palette[1])
    path = ASSETS / 'textures/block' / f'{target}_{kind}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def box(elements, name, a, b, material, grain=None, rotation=None):
    dx, dy, dz = [b[i] - a[i] for i in range(3)]
    dims = {'north': (dx, dy), 'south': (dx, dy), 'west': (dz, dy),
            'east': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
    faces = {}
    for face, (w, h) in dims.items():
        turn = (grain == 'x' and face in ('north', 'south', 'up', 'down')) or (
            grain == 'z' and face in ('east', 'west'))
        if turn:
            w, h = h, w
        faces[face] = {'uv': [0, 0, w, h], 'texture': '#' + material}
        if turn:
            faces[face]['rotation'] = 90
        if material == 'wood' and grain == 'y' and face in ('up', 'down'):
            faces[face]['texture'] = '#end'
    element = {'name': name, 'from': a, 'to': b, 'faces': faces}
    if rotation:
        element['rotation'] = rotation
    elements.append(element)


def barricade():
    elements = []
    # Packed timbers fill the block silhouette to communicate full-cube collision.
    for x in (0, 4, 8, 12):
        box(elements, 'packed upright timber', [x + .15, 0, .5],
            [x + 3.85, 15 if x in (4, 8) else 16, 15.5], 'wood', 'y')
    for y in (3, 11):
        for z in (0, 15.5):
            box(elements, 'forged cross strap', [0, y, z], [16, y + 2, z + .5], 'iron')
            for x in (1, 5, 10, 14):
                near, far = (-.2, 0) if z == 0 else (16, 16.2)
                box(elements, 'strap rivet', [x, y + .5, near], [x + 1, y + 1.5, far], 'steel')
        for x in (0, 15.5):
            box(elements, 'return strap', [x, y, .5], [x + .5, y + 2, 15.5], 'iron')
    for z in (-.5, 16):
        box(elements, 'diagonal timber brace', [7, 1.5, z], [9, 14.5, z + .5],
            'wood', 'y', {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45})
    return elements


def spike_trap():
    elements = []
    for z in (0, 4, 8, 12):
        box(elements, 'low timber plate', [0, 0, z + .125], [16, 1, z + 3.875], 'wood', 'x')
    for x in (0, 15):
        box(elements, 'iron edge binding', [x, 0, 0], [x + 1, 1.25, 16], 'iron')
    for z in (0, 15):
        box(elements, 'iron end binding', [1, 0, z], [15, 1.25, z + 1], 'iron')
    for x in (2.5, 6, 10, 13.5):
        for z in (2.5, 6, 10, 13.5):
            box(elements, 'tooth socket', [x - 1, 1, z - 1], [x + 1, 1.4, z + 1], 'iron')
            # A 45-degree square-section tooth gives a real pointed ridge within y=3.
            box(elements, 'sharpened iron tooth', [x - .7, 1.3, z - .65],
                [x + .7, 2.7, z + .65], 'steel', rotation={
                    'origin': [x, 2, z], 'axis': 'z', 'angle': 45})
    return elements


def generate(renderer_path=None):
    renderer = None
    if renderer_path:
        spec = importlib.util.spec_from_file_location('killzone_preview', renderer_path)
        renderer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(renderer)
        OUT.mkdir(parents=True, exist_ok=True)
    for target, elements in [('barricade', barricade()), ('spike_trap', spike_trap())]:
        kinds = ('wood', 'end', 'iron', 'steel') if target == 'barricade' else ('wood', 'iron', 'steel')
        for kind in kinds:
            texture(target, kind)
        textures = {k: f'coredefender:block/{target}_{k}' for k in kinds}
        textures['particle'] = textures['wood']
        model = {'parent': 'minecraft:block/block', 'ambientocclusion': True,
                 'textures': textures, 'elements': elements}
        for element in elements:
            assert all(-16 <= v <= 32 for v in element['from'] + element['to'])
            assert element.get('rotation', {}).get('angle', 0) in (-45, -22.5, 0, 22.5, 45)
            for face in element['faces'].values():
                assert all(0 <= v <= 16 for v in face['uv'])
        (ASSETS / 'models/block' / f'{target}.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
        item = {'parent': f'coredefender:block/{target}', 'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.625, .625, .625]}}}
        if target == 'spike_trap':
            # 3픽셀 높이 판이라 기본 손 위치에서는 옆면만 보인다. 윗면이 시점을 향하도록 세운다.
            hand = {'rotation': [70, 0, 0], 'translation': [0, 4, 0], 'scale': [.5, .5, .5]}
            item['display'].update(firstperson_righthand=hand, firstperson_lefthand=hand,
                                   thirdperson_righthand={'rotation': [75, 45, 0], 'translation': [0, 3, 2], 'scale': [.375, .375, .375]},
                                   thirdperson_lefthand={'rotation': [75, 45, 0], 'translation': [0, 3, 2], 'scale': [.375, .375, .375]})
        (ASSETS / 'models/item' / f'{target}.json').write_text(json.dumps(item, indent=2) + '\n', encoding='utf-8')
        if renderer:
            preview = dict(model, textures={k: str(ASSETS / 'textures/block' / f'{target}_{k}.png') for k in kinds})
            loaded = renderer.load_textures(preview, str(OUT))
            assert all(t is not None for t in loaded.values())
            sheet = Image.new('RGB', (1024, 1024))
            for i, (direction, yaw) in enumerate([('north', -35), ('east', 55), ('south', 145), ('west', 235)]):
                view = renderer.render_view(preview, loaded, yaw, 30, f'{target} / {direction}')
                view.save(OUT / f'{target}_{direction}.png')
                sheet.paste(view, ((i % 2) * 512, (i // 2) * 512))
            sheet.save(OUT / f'{target}_4views.png')
        print(f'{target}: {len(elements)} elements, {len(kinds)} textures')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--renderer', type=Path)
    generate(parser.parse_args().renderer)
