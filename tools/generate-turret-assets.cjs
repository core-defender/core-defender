// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const fs=require('node:fs'),path=require('node:path');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedVariable
const root=path.join(__dirname,'../mod/src/main/resources');
function write(name,data){const p=path.join(root,name);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(data,null,2)+'\n');}
const names=['damage','speed','pierce','multishot','ricochet','aim','stone_range','blast','cluster','flame_range','burn'];
const ko=['공격력','공격속도','관통','다중발사','도탄','조준속도','투석 사거리','폭발 범위','집속탄','화염 사거리','잔류 화상'];
const textures=['iron_sword','sugar','flint','arrow','slime_ball','compass','string','gunpowder','fire_charge','copper_ingot','blaze_powder'];
for(const language of ['en_us','ko_kr']){
 const file=`assets/coredefender/lang/${language}.json`,lang=JSON.parse(fs.readFileSync(path.join(root,file),'utf8').replace(/^\uFEFF/,'')),k=language==='ko_kr';
 lang['block.coredefender.basic_turret']=k?'화살 포탑':'Arrow Turret';lang['block.coredefender.stone_turret']=k?'투석 포탑':'Stone Turret';lang['block.coredefender.flame_turret']=k?'화염 포탑':'Flame Turret';
 lang['item.coredefender.range_analyzer']=k?'방어 분석기':'Defense Analyzer';lang['item.coredefender.module_chassis']=k?'포탑 모듈 기판':'Turret Module Chassis';lang['entity.coredefender.stone_projectile']=k?'포탑 투석탄':'Turret Stone';
 names.forEach((n,i)=>{for(let g=1;g<=3;g++)lang[`item.coredefender.module_${n}_${g}`]=`${k?ko[i]:n.replaceAll('_',' ')} ${k?'모듈':'Module'} ${['','I','II','III'][g]}`;});write(file,lang);
}
names.forEach((n,i)=>{for(let g=1;g<=3;g++)write(`assets/coredefender/models/item/module_${n}_${g}.json`,{parent:'minecraft:item/generated',textures:{layer0:`minecraft:item/${textures[i]}`}});});
for(const [n,t] of [['range_analyzer','compass'],['module_chassis','comparator']])write(`assets/coredefender/models/item/${n}.json`,{parent:'minecraft:item/generated',textures:{layer0:`minecraft:item/${t}`}});
const base=JSON.parse(fs.readFileSync(path.join(root,'assets/coredefender/models/block/basic_turret.json'),'utf8'));
function cube(from,to,texture){return {from,to,faces:Object.fromEntries(['down','up','north','south','west','east'].map(f=>[f,{texture}]))};}
for(const kind of ['stone','flame']){
 const block=structuredClone(base);if(kind==='flame')block.textures.wood='minecraft:block/copper_block';
 write(`assets/coredefender/models/block/${kind}_turret.json`,block);
 write(`assets/coredefender/blockstates/${kind}_turret.json`,{variants:{'':{model:`coredefender:block/${kind}_turret`}}});
 const item=structuredClone(block);item.elements.push(cube([5,12,5],[11,18,11],'#metal'));
 if(kind==='stone'){item.elements.push(cube([3,18,1],[13,21,17],'#wood'));item.elements.push(cube([5,21,10],[11,26,16],'#metal'));}
 else{item.elements.push(cube([3,17,2],[13,24,11],'#wood'));item.elements.push(cube([6,18,11],[10,22,22],'#metal'));}
 write(`assets/coredefender/models/item/${kind}_turret.json`,item);
}
write('data/coredefender/tags/item/turret_stones.json',{replace:false,values:['stone','cobblestone','granite','diorite','andesite','tuff','calcite','blackstone','basalt','netherrack','end_stone'].map(s=>'minecraft:'+s)});
write('data/coredefender/tags/item/turret_deepslate.json',{replace:false,values:['minecraft:deepslate','minecraft:cobbled_deepslate']});
for(const p of ['data/minecraft/tags/block/mineable/pickaxe.json','data/minecraft/tags/block/needs_stone_tool.json']){
 if(!fs.existsSync(path.join(root,p)))continue;
 const tag=JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
 for(const id of ['coredefender:stone_turret','coredefender:flame_turret'])if(!tag.values.includes(id))tag.values.push(id);write(p,tag);
}
console.log('Generated turret models, module names and stone tags.');
