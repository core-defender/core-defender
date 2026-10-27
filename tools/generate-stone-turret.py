from pathlib import Path
from PIL import Image, ImageDraw
import json, random, copy, argparse, importlib.util, os
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/coredefender'
OUT = ROOT / 'outputs/turret-models/stone'
PALETTES = {
 'oak': [(115,73,37),(143,96,49),(128,82,40),(91,55,29)],
 'heartwood': [(76,47,28),(101,65,35),(88,54,30),(61,38,26)],
 'iron': [(61,69,73),(107,119,121),(71,80,83),(49,57,63)],
 'steel': [(147,160,162),(195,204,199),(131,145,150),(165,176,174)],
 'rope': [(184,160,112),(204,185,140),(161,133,89)],
 'stone': [(106,109,106),(139,141,132),(92,96,94),(119,121,115)],
 'ready': [(55,151,91),(92,201,118),(37,109,69)],
 'warning': [(184,55,39),(232,93,59),(122,39,32)],
 'amp': [(207,151,43),(242,194,77),(156,102,32)],
 'stopped': [(89,94,97),(128,134,133),(61,66,70)]}

def box(dst, name, a, b, tex, grain=None, rotation=None):
    dx,dy,dz = [round(b[i]-a[i],4) for i in range(3)]
    faces = {}
    for face,(w,h) in {'north':(dx,dy),'south':(dx,dy),'west':(dz,dy),'east':(dz,dy),'up':(dx,dz),'down':(dx,dz)}.items():
        turn = (grain == 'z' and face in ('east','west')) or (grain == 'x' and face in ('north','south','up','down'))
        if turn: w,h = h,w
        assert max(w,h) <= 16, (name,w,h)
        faces[face] = {'uv':[0,0,w,h], 'texture':'#'+tex}
        if turn: faces[face]['rotation'] = 90
    element = {'name':name,'from':a,'to':b,'faces':faces}
    if rotation: element['rotation'] = rotation
    dst.append(element)

def shifted(elements, offset):
    result = copy.deepcopy(elements)
    for e in result:
        for key in ('from','to'):
            e[key] = [round(e[key][i]+offset[i],4) for i in range(3)]
        if 'rotation' in e:
            e['rotation']['origin'] = [e['rotation']['origin'][i]+offset[i] for i in range(3)]
    return result

