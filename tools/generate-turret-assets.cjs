// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const fs=require('node:fs'),path=require('node:path');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedVariable
const root=path.join(__dirname,'../mod/src/main/resources');
function write(name,data){const p=path.join(root,name);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(data,null,2)+'\n');}
const names=['damage','speed','pierce','multishot','ricochet','aim','stone_range','blast','cluster','flame_range','burn','range'];
const ko=['공격력','공격속도','관통','다중발사','도탄','조준속도','구형 투석 사거리','폭발 범위','집속탄','구형 화염 사거리','잔류 화상','사거리'];
for(const language of ['en_us','ko_kr']){
 const file=`assets/coredefender/lang/${language}.json`,lang=JSON.parse(fs.readFileSync(path.join(root,file),'utf8').replace(/^\uFEFF/,'')),k=language==='ko_kr';
 lang['block.coredefender.basic_turret']=k?'화살 포탑':'Arrow Turret';lang['block.coredefender.stone_turret']=k?'투석 포탑':'Stone Turret';lang['block.coredefender.flame_turret']=k?'화염 포탑':'Flame Turret';
 lang['block.coredefender.oil_turret']=k?'기름 포탑':'Burning Oil Turret';
 lang['item.coredefender.range_analyzer']=k?'방어 분석기':'Defense Analyzer';lang['item.coredefender.module_chassis']=k?'포탑 모듈 기판':'Turret Module Chassis';lang['entity.coredefender.stone_projectile']=k?'포탑 투석탄':'Turret Stone';
 names.forEach((n,i)=>{for(let g=1;g<=3;g++)lang[`item.coredefender.module_${n}_${g}`]=`${k?ko[i]:(['stone_range','flame_range'].includes(n)?'Legacy ':'')+n.replaceAll('_',' ')} ${k?'모듈':'Module'} ${['','I','II','III'][g]}`;});write(file,lang);
}
// 모듈·분석기 아이템 모델과 아이콘은 module_item_icons.py 가 관리한다.
// 포탑 블록/아이템 모델은 포탑별 전용 생성기가 관리한다:
// generate-arrow-flame-models.py, generate-stone-turret.py, generate-oil-turret-assets.py
write('data/coredefender/tags/item/turret_stones.json',{replace:false,values:['stone','cobblestone','granite','diorite','andesite','tuff','calcite','blackstone','basalt','netherrack','end_stone'].map(s=>'minecraft:'+s)});
write('data/coredefender/tags/item/turret_deepslate.json',{replace:false,values:['minecraft:deepslate','minecraft:cobbled_deepslate']});
for(const p of ['data/minecraft/tags/block/mineable/pickaxe.json','data/minecraft/tags/block/needs_stone_tool.json']){
 if(!fs.existsSync(path.join(root,p)))continue;
 const tag=JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
 for(const id of ['coredefender:stone_turret','coredefender:flame_turret','coredefender:oil_turret'])if(!tag.values.includes(id))tag.values.push(id);write(p,tag);
}
console.log('Generated turret models, module names and stone tags.');
