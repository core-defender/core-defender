// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedVariable
const root=path.join(__dirname,'..');
const config=JSON.parse(fs.readFileSync(path.join(root,'kubejs/config/coredefender-waves.json'),'utf8'));
assert.equal(config.repair.item,'minecraft:iron_ingot');
const recipes=[],removed=[];let current;
const event={remove(filter){removed.push(filter);},shaped(out,pattern,keys){current={out,pattern,keys};recipes.push(current);return {id(id){current.id=id;return this;}};},shapeless(out,ingredients){current={out,ingredients};recipes.push(current);return {id(id){current.id=id;return this;}};},custom(){return {id(){return this;}};}};
const context={ServerEvents:{recipes:fn=>fn(event)},Item:{of:(id,count)=>({id,count})}};
for(const name of ['recipes','turrets'])vm.runInNewContext(fs.readFileSync(path.join(root,`kubejs/server_scripts/coredefender/${name}.js`),'utf8'),context);
assert.equal(recipes.filter(r=>r.id&&r.id.startsWith('coredefender:module_')&&r.ingredients).length,30);
assert.equal(recipes.filter(r=>r.id&&r.id.startsWith('coredefender:convert_')).length,6);
assert.ok(!recipes.some(r=>typeof r.out==='string'&&/module_(stone|flame)_range_/.test(r.out)));
for(let grade=1;grade<=3;grade++) {
 assert.ok(recipes.some(r=>r.id===`coredefender:module_range_${grade}`));
 for(const legacy of ['stone_range','flame_range']) {
  const conversion=recipes.find(r=>r.id===`coredefender:convert_${legacy}_${grade}`);
  assert.equal(conversion.out,`coredefender:module_range_${grade}`);
  assert.equal(conversion.ingredients[0],`coredefender:module_${legacy}_${grade}`);
 }
}
assert.ok(recipes.some(r=>r.out==='coredefender:oil_turret'));
assert.ok(!recipes.some(r=>r.out==='coredefender:flame_turret'));
assert.ok(removed.some(r=>r.output==='coredefender:flame_turret'));
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
console.log('PASS: repair access, 30 module recipes, 6 legacy conversions and crafting grid sizes');