def main():
    parser = argparse.ArgumentParser(description='Regenerate only stone turret assets (requires Pillow).')
    parser.add_argument('--renderer', type=Path, help='Optional shared/render_model.py path for preview sheets')
    args = parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    texdir = ASSETS/'textures/block'
    texdir.mkdir(parents=True,exist_ok=True)
    for name,palette in PALETTES.items():
        rng = random.Random(41)
        im = Image.new('RGB',(16,16))
        for y in range(16):
            for x in range(16):
                if name in ('oak','heartwood'):
                    c = palette[(x//3+(1 if y in (5,12) and x%4==0 else 0))%len(palette)]
                    delta = rng.choice([-3,0,0,0,3])
                    c = tuple(v+delta for v in c)
                else: c = rng.choices(palette,weights=[7]+[1]*(len(palette)-1))[0]
                im.putpixel((x,y),c)
        draw = ImageDraw.Draw(im)
        if name in ('oak','heartwood'):
            for x,y,length in [(2,1,6),(7,7,6),(12,2,8),(4,11,4)]: draw.line((x,y,x,y+length),fill=palette[-1])
        elif name in ('iron','steel'):
            draw.line((0,0,15,0),fill=palette[1]); draw.line((0,1,0,15),fill=palette[1])
        elif name == 'stone':
            draw.line((1,4,7,4,10,7,15,7),fill=palette[2]); draw.line((6,5,6,12,3,15),fill=palette[2])
        im.save(texdir/f'stone_turret_{name}.png')
    base=[]; bearing=[]; head=[]; arm=[]; ammo=[]
    for x in (1,12):
        box(base,'ground runner',[x,0,0],[x+3,2,16],'heartwood','z')
        for z in (0,14):
            box(base,'iron shoe',[x-.125,0,z],[x+3.125,2.25,z+2],'iron')
            box(base,'shoe rivet',[x+1,2.25,z+.5],[x+2,2.5,z+1.5],'steel')
    for z in (3,10): box(base,'cross sill',[1,2,z],[15,4,z+3],'oak','x')
    box(base,'timber pedestal',[5,4,5],[11,16,11],'oak')
    for x,angle in [(3.5,-22.5),(12.5,22.5)]:
        box(base,'splayed brace',[x-1,3,7],[x+1,13,9],'heartwood',rotation={'origin':[x,3,8],'axis':'z','angle':angle})
    for y in (5,13):
        box(base,'pedestal collar',[4.75,y,4.75],[11.25,y+1.25,11.25],'iron')
        box(base,'collar rivet',[7.5,y+.125,4.5],[8.5,y+1.125,4.75],'steel')
    box(base,'bearing seat',[3,16,3],[13,17.5,13],'heartwood')
    box(base,'fixed bearing',[4,17.5,4],[12,18.5,12],'iron')
    box(base,'lamp frame',[6,3,.25],[10,6,1.25],'iron')
    # Moving part coordinates are relative to the existing (0.5, 1.3, 0.5) pivot.
    box(bearing,'rotating bearing',[-3.5,-2.3,-3.5],[3.5,-1.3,3.5],'steel')
    for x in (-5,3):
        box(head,'carriage rail',[x,-1.3,-7],[x+2,.7,8],'heartwood','z')
        box(head,'axle upright',[x,-.5,-1.5],[x+2,3,1.5],'oak')
        box(head,'axle cap',[x-.125,2,-1.625],[x+2.125,3.25,1.625],'iron')
        box(head,'axle pin',[x-.25,.5,-.5],[x+2.25,1.5,.5],'steel')
    for z in (-6,6): box(head,'carriage crossbar',[-5,-2.3,z],[5,-1.3,z+2],'oak','x')
    box(head,'winding axle',[-6,-.5,5],[6,.5,6],'iron','x')
    box(head,'crank lever',[6,-2,5],[7,1,6],'steel')
    box(head,'crank grip',[7,-2,4.75],[9,-1,6.25],'oak','x')
    box(arm,'throwing beam',[-1,0,-11],[1,2,4],'oak','z')
    box(arm,'counterweight end',[-2,-1,4],[2,3,6],'iron')
    box(arm,'pivot sleeve',[-1.25,-.25,-1.25],[1.25,2.25,1.25],'iron')
    for z in (-5,-3): box(arm,'rope binding',[-1.125,-.125,z],[1.125,2.125,z+1],'rope')
    box(arm,'cup floor',[-3.5,1,-12],[3.5,2,-6],'heartwood')
    for x in (-3.5,2.5): box(arm,'cup side',[x,2,-12],[x+1,4,-6],'iron')
    for z in (-12,-7): box(arm,'cup rim',[-2.5,2,z],[2.5,3.5,z+1],'iron')
    box(ammo,'loaded stone',[-2.25,2,-10.75],[2.25,6,-6.75],'stone')
    box(ammo,'stone crown',[-1.5,6,-10],[1.5,6.75,-7.5],'stone')
    parts={'stone_turret':base,'stone_turret_bearing':shifted(bearing,[8,8,8]),'stone_turret_head':shifted(head,[8,8,8]),'stone_turret_arm':shifted(arm,[8,8,8]),'stone_turret_ammo':shifted(ammo,[8,8,8])}
    for state in ('ready','warning','amp','stopped'):
        lamp=[];box(lamp,'status lamp',[6.5,3.5,0],[9.5,5.5,.375],state)
        parts['stone_turret_lamp_'+state]=lamp
    assembled=base+shifted(bearing+head+arm+ammo,[8,20.8,8])+parts['stone_turret_lamp_ready']
    parts['stone_turret_assembled']=assembled
    textures={name:'coredefender:block/stone_turret_'+name for name in PALETTES}
    textures['particle']='#oak'
    for name,elements in parts.items():
        for e in elements:
            assert all(-16<=v<=32 for key in ('from','to') for v in e[key]),e
            assert e.get('rotation',{}).get('angle',0) in (-45,-22.5,0,22.5,45)
        model={'parent':'minecraft:block/block','textures':textures,'elements':elements}
        (ASSETS/'models/block'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n')
    item={'parent':'coredefender:block/stone_turret_assembled','display':{'gui':{'rotation':[30,225,0],'translation':[0,-3,0],'scale':[.55,.55,.55]},'ground':{'translation':[0,2,0],'scale':[.25,.25,.25]},'fixed':{'rotation':[0,180,0],'translation':[0,-3,0],'scale':[.5,.5,.5]}}}
    (ASSETS/'models/item/stone_turret.json').write_text(json.dumps(item,indent=2)+'\n')
    preview={'textures':{name:Path(os.path.relpath(texdir/f'stone_turret_{name}.png',OUT)).as_posix() for name in PALETTES},'elements':assembled}
    (OUT/'assembled.json').write_text(json.dumps(preview,indent=2)+'\n')
    fired_arm = shifted(arm,[8,20.8,8])
    for element in fired_arm:
        element['rotation'] = {'origin':[8,20.8,8],'axis':'x','angle':45}
    fired = copy.deepcopy(preview)
    fired['elements'] = base+shifted(bearing+head,[8,20.8,8])+fired_arm+parts['stone_turret_lamp_ready']
    (OUT/'fired.json').write_text(json.dumps(fired,indent=2)+'\n')
    if args.renderer:
        spec = importlib.util.spec_from_file_location('stone_preview_renderer',args.renderer)
        renderer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(renderer)
        renderer.SCALE = 11
        for name,source in [('preview',preview),('fired',fired)]:
            framed = copy.deepcopy(source)
            framed['elements'] = shifted(source['elements'],[0,-5,0])
            images = renderer.load_textures(framed,str(OUT))
            sheet = Image.new('RGB',(1024,1024))
            for i,(yaw,pitch,label) in enumerate([(-35,30,'north-left'),(35,30,'north-right'),(145,30,'south / firing direction'),(-35,75,'top')]):
                sheet.paste(renderer.render_view(framed,images,yaw,pitch,label),((i%2)*512,(i//2)*512))
            sheet.save(OUT/(name+'.png'))
    print('Generated stone turret:',len(assembled),'elements; 10 dedicated 16x16 textures')

if __name__ == '__main__': main()
