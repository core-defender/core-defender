// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedVariable
const root=path.join(__dirname,'..');
const config=vm.runInNewContext(fs.readFileSync(path.join(root,'kubejs/server_scripts/coredefender/config.js'),'utf8')+'\nCD_CONFIG');
assert.equal(config.stages.length,10);
const totals=config.stages.map(s=>s.mobs.reduce((n,m)=>n+m.count,0));
assert.deepEqual(Array.from(totals),[12,18,24,32,40,48,58,70,84,100]);
config.stages.forEach((s,i)=>{assert.ok(s.spawnInterval>=10&&s.maxAlive<=60);assert.ok(s.rewards.every(r=>r.count>0));if(i<3)assert.ok(!s.mobs.some(m=>m.type==='minecraft:skeleton'));if(i<6)assert.ok(!s.mobs.some(m=>m.elite));});
assert.equal(config.repair.item,'minecraft:iron_ingot');
const recipes=[];let current;
const event={shaped(out,pattern,keys){current={out,pattern,keys};recipes.push(current);return {id(id){current.id=id;return this;}};},shapeless(out,ingredients){current={out,ingredients};recipes.push(current);return {id(id){current.id=id;return this;}};},custom(){return {id(){return this;}};}};
const context={ServerEvents:{recipes:fn=>fn(event)},Item:{of:(id,count)=>({id,count})}};
for(const name of ['recipes','turrets'])vm.runInNewContext(fs.readFileSync(path.join(root,`kubejs/server_scripts/coredefender/${name}.js`),'utf8'),context);
assert.equal(recipes.filter(r=>typeof r.out==='string'&&r.out.startsWith('coredefender:module_')).length,33);
assert.equal(new Set(recipes.filter(r=>r.id).map(r=>r.id)).size,recipes.filter(r=>r.id).length);
assert.ok(recipes.find(r=>r.out==='coredefender:basic_turret').keys.I==='minecraft:iron_ingot');
for(const r of recipes.filter(r=>r.id&&r.id.match(/module_.+_1$/)))assert.ok(r.ingredients.every(i=>!i.includes('create:')));
for(const r of recipes.filter(r=>r.pattern)){
 assert.ok(r.pattern.length<=3&&r.pattern.every(row=>row.length<=3));
 for(const c of r.pattern.join(''))if(c!==' ')assert.ok(r.keys[c],`Missing shaped key ${c}`);
}
for(const r of recipes.filter(r=>r.ingredients)){
 let slots=0;for(const ingredient of r.ingredients){const m=ingredient.match(/^(\d+)x /);slots+=m?Number(m[1]):1;}assert.ok(slots<=9,`${r.id}: ${slots} crafting slots`);
}
console.log('PASS: 10 wave stages, staged special enemies, repair access, 33 module recipes and crafting grid sizes');
