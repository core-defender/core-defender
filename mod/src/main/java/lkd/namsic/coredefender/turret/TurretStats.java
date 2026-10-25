package lkd.namsic.coredefender.turret;

/** Immutable launch-time balance. Bonuses from the two module slots add together. */
public record TurretStats(int kind, double damageBonus, double speedBonus, int pierce, int multishot,
                          int ricochet, double aimBonus, double rangeBonus, double radiusBonus,
                          int fragments, double flameRange, double flameAngle, double burnBonus) {
    public static final int ARROW = 0, STONE = 1, FLAME = 2;
    public double damage(boolean deep, int amplifier) {
        return (this.kind == ARROW ? 4 : this.kind == STONE ? (deep ? 18 : 14) : 2)
                * (1 + this.damageBonus + (amplifier == 2 ? .25 : amplifier == 3 ? .3 : 0));
    }
    public int interval(int amplifier) {
        double speed = 1 + this.speedBonus + (amplifier == 1 ? .25 : 0);
        return this.kind == FLAME ? Math.max(12, (int)Math.ceil(10 + 20 / speed))
                : Math.max(this.kind == ARROW ? 4 : 15, (int)Math.ceil((this.kind == ARROW ? 16 : 60) / speed));
    }
    public double range(boolean deep, int amplifier) {
        return this.kind == ARROW ? 16 : this.kind == FLAME ? 4 + this.flameRange
                : (deep ? 21 : 24) * (1 + this.rangeBonus + (amplifier == 2 ? .2 : 0));
    }
    public double radius(boolean deep) { return (deep ? 3.5 : 3) * (1 + this.radiusBonus); }
    public double angle() { return Math.min(360, 90 + this.flameAngle); }
    public double burn(int amplifier) { return 1 + this.damageBonus + this.burnBonus + (amplifier == 3 ? .3 : 0); }
    // Longer range requires more launch energy to remain physically reachable with the same gravity.
    public double projectileSpeed(int amplifier) { return this.kind == ARROW ? 2.4 : 1.25 * Math.sqrt(1 + this.rangeBonus) * (amplifier == 2 ? 1.15 : 1); }
}
