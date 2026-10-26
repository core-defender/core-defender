// Arrow, stone and oil turrets are available before Create; flame remains registered for a later tier.
// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.recipes(event => {
  event.shaped('coredefender:stone_turret', ['SLS', 'IPI', 'CCC'], {
    S: 'minecraft:string', L: '#minecraft:logs', I: 'minecraft:iron_ingot', P: 'minecraft:piston', C: 'minecraft:cobblestone'
  }).id('coredefender:stone_turret')
  event.remove({output: 'coredefender:flame_turret'})
  event.shaped('coredefender:oil_turret', ['IBI', 'LFL', 'CCC'], {
    I: 'minecraft:iron_ingot', B: 'minecraft:bucket', L: '#minecraft:logs', F: 'minecraft:furnace', C: 'minecraft:cobblestone'
  }).id('coredefender:oil_turret')
  event.shaped('coredefender:range_analyzer', [' C ', ' R ', ' I '], {
    C: 'minecraft:copper_ingot', R: 'minecraft:redstone', I: 'minecraft:iron_ingot'
  }).id('coredefender:range_analyzer')
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  event.shaped(Item.of('coredefender:module_chassis', 4), ['CIC', ' R ', 'CIC'], {
    C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', R: 'minecraft:redstone'
  }).id('coredefender:module_chassis')

  const materials = {
    damage: ['2x minecraft:iron_ingot', '2x minecraft:flint'],
    speed: ['2x minecraft:copper_ingot', '2x minecraft:redstone'],
    pierce: ['minecraft:iron_ingot', '4x minecraft:flint'],
    multishot: ['2x minecraft:string', '4x minecraft:arrow'],
    ricochet: ['minecraft:slime_ball', '2x minecraft:iron_ingot'],
    aim: ['2x minecraft:redstone', '2x minecraft:string'],
    range: ['2x minecraft:string', 'minecraft:redstone'],
    blast: ['2x minecraft:gunpowder', '#minecraft:coals', '#minecraft:coals'],
    cluster: ['2x minecraft:gunpowder', '2x minecraft:flint'],
    burn: ['#minecraft:coals', '#minecraft:coals', 'minecraft:gunpowder']
  }
  Object.keys(materials).forEach(name => {
    event.shapeless(`coredefender:module_${name}_1`, ['coredefender:module_chassis'].concat(materials[name]))
      .id(`coredefender:module_${name}_1`)
    // Grade effects are implemented now; progression recipes use the pack's existing engineering chain.
    event.shapeless(`coredefender:module_${name}_2`, [
      `coredefender:module_${name}_1`, 'create:precision_mechanism', '2x create:brass_ingot'
    ]).id(`coredefender:module_${name}_2`)
    event.shapeless(`coredefender:module_${name}_3`, [
      `coredefender:module_${name}_2`, 'immersiveengineering:component_electronic', '2x immersiveengineering:ingot_steel'
    ]).id(`coredefender:module_${name}_3`)
  })
  for (let grade = 1; grade <= 3; grade++) {
    for (const legacy of ['stone_range', 'flame_range']) {
      event.shapeless(`coredefender:module_range_${grade}`, [`coredefender:module_${legacy}_${grade}`])
        .id(`coredefender:convert_${legacy}_${grade}`)
    }
  }
})
