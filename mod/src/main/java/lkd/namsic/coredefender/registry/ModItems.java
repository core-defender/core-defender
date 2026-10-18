package lkd.namsic.coredefender.registry;

import lombok.experimental.UtilityClass;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/** 블록이 아닌 아이템. 레시피는 KubeJS 에서 공학 단계별 재료로 정한다. */
@UtilityClass
public class ModItems {
    /** 코어 메뉴에서 성벽 확장에 소모 */
    public static final DeferredItem<Item> WALL_EXPANSION_PLAN = ModBlocks.ITEMS.registerSimpleItem("wall_expansion_plan");

    /** 보급 송수신기 강화 부품 (1: Create, 2: IE, 3: Mekanism 단계 재료로 제작) */
    public static final DeferredItem<Item> SUPPLY_UPGRADE_1 = ModBlocks.ITEMS.registerSimpleItem("supply_upgrade_1");
    public static final DeferredItem<Item> SUPPLY_UPGRADE_2 = ModBlocks.ITEMS.registerSimpleItem("supply_upgrade_2");
    public static final DeferredItem<Item> SUPPLY_UPGRADE_3 = ModBlocks.ITEMS.registerSimpleItem("supply_upgrade_3");

    private static final List<DeferredItem<Item>> SUPPLY_UPGRADES = List.of(SUPPLY_UPGRADE_1, SUPPLY_UPGRADE_2, SUPPLY_UPGRADE_3);

    /** 강화 부품이면 적용 후 단계(1~3), 아니면 -1 */
    public static int supplyUpgradeTier(ItemStack stack) {
        for (int i = 0; i < SUPPLY_UPGRADES.size(); i++) {
            if (stack.is(SUPPLY_UPGRADES.get(i).get())) {
                return i + 1;
            }
        }
        return -1;
    }

    /** 클래스 로딩으로 위 아이템을 등록시키기 위한 호출 지점 */
    public static void init() {
    }
}
