package lkd.namsic.coredefender.wave;

import net.minecraft.core.BlockPos;
import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// GameTest discovers these entry points through annotations.
@SuppressWarnings("unused")
public class WaveBossGameTests {
    @GameTest(template = "empty", batch = "waveSummoning")
    public void summonedTroopsRespectCapsAndStopAfterBossDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(30, 2, 30));
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }

        Evoker boss = helper.spawn(EntityType.EVOKER, new BlockPos(30, 2, 30));
        boss.setNoAi(true);
        WaveSpec spec = WaveSpec.parse("""
            {"mobs":[{"type":"minecraft:evoker","count":1,"boss":"boss","health":900}]}
            """);
        WallGeometry geometry = new WallGeometry(origin.getX() + 100, origin.getZ(), origin.getY(), 1, 5, 0);
        WaveSummoner summoner = new WaveSummoner(spec.rules());
        List<Mob> troops = new ArrayList<>();
        try {
            for (int tick = 0; tick < 1500; tick++) {
                for (Mob troop : summoner.tick(level, boss, geometry, spec, 100)) {
                    // This isolated test owns the troops; production registers them in ActiveWave before joining.
                    troop.removeTag(WaveMobs.TAG);
                    level.addFreshEntity(troop);
                    troop.addTag(WaveMobs.TAG);
                    troops.add(troop);
                    helper.assertTrue(!geometry.isInterior(troop.getBlockX(), troop.getBlockZ()), "Summons must remain outside the wall");
                }
            }

            helper.assertTrue(troops.size() == 12, "Living summons must stop at twelve");
            helper.assertTrue(troops.stream().anyMatch(mob -> mob.getType() == EntityType.RAVAGER), "Alternating casts must include ravagers");
            helper.assertTrue(troops.stream().anyMatch(mob -> mob.getType() == EntityType.PILLAGER), "Alternating casts must include pillagers");
            Mob victim = troops.getFirst();
            float health = victim.getHealth();
            victim.hurt(level.damageSources().mobAttack(troops.getLast()), 20);
            helper.assertTrue(victim.getHealth() == health, "Summoned troops must not damage each other");
            boss.discard();
            helper.assertTrue(summoner.tick(level, boss, geometry, spec, 100).isEmpty(), "A dead boss must not summon reinforcements");
            helper.succeed();
        } finally {
            troops.forEach(Mob::discard);
            boss.discard();
        }
    }

    @GameTest(template = "empty", batch = "waveBosses")
    public void summonerWardReducesActualDamageAndExpires(GameTestHelper helper) {
        Evoker boss = helper.spawn(EntityType.EVOKER, new BlockPos(1, 1, 1));
        boss.setNoAi(true);
        WaveSpec spec = WaveSpec.parse("""
            {"mobs":[{"type":"minecraft:evoker","count":1,"boss":"boss","health":900,"attack":25}]}
            """);
        WaveMobs.prepare(boss, false, true);
        WaveMobs.scale(boss, spec, spec.mobs().getFirst());
        boss.hurt(helper.getLevel().damageSources().generic(), 100);
        helper.assertTrue(Math.abs(boss.getHealth() - 890) < .01, "Initial ward must reduce real damage by 90 percent");
        boss.getPersistentData().putLong("cd_summoner_ward_start", helper.getLevel().getGameTime() - 1200);
        boss.invulnerableTime = 0;
        boss.hurt(helper.getLevel().damageSources().generic(), 100);
        helper.assertTrue(Math.abs(boss.getHealth() - 790) < .01, "Expired ward must allow full damage");
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "waveBosses", timeoutTicks = 340)
    public void bruteHasFixedStatsNoWardAndDoesNotZombify(GameTestHelper helper) {
        PiglinBrute boss = helper.spawn(EntityType.PIGLIN_BRUTE, new BlockPos(1, 1, 1));
        WaveSpec spec = WaveSpec.parse("""
            {"healthMultiplier":3,"damageMultiplier":3.5,"mobs":[
            {"type":"minecraft:piglin_brute","count":1,"boss":"boss","health":1024,"attack":45,"armor":16}]}
            """);
        WaveMobs.prepare(boss, false, true);
        WaveMobs.scale(boss, spec, spec.mobs().getFirst());
        helper.assertTrue(boss.getMaxHealth() == 1024 && WaveMobs.attackDamage(boss) == 45, "Boss attributes must not be multiplied twice");
        helper.assertTrue(SummonerWard.reduction(boss) == 0, "Only the summoner receives the timed ward");
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(boss.isAlive() && boss.getType() == EntityType.PIGLIN_BRUTE, "Brute must remain a brute in an unsafe dimension");
            boss.discard();
            helper.succeed();
        });
    }
}

