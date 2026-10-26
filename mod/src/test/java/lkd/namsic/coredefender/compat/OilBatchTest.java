package lkd.namsic.coredefender.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OilBatchTest {
    @Test
    void cannotProduceOilBeforeFullBatchAndTwelveLandings() {
        OilBatch batch = new OilBatch(0, 0).add(9);
        for (int i = 0; i < 100; i++) {
            batch = batch.stomp();
        }

        assertFalse(batch.ready());
        assertEquals(0, batch.stomps());
        batch = batch.add(1);
        for (int i = 0; i < 11; i++) {
            batch = batch.stomp();
        }

        assertFalse(batch.ready());
        assertTrue(batch.stomp().ready());
        assertEquals(batch.stomp(), batch.stomp().stomp());
    }

    @Test
    void capsInputAndKeepsManualYieldFourTimesLowerThanCreate() {
        assertEquals(10, new OilBatch(0, 0).add(64).seeds());
        assertEquals(0, new OilBatch(-1, 100).seeds());
        assertEquals(0, new OilBatch(9, 100).stomps());
        assertEquals(100, OilBatch.MILLIBUCKETS * 4 / OilBatch.SEEDS);
    }
}
