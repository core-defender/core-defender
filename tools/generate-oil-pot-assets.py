"""포도 덩굴 화분의 종자유 압착 내용물 텍스처와 모델을 생성한다.

Usage: python tools/generate-oil-pot-assets.py
"""
import json
import pathlib
import random

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'mod/src/main/resources/assets/coredefender'
TEXTURES = ROOT / 'textures/block'
MODELS = ROOT / 'models/block'

# 씨앗 더미 -> 압착 중 으깨진 반죽 -> 위에 고이는 기름
PALETTES = {
    'oil_pot_seeds': [(122, 104, 58), (146, 126, 70), (98, 84, 46), (168, 150, 92), (80, 70, 40)],
    'oil_pot_mash': [(150, 118, 48), (176, 140, 58), (128, 98, 40), (196, 162, 76), (110, 84, 34)],
    'oil_pot_oil': [(214, 168, 44), (226, 184, 60), (200, 152, 36), (238, 204, 96)],
}


def texture(name, palette):
    rng = random.Random(name)
    image = Image.new('RGBA', (16, 16))
    alpha = 210 if name == 'oil_pot_oil' else 255
    for y in range(16):
        for x in range(16):
            if name == 'oil_pot_oil':
                # 잔물결 띠
                index = 3 if (x + 2 * y) % 11 == 0 else rng.choice((0, 0, 1, 2))
            else:
                index = rng.choices(range(len(palette)), weights=(5, 3, 3, 1, 1)[:len(palette)])[0]
            image.putpixel((x, y), palette[index] + (alpha,))
    image.save(TEXTURES / (name + '.png'))


def model(name):
    face = {'texture': '#all'}
    data = {
        'textures': {'all': 'coredefender:block/' + name, 'particle': 'coredefender:block/' + name},
        'elements': [{
            'from': [0, 0, 0],
            'to': [16, 16, 16],
            'faces': {side: dict(face) for side in ('north', 'south', 'east', 'west', 'up', 'down')},
        }],
    }
    (MODELS / (name + '.json')).write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    for name, palette in PALETTES.items():
        texture(name, palette)
        model(name)


if __name__ == '__main__':
    main()
