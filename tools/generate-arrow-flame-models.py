"""Rebuild arrow/flame assets and display sheets: python tools/generate-arrow-flame-models.py.

Requires Pillow. The checked-in reference preserves the selected timber ballista design.
All dimensions are model texels; moving models use the existing (8, 20.8, 8) pivot.
"""
import copy
import json
import math
import os
from pathlib import Path
import random

from PIL import Image, ImageDraw
import sys

sys.dont_write_bytecode = True
import render_arrow_turret_model as preview

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUT = ROOT / 'outputs/turret-models/arrow'
OUT.mkdir(parents=True, exist_ok=True)
REFERENCE = json.loads(Path(__file__).with_name('arrow_turret_reference.json').read_text())
PALETTES = {
    'oak': [(115,73,37),(143,96,49),(128,82,40),(91,55,29)],
    'heartwood': [(76,47,28),(101,65,35),(88,54,30),(61,38,26)],
    'bow': [(152,108,56),(176,131,72),(140,92,44),(111,71,34)],
    'iron': [(61,69,73),(107,119,121),(71,80,83),(49,57,63)],
    'steel': [(147,160,162),(195,204,199),(131,145,150),(165,176,174)],
    'rope': [(184,160,112),(204,185,140),(161,133,89)],
    'feather': [(201,192,166),(229,222,198),(163,162,148)],
    'leather': [(83,45,29),(109,61,35),(67,35,25)],
    'ember': [(222,103,24),(255,195,65),(151,50,18)],
    'lamp': [(220,220,220),(255,255,255),(180,180,180)],
}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def box(name, a, b, tex):
    dx, dy, dz = [b[i]-a[i] for i in range(3)]
    dims = dict(north=(dx,dy), south=(dx,dy), west=(dz,dy), east=(dz,dy), up=(dx,dz), down=(dx,dz))
    return dict(name=name, **{'from': a, 'to': b}, faces={f: {'uv':[0,0,w,h], 'texture':'#'+tex} for f,(w,h) in dims.items()})


def shifted(elements, offset):
    result = copy.deepcopy(elements)
    for e in result:
        for key in ('from','to'):
            e[key] = [round(v+offset[i], 5) for i,v in enumerate(e[key])]
        if 'rotation' in e:
            e['rotation']['origin'] = [round(v+offset[i],5) for i,v in enumerate(e['rotation']['origin'])]
    return result


