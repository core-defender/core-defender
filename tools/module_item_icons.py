"""Hand-pixelled item assets. Run: python tools/module_item_icons.py (Pillow)."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUT = ROOT / 'outputs/models/items'
INK = '#242a2d'
WOOD = '#734925'
LIGHT = '#c3ccc7'
TIERS = {1: ('#6b7779', '#a5b0ae'), 2: ('#a47a40', '#d4b873'), 3: ('#688b92', '#b4d4d2')}
# Seven-pixel emblems leave room for the separate three-pixel tier inscription.
GLYPHS = {
    'aim': ['..###..', '.#...#.', '#..#..#', '#.###.#', '#..#..#', '.#...#.', '..###..'],
    'blast': ['#..#..#', '.#...#.', '..###..', '#.###.#', '..###..', '.#...#.', '#..#..#'],
    'burn': ['...#...', '...##..', '.#.##..', '.####..', '####.#.', '.#####.', '..###..'],
    'cluster': ['..###..', '..###..', '...#...', '..#.#..', '##...##', '##.#.##', '...#...'],
    'damage': ['....###', '...####', '..####.', '.####..', '..##...', '.#..#..', '#......'],
    'multishot': ['#..#..#', '#######', '#..#..#', '...#...', '.#.#.#.', '.#.#.#.', '.#.#.#.'],
    'pierce': ['..#.#..', '..#.#..', '#####.#', '#######', '#####.#', '..#.#..', '..#.#..'],
    'range': ['..###..', '.#...#.', '#..#..#', '#..####', '#.....#', '.#...#.', '..###..'],
    'ricochet': ['....###', '.....##', '....#.#', '...#...', '#.#....', '.#.....', '#######'],
    'speed': ['##..##.', '.##..##', '..##..#', '...##..', '..##..#', '.##..##', '##..##.'],
}


def canvas():
    im = Image.new('RGBA', (16, 16))
    return im, ImageDraw.Draw(im)


def module(name):
    im, d = canvas()
    chassis = name == 'module_chassis'
    tier = 1 if chassis else int(name[-1])
    rim, shine = TIERS[tier]
    d.polygon([(2, 1), (13, 1), (14, 2), (14, 13), (13, 14), (2, 14), (1, 13), (1, 2)], fill=INK)
    d.rectangle((2, 2, 13, 13), fill=rim)
    d.line((2, 2, 12, 2), fill=shine)
    d.line((2, 2, 2, 12), fill=shine)
    d.rectangle((3, 3, 12, 11), fill='#3d261a')
    d.rectangle((4, 3, 11, 10), fill=WOOD)
    for x in (5, 8, 11):
        d.line((x, 3, x, 10), fill='#5b371d')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=LIGHT)
    if chassis:
        d.rectangle((5, 5, 10, 9), outline=rim)
        d.point((5, 5), fill=shine)
        d.line((5, 12, 10, 12), fill=INK)
    else:
        kind = name[7:-2]
        legacy = kind in ('stone_range', 'flame_range')
        glyph = GLYPHS['range' if legacy else kind]
        color = '#e0a653' if kind == 'burn' else '#e5dec6'
        for y, row in enumerate(glyph):
            for x, pixel in enumerate(row):
                if pixel == '#':
                    d.point((x + 4, y + 4), fill=INK)
        for y, row in enumerate(glyph):
            for x, pixel in enumerate(row):
                if pixel == '#':
                    d.point((x + 4, y + 3), fill=color)
        d.rectangle((4, 11, 11, 13), fill=INK)
        for x in range(8 - tier, 8 + tier, 2):
            d.line((x, 11, x, 13), fill=shine)
        if legacy:
            d.line((12, 8, 12, 10), fill='#a5b0ae' if kind == 'stone_range' else '#d28c48')
    return im


def special(name):
    im, d = canvas()
    if name == 'seed_oil_bottle':
        d.rectangle((6, 0, 9, 2), fill=INK)
        d.rectangle((6, 1, 9, 2), fill='#98713e')
        d.polygon([(5, 3), (10, 3), (10, 5), (13, 7), (13, 13), (11, 15), (4, 15), (2, 13), (2, 7), (5, 5)], fill=INK)
        d.polygon([(6, 3), (9, 3), (9, 6), (12, 8), (12, 12), (10, 14), (5, 14), (3, 12), (3, 8), (6, 6)], fill='#72908a')
        d.rectangle((4, 8, 11, 12), fill='#b99437')
        d.rectangle((5, 13, 10, 13), fill='#806223')
        d.line((4, 8, 11, 8), fill='#e4c967')
        d.line((4, 7, 4, 11), fill='#d1e1d2')
        d.rectangle((6, 9, 9, 12), fill='#dfd2a6')
        d.line((7, 11, 8, 10), fill='#617043')
    elif name == 'range_analyzer':
        d.line((5, 10, 2, 14), fill=INK, width=4)
        d.line((5, 10, 2, 14), fill=WOOD, width=2)
        d.polygon([(6, 1), (11, 1), (14, 4), (14, 9), (11, 12), (6, 12), (3, 9), (3, 4)], fill=INK)
        d.ellipse((4, 2, 13, 11), fill='#a47a40')
        d.ellipse((5, 3, 12, 10), fill='#435c5c')
        d.line((6, 4, 8, 4), fill='#9dbfba')
        d.line((8, 4, 8, 9), fill=LIGHT)
        d.line((6, 7, 11, 7), fill=LIGHT)
        d.point((8, 7), fill='#d4b873')
    elif name.startswith('supply_upgrade_'):
        tier = int(name[-1])
        rim, shine = [('#a47a40', '#d4b873'), ('#59676c', '#a5b0ae'), ('#688b92', '#b4d4d2')][tier - 1]
        d.rectangle((2, 3, 13, 12), fill=INK)
        d.rectangle((3, 4, 12, 11), fill=WOOD)
        for x in (4, 11):
            d.rectangle((x, 1, x + 1, 14), fill=INK)
            d.line((x, 2, x, 13), fill=rim)
            d.point((x, 2), fill=shine)
        if tier == 1:
            d.rectangle((6, 3, 9, 12), fill=rim)
            d.rectangle((3, 6, 12, 9), fill=rim)
            d.rectangle((5, 5, 10, 10), fill=shine)
            d.rectangle((7, 7, 8, 8), fill=INK)
        elif tier == 2:
            d.rectangle((6, 4, 9, 11), fill=shine)
            d.rectangle((7, 5, 8, 10), fill=rim)
            for y in (5, 7, 9):
                d.line((3, y, 4, y), fill='#b18351')
                d.line((11, y, 12, y), fill='#b18351')
        else:
            d.polygon([(7, 3), (10, 6), (10, 9), (7, 12), (5, 9), (5, 6)], fill=rim)
            d.rectangle((6, 5, 9, 10), fill=shine)
            d.rectangle((7, 6, 8, 9), fill='#45686c')
            d.point((7, 6), fill='#e0ede1')
        d.rectangle((5, 12, 10, 14), fill=INK)
        for x in range(8 - tier, 8 + tier, 2):
            d.line((x, 12, x, 14), fill=shine)
    else:
        d.polygon([(2, 1), (13, 1), (13, 3), (12, 3), (12, 12), (14, 12), (14, 15), (3, 15), (3, 13), (2, 13)], fill=INK)
        d.rectangle((3, 2, 11, 12), fill='#cdb98a')
        d.line((3, 2, 11, 2), fill='#ede0b8')
        d.rectangle((4, 13, 13, 14), fill='#98713e')
        d.line((4, 13, 12, 13), fill='#e0c995')
        d.line([(4, 9), (4, 5), (5, 5), (5, 6), (7, 6), (7, 5), (8, 5), (8, 6), (10, 6), (10, 9), (4, 9)], fill='#53696a')
        d.line((7, 7, 7, 9), fill='#53696a')
        d.line((5, 11, 10, 11), fill='#8a734c')
    return im


def main():
    models = ASSETS / 'models/item'
    textures = ASSETS / 'textures/item'
    names = sorted(p.stem for p in models.glob('module_*.json')) + [
        'range_analyzer', 'seed_oil_bottle', 'supply_upgrade_1',
        'supply_upgrade_2', 'supply_upgrade_3', 'wall_expansion_plan']
    textures.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGB', (1000, 80 + ((len(names) + 5) // 6) * 150), '#262b31')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    draw.text((20, 16), 'CORE DEFENDER / ITEM ATLAS / 16 x 16', fill='#e5dec6', font=font)
    draw.text((20, 38), 'I: iron   II: brass   III: pale steel | Native size + 5x nearest-neighbor', fill='#a5b0ae', font=font)
    for i, name in enumerate(names):
        im = module(name) if name.startswith('module_') else special(name)
        im.save(textures / f'{name}.png')
        model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'coredefender:item/{name}'}}
        (models / f'{name}.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
        x, y = 12 + (i % 6) * 166, 80 + (i // 6) * 150
        draw.rectangle((x, y, x + 153, y + 137), fill='#30363d')
        sheet.paste(im.resize((80, 80), Image.Resampling.NEAREST), (x + 36, y + 8), im.resize((80, 80), Image.Resampling.NEAREST))
        sheet.paste(im, (x + 9, y + 72), im)
        label = name.removeprefix('module_') if name.startswith('module_') else name
        draw.text((x + 5, y + 99), label, fill='#e5dec6', font=font)
        draw.text((x + 5, y + 116), 'module' if name.startswith('module_') else 'item', fill='#96a3a8', font=font)
    sheet.save(OUT / 'module_item_icons_sheet.png')
    print(f'Generated {len(names)} models and 16x16 textures; {OUT / "module_item_icons_sheet.png"}')


if __name__ == '__main__':
    main()
