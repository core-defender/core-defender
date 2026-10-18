package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.CoreDefender;
import lombok.experimental.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Zombie;

/** 웨이브 몹 표식과 AI 준비. */
@UtilityClass
public class WaveMobs {
    public static final String TAG = "cd_wave";
    public static final String BREAKER_TAG = "cd_breaker";
    private static final ResourceLocation ELITE_MODIFIER = ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "elite");

    public static boolean isWaveMob(Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    public static boolean isBreaker(Entity entity) {
        return entity.getTags().contains(BREAKER_TAG);
    }

    /**
     * 웨이브 몹으로 설정: 기본 타겟 탐색과 배회를 제거한다.
     * 이동/성벽 공격은 WaveMobController 가, 플레이어 어그로는 피격 이벤트가 직접 타겟을 지정한다.
     */
    public static void prepare(Mob mob, boolean elite, boolean breaker) {
        mob.addTag(TAG);
        if (breaker) {
            mob.addTag(BREAKER_TAG);
        }
        mob.setPersistenceRequired();
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.goalSelector.removeAllGoals(goal -> goal instanceof RandomStrollGoal || goal instanceof MoveThroughVillageGoal);
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(false);
        }
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(64);
        }
        if (elite) {
            applyElite(mob);
        }
    }

    private static void applyElite(Mob mob) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addPermanentModifier(new AttributeModifier(ELITE_MODIFIER, Balance.ELITE_HEALTH_MULTIPLIER - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.addPermanentModifier(new AttributeModifier(ELITE_MODIFIER, Balance.ELITE_DAMAGE_MULTIPLIER - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        mob.setCustomName(Component.translatable("entity.coredefender.elite", mob.getType().getDescription()).withStyle(ChatFormatting.RED));
        mob.setCustomNameVisible(true);
    }

    public static float attackDamage(Mob mob) {
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        return damage != null ? (float) damage.getValue() : 2.0F;
    }
}
