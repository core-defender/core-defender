"""블록 모델에서 같은 평면·같은 방향으로 겹치는 면(z-fighting)을 찾아 작은 요소의 면을 바깥으로 살짝 민다.

모델 생성 스크립트를 다시 돌린 뒤 실행한다. 이미 분리된 모델에는 변화가 없다.
Usage: python tools/fix-coplanar-faces.py [glob ...]   (기본: models/block/*.json)
"""
import json
import pathlib
import sys

MODELS = pathlib.Path(__file__).resolve().parent.parent / 'mod/src/main/resources/assets/coredefender/models/block'
# 16 단위 = 1블록. 눈에 띄지 않으면서 깊이 버퍼에서 구분되는 간격.
OFFSET = .03
FACES = {'north': (2, False), 'south': (2, True), 'west': (0, False), 'east': (0, True),
         'down': (1, False), 'up': (1, True)}


def rotation_key(element):
    rotation = element.get('rotation')
    if not rotation or rotation.get('angle', 0) == 0:
        return None

    return rotation['axis'], rotation['angle'], tuple(rotation.get('origin', [8, 8, 8]))


def face_rect(element, face):
    axis, positive = FACES[face]
    low = [min(element['from'][i], element['to'][i]) for i in range(3)]
    high = [max(element['from'][i], element['to'][i]) for i in range(3)]
    others = [i for i in range(3) if i != axis]
    plane = high[axis] if positive else low[axis]
    return plane, [(low[i], high[i]) for i in others]


def overlaps(a, b):
    return all(min(x[1], y[1]) - max(x[0], y[0]) > 1e-6 for x, y in zip(a, b))


def volume(element):
    size = 1.0
    for i in range(3):
        size *= abs(element['to'][i] - element['from'][i])
    return size


def push(element, face):
    axis, positive = FACES[face]
    key = 'to' if (element['to'][axis] >= element['from'][axis]) == positive else 'from'
    element[key][axis] = round(element[key][axis] + (OFFSET if positive else -OFFSET), 4)


def separate(elements):
    changed = 0
    for i in range(len(elements)):
        for j in range(i + 1, len(elements)):
            a, b = elements[i], elements[j]
            if rotation_key(a) != rotation_key(b):
                continue

            for face in set(a.get('faces', {})) & set(b.get('faces', {})):
                plane_a, rect_a = face_rect(a, face)
                plane_b, rect_b = face_rect(b, face)
                if abs(plane_a - plane_b) < 1e-4 and overlaps(rect_a, rect_b):
                    push(a if volume(a) < volume(b) else b, face)
                    changed += 1

    return changed


def main():
    patterns = sys.argv[1:] or ['*.json']
    for pattern in patterns:
        for path in sorted(MODELS.glob(pattern)):
            model = json.loads(path.read_text(encoding='utf-8'))
            changed = separate(model.get('elements', []))
            if changed:
                path.write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
                print(f'{path.name}: {changed}')


if __name__ == '__main__':
    main()
