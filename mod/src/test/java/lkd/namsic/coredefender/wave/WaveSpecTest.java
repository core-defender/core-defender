package lkd.namsic.coredefender.wave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WaveSpecTest {
    @Test
    void bossesFollowAllNormalEntriesAndWaitForTheFinalFifth() {
        WaveSpec spec = WaveSpec.parse("""
            {"mobs":[{"type":"minecraft:evoker","count":1,"boss":"boss","health":900},
            {"type":"minecraft:zombie","count":100}],"spawnBatchSize":8}
            """);
        for (int seed = 0; seed < 20; seed++) {
            List<WaveSpec.MobEntry> queue = spec.spawnQueue(new Random(seed));
            assertEquals(101, queue.size());
            assertTrue(queue.subList(0, 100).stream().allMatch(entry -> entry.boss().isEmpty()));
            assertEquals("boss", queue.getLast().boss());
            assertFalse(spec.canSpawn(queue.getLast(), 21));
            assertTrue(spec.canSpawn(queue.getLast(), 20));
            assertTrue(spec.canSpawn(queue.getFirst(), 90));
        }

        assertEquals(8, spec.spawnBatchSize());
    }

    @Test
    void summonerWardDecaysWithoutInvulnerabilityOrNegativeReduction() {
        assertEquals(.9, SummonerWard.reductionAt(0), 1e-8);
        assertEquals(.45, SummonerWard.reductionAt(600), 1e-8);
        assertEquals(0, SummonerWard.reductionAt(1200));
        assertEquals(0, SummonerWard.reductionAt(2400));
        assertEquals(.9, SummonerWard.reductionAt(-1), 1e-8);
    }
}