def texture(prefix, name, palette):
    rng = random.Random(41 + list(PALETTES).index(name))
    im = Image.new('RGB', (16,16))
    wood = name in ('oak','heartwood','bow')
    for y in range(16):
        for x in range(16):
            im.putpixel((x,y), palette[(x//3 + (y in (5,12) and x%4==0))%len(palette)] if wood else rng.choices(palette, [7]+[1]*(len(palette)-1))[0])
    d = ImageDraw.Draw(im)
    if wood:
        for x,y,n in [(2,1,6),(7,7,6),(12,2,8),(4,11,4)]:
            d.line((x,y,x,y+n), fill=palette[-1])
    elif name in ('iron','steel'):
        d.line((0,0,15,0), fill=palette[1])
        d.line((0,1,0,15), fill=palette[1])
    path = ASSETS / 'textures/block' / f'{prefix}_{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def model(elements, textures):
    for e in elements:
        assert all(-16 <= v <= 32 for key in ('from','to') for v in e[key]), e['name']
        assert e.get('rotation', {}).get('angle', 0) in (-45,-22.5,0,22.5,45)
        assert all(0 <= v <= 16 for face in e['faces'].values() for v in face['uv']), e['name']
    return {'parent':'minecraft:block/block', 'ambientocclusion':True, 'textures':textures, 'elements':elements}


for prefix, block in [('arrow_turret','basic_turret'), ('flame_turret','flame_turret')]:
    for name, palette in PALETTES.items():
        texture(prefix, name, palette)
    textures = {name:f'coredefender:block/{prefix}_{name}' for name in PALETTES}
    textures['particle'] = textures['oak']
    base = copy.deepcopy(REFERENCE['elements'][:20])
    # The selected low pedestal is extended to the existing gameplay pivot.
    for e in base:
        for key in ('from','to'):
            e[key][1] *= 1.5
        if 'rotation' in e:
            e['rotation']['origin'][1] *= 1.5
        for face, data in e['faces'].items():
            if face not in ('up','down'):
                data['uv'][2 if data.get('rotation', 0) in (90,270) else 3] *= 1.5
    base.append(box('status lamp frame',[5.5,3,4.7],[10.5,6,5.5],'iron'))
    write(ASSETS / f'models/block/{block}.json', model(base,textures))
    parts = {}
    parts['bearing'] = [box('yaw bearing',[-3,-2.8,-3],[3,-1.3,3],'iron')]
    parts['lamp'] = [box('status lens',[6,3.5,4.5],[10,5.5,4.7],'lamp')]
    for face in parts['lamp'][0]['faces'].values():
        face['tintindex'] = 0
    if prefix == 'arrow_turret':
        head = []
        for e in REFERENCE['elements'][21:]:
            if any(n in e['name'] for n in ('bearing','bow limb','tip iron','bowstring','arrow','feather')):
                continue
            e = copy.deepcopy(e)
            # Reference faces north; the game fires along local +Z.
            a,b = e['from'],e['to']
            e['from'] = [a[0]-8,a[1]-14.5,8-b[2]]
            e['to'] = [b[0]-8,b[1]-14.5,8-a[2]]
            e['faces']['north'],e['faces']['south'] = e['faces']['south'],e['faces']['north']
            head.append(e)
        parts['head'] = head
        for side in ('left','right'):
            limbs=[]
            for source in REFERENCE['elements']:
                if source['name'] not in ('swept wooden bow limb','bow tip iron cap'):
                    continue
                if (source['from'][0] < 8) != (side == 'left'):
                    continue
                e=copy.deepcopy(source)
                a,b=e['from'],e['to']
                e['from']=[a[0]-8,a[1]-14.5,8-b[2]]
                e['to']=[b[0]-8,b[1]-14.5,8-a[2]]
                e['rotation']['origin']=[0,0,4]
                e['rotation']['angle'] *= -1
                limbs.append(e)
            parts[side]=limbs
        parts['bolt'] = [box('shaft',[-.224,-.224,-6.4],[.224,.224,8.48],'bow'),
                         box('broadhead',[-.7,-.35,8],[.7,.35,9.6],'steel'),
                         box('point',[-.25,-.25,9.6],[.25,.25,10.4],'steel'),
                         box('horizontal fletching',[-1.12,-.144,-6.4],[1.12,.144,-4.32],'feather'),
                         box('vertical fletching',[-.144,-.96,-6.4],[.144,.96,-4.32],'feather')]
        # Unit beams are stretched only longitudinally by the existing draw animation.
        for part, tex, width, length in [('string','rope',.288,12)]:
            parts[part] = [box(part,[-width/2,-width/2,0],[width/2,width/2,length],tex)]
    else:
        parts['head'] = [box('timber cradle',[-5,-1.3,-6],[5,0,4],'heartwood'),
                         box('iron reservoir',[-4,-.8,-5],[4,5,3],'iron'),
                         box('reservoir top',[-3,5,-4],[3,6,2],'steel'),
                         box('filler cap',[-1,6,-2],[1,7,0],'iron'),
                         box('leather bellows',[-3,0,-7],[3,4,-5],'leather'),
                         box('nozzle pipe',[-1.7,.2,3],[1.7,3.6,10.4],'iron')]
        for z in (-4,1):
            parts['head'].append(box('tank band',[-4.2,-1,z],[4.2,5.2,z+1],'steel'))
        for x in (-4.5,3.5):
            parts['head'].append(box('wood side brace',[x,-1,-5],[x+1,3.5,2],'oak'))
        for a,b in [([-2.5,-.6,10],[2.5,.4,12.3]),([-2.5,3.4,10],[2.5,4.4,12.3]),([-2.5,.4,10],[-1.5,3.4,12.3]),([1.5,.4,10],[2.5,3.4,12.3])]:
            parts['head'].append(box('open nozzle rim',a,b,'iron'))
        parts['head'].append(box('recessed nozzle',[-1.5,.4,10],[1.5,3.4,10.1],'leather'))
        parts['flash'] = [box('ignition core',[-1.2,.6,12.4],[1.2,3,15.4],'ember')]
    for name, elements in parts.items():
        write(ASSETS / f'models/block/{prefix}_{name}.json', model(elements,textures))
    assembled = base + parts['lamp'] + shifted(parts['bearing']+parts['head'],[8,20.8,8])
    if prefix == 'arrow_turret':
        assembled += shifted(parts['bolt'],[8,22.4,8])
        assembled += shifted(parts['left']+parts['right'],[8,20.8,8])
        # At full draw each string half is exactly 45 degrees, matching the renderer.
        tip_x = math.cos(math.radians(22.5))*7.75
        tip_z = 4-math.sin(math.radians(22.5))*7.75
        for side in (-1,1):
            x,z=8+side*tip_x,8+tip_z
            cord=box('drawn string',[x-.144,21.616,z-tip_x*math.sqrt(2)],[x+.144,21.904,z],'rope')
            cord['rotation']={'origin':[x,21.76,z],'axis':'y','angle':side*45}
            assembled.append(cord)
    item = model(assembled,textures)
    item['display'] = {'gui':{'rotation':[30,225,0],'translation':[0,-3,0],'scale':[.55,.55,.55]},
                       'ground':{'translation':[0,2,0],'scale':[.3,.3,.3]},
                       'fixed':{'rotation':[0,180,0],'translation':[0,-3,0],'scale':[.5,.5,.5]}}
    write(ASSETS / f'models/item/{block}.json', item)
    display = copy.deepcopy(item)
    display['textures'] = {k:Path(os.path.relpath(ASSETS/'textures'/ (v.split(':')[1]+'.png'),OUT)).as_posix() for k,v in textures.items()}
    write(OUT / f'{prefix}_assembled.json', display)
    tex = preview.load_textures(display, str(OUT))
    sheet=Image.new('RGB',(1024,1024))
    # Taller gameplay pedestal needs more framing than the original low concept.
    preview.SCALE=12
    for i,(yaw,pitch,label) in enumerate([(-35,30,'north'),(35,30,'north east'),(145,30,'firing side'),(-35,75,'top')]):
        sheet.paste(preview.render_view(display,tex,yaw,pitch,label),((i%2)*512,(i//2)*512))
    sheet.save(OUT / f'{prefix}_assembled.png')
    print(prefix, 'generated')
