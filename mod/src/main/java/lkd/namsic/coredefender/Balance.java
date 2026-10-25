package lkd.namsic.coredefender;

import lombok.experimental.UtilityClass;

/**
 * 모드 쪽 밸런스 상수. 스테이지 구성(몹 종류/수/보상)과 비용은 KubeJS 설정에서 정한다.
 */
@UtilityClass
public class Balance {
    /** 성벽 바깥면으로부터 킬존 장애물을 설치할 수 있는 거리 */
    public static final int KILLZONE_WIDTH = 12;

    public static final int BARRICADE_HP = 200;
    /** 플로우 필드에서 바리케이드 칸의 이동 비용 (일반 칸 = 1). 우회로가 이보다 길면 바리케이드를 부수고 지나간다. */
    public static final int BARRICADE_PATH_COST = 40;

    public static final float SPIKE_DAMAGE = 2.0F;
    public static final int SPIKE_INTERVAL = 10;

    /** 폭발 범위 안 성벽 블록 하나당 최대 피해 (중심에서 멀수록 감소) / 휘말린 바리케이드 피해 */
    public static final int EXPLOSION_WALL_DAMAGE = 8;
    public static final int EXPLOSION_BARRICADE_DAMAGE = 20;

    /** 몹 근접 공격 간격 (tick) */
    public static final int MELEE_INTERVAL = 20;
    /** 원거리 몹이 성벽을 쏘기 시작하는 거리 / 간격 / 화살 하나 피해 */
    public static final double RANGED_WALL_RANGE = 12;
    public static final int RANGED_INTERVAL = 40;
    public static final int RANGED_WALL_DAMAGE = 3;

    /** 플레이어가 공격했을 때 반응하는 주변 몹 반경 / 어그로 유지 시간 / 추적 최대 거리 */
    public static final double AGGRO_RADIUS = 12;
    public static final int AGGRO_DURATION = 200;
    public static final double AGGRO_LEASH = 24;

    public static final float ELITE_HEALTH_MULTIPLIER = 4.0F;
    public static final float ELITE_DAMAGE_MULTIPLIER = 2.0F;

    // Turret balance lives in turret/TurretStats to keep effective stats and combat in agreement.

    /** 보급 송수신기: 단계별 10 tick 당 전송량, 송신기가 로딩하는 청크 반경. 단계 = 강화 부품 적용 수 */
    public static final int[] SUPPLY_RATE = {4, 16, 64, 256};
    public static final int[] SUPPLY_CHUNK_RADIUS = {0, 1, 2, 3};
    public static final int SUPPLY_INTERVAL = 10;
    /** 차원 간 공용 보급 버퍼 크기 (스택 수) */
    public static final int SUPPLY_BUFFER_STACKS = 54;
}
