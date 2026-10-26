package lkd.namsic.coredefender.compat;

/**
 * 수동 압착은 씨앗당 25mB. Create 기본 압착(100mB)의 1/4 수율.
 */
public record OilBatch(int seeds, int stomps) {
    public static final int SEEDS = 10;
    public static final int STOMPS = 12;
    public static final int MILLIBUCKETS = 250;

    public OilBatch {
        seeds = Math.clamp(seeds, 0, SEEDS);
        stomps = seeds == SEEDS ? Math.clamp(stomps, 0, STOMPS) : 0;
    }

    public boolean ready() {
        return this.seeds == SEEDS && this.stomps == STOMPS;
    }

    public OilBatch add(int amount) {
        return new OilBatch(this.seeds + Math.clamp(amount, 0, SEEDS - this.seeds), this.stomps);
    }

    public OilBatch stomp() {
        return new OilBatch(this.seeds, this.stomps + 1);
    }
}
