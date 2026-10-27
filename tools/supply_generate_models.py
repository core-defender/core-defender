"""Generate supply assets. Requires Pillow; optional --renderer path/to/render_model.py."""
import argparse
import importlib.util
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUTPUT = ROOT / 'outputs/models/supply'
WOOD = [(115, 73, 37), (143, 96, 49), (128, 82, 40), (91, 55, 29)]
IRON = [(61, 69, 73), (107, 119, 121), (71, 80, 83), (49, 57, 63)]


def surface(seed):
    rng = random.Random(seed)
    im = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            color = WOOD[(x // 4) % 4]
            delta = rng.choice([-3, 0, 0, 0, 3])
            im.putpixel((x, y), tuple(v + delta for v in color))
    d = ImageDraw.Draw(im)
    for x in (3, 7, 11):
        d.line((x, 0, x, 15), fill=WOOD[3])
    for x, y in ((1, 3), (5, 9), (9, 2), (14, 10)):
        d.line((x, y, x, y + 3), fill=WOOD[3])
    return im


def border(im):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=IRON[3], width=2)
    d.rectangle((1, 1, 14, 14), outline=IRON[0])
    d.line((0, 0, 15, 0), fill=IRON[1])
    for x in (1, 14):
        for y in (1, 14):
            d.point((x, y), fill=(165, 176, 174))


def textures(kind):
    accent = (184, 140, 67) if kind == 'sender' else (83, 153, 144)
    light = (224, 191, 112) if kind == 'sender' else (143, 198, 179)
    side = surface(41)
    border(side)
    d = ImageDraw.Draw(side)
    # Iron escutcheon, a contrasting arrow, and a separate dispatch/arrival bar.
    d.rectangle((4, 3, 11, 12), fill=IRON[3])
    d.rectangle((5, 4, 10, 11), fill=IRON[0])
    arrow = [(7, 4), (8, 4), (8, 5), (9, 5), (9, 6), (10, 6),
             (10, 7), (8, 7), (8, 10), (7, 10), (7, 7), (5, 7),
             (5, 6), (6, 6), (6, 5), (7, 5)]
    if kind == 'receiver':
        arrow = [(x, 14 - y) for x, y in arrow]
    d.polygon(arrow, fill=accent)
    d.point((7, 4 if kind == 'sender' else 10), fill=light)
    d.line((6, 12 if kind == 'sender' else 3, 9, 12 if kind == 'sender' else 3), fill=accent)
    top = surface(43)
    border(top)
    d = ImageDraw.Draw(top)
    # Octagonal portal seal; outward/inward teeth distinguish the pair from above.
    d.polygon([(5, 3), (10, 3), (12, 5), (12, 10), (10, 12),
               (5, 12), (3, 10), (3, 5)], fill=IRON[3])
    d.rectangle((5, 5, 10, 10), outline=IRON[1])
    d.rectangle((7, 7, 8, 8), fill=accent)
    for rotation in range(4):
        mark = Image.new('RGBA', (16, 16))
        md = ImageDraw.Draw(mark)
        if kind == 'sender':
            md.line((7, 3, 8, 3), fill=light)
            md.line((6, 4, 9, 4), fill=accent)
        else:
            md.line((6, 3, 9, 3), fill=accent)
            md.line((7, 4, 8, 4), fill=light)
        top.paste(mark.rotate(90 * rotation), (0, 0), mark.rotate(90 * rotation))
    bottom = surface(44)
    border(bottom)
    for name, im in [('side', side), ('top', top), ('bottom', bottom)]:
        im.save(ASSETS / f'textures/block/supply_{kind}_{name}.png')


def model(kind):
    elements = []

    def box(name, lo, hi, faces=None):
        x, y, z = lo
        X, Y, Z = hi
        uvs = {'north': [16-X, 16-Y, 16-x, 16-y], 'south': [x, 16-Y, X, 16-y],
               'west': [z, 16-Y, Z, 16-y], 'east': [16-Z, 16-Y, 16-z, 16-y],
               'up': [x, z, X, Z], 'down': [x, 16-Z, X, 16-z]}
        data = {}
        for face in faces or uvs:
            tex = 'top' if face == 'up' else 'bottom' if face == 'down' else 'side'
            data[face] = {'texture': '#' + tex, 'uv': uvs[face]}
            axis, value = {'north': (2, 0), 'south': (2, 16), 'west': (0, 0),
                           'east': (0, 16), 'up': (1, 16), 'down': (1, 0)}[face]
            if (hi if value else lo)[axis] == value:
                data[face]['cullface'] = face
        elements.append({'name': name, 'from': lo, 'to': hi, 'faces': data})

    box('lower iron rim', [0, 0, 0], [16, 2, 16])
    box('upper iron rim and portal seal', [0, 14, 0], [16, 16, 16])
    for x in (0, 14):
        for z in (0, 14):
            box('corner iron upright', [x, 2, z], [x+2, 14, z+2])
    box('solid timber casing', [.25, 2, .25], [15.75, 14, 15.75],
        ['north', 'south', 'west', 'east'])
    return {'parent': 'minecraft:block/block', 'ambientocclusion': True,
            'textures': {**{t: f'coredefender:block/supply_{kind}_{t}'
                           for t in ('side', 'top', 'bottom')}, 'particle': '#side'},
            'elements': elements}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--renderer', type=Path)
    args = parser.parse_args()
    (ASSETS / 'textures/block').mkdir(parents=True, exist_ok=True)
    for kind in ('sender', 'receiver'):
        textures(kind)
        data = model(kind)
        (ASSETS / f'models/block/supply_{kind}.json').write_text(
            json.dumps(data, indent=2) + '\n', encoding='utf-8')
        item = {'parent': f'coredefender:block/supply_{kind}',
                'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0],
                                    'scale': [.625, .625, .625]}}}
        (ASSETS / f'models/item/supply_{kind}.json').write_text(
            json.dumps(item, indent=2) + '\n', encoding='utf-8')
        if args.renderer:
            OUTPUT.mkdir(parents=True, exist_ok=True)
            spec = importlib.util.spec_from_file_location('supply_preview_renderer', args.renderer)
            renderer = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(renderer)
            preview = json.loads(json.dumps(data))
            for key in ('side', 'top', 'bottom'):
                preview['textures'][key] = (ASSETS / f'textures/block/supply_{kind}_{key}.png').as_posix()
            loaded = renderer.load_textures(preview, str(OUTPUT))
            sheet = Image.new('RGB', (1024, 1024))
            for i, (direction, yaw) in enumerate([('north', 0), ('east', 90), ('south', 180), ('west', 270)]):
                view = renderer.render_view(preview, loaded, yaw, 30, f'{kind} / {direction}')
                view.save(OUTPUT / f'supply_{kind}_{direction}.png')
                angled = renderer.render_view(preview, loaded, yaw - 35, 30,
                                               f'{kind} / {direction} three-quarter')
                sheet.paste(angled, ((i % 2) * 512, (i // 2) * 512))
            sheet.save(OUTPUT / f'supply_{kind}_preview.png')
    print('Generated supply models and 16x16 textures.')


if __name__ == '__main__':
    main()
