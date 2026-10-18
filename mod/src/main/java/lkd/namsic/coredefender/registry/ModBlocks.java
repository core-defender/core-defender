package lkd.namsic.coredefender.registry;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.block.CoreBlock;
import lkd.namsic.coredefender.block.WallBlock;
import lkd.namsic.coredefender.killzone.BarricadeBlock;
import lkd.namsic.coredefender.killzone.SpikeTrapBlock;
import lkd.namsic.coredefender.supply.SupplyBlock;
import lkd.namsic.coredefender.turret.BasicTurretBlock;
import lombok.experimental.UtilityClass;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@UtilityClass
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CoreDefender.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CoreDefender.MOD_ID);

    /** 성 중앙의 관리 거점. 파괴 불가. */
    public static final DeferredBlock<CoreBlock> CORE = BLOCKS.registerBlock("core", CoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 12)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    /** 시스템이 생성하는 성벽. 블록 자체는 파괴 불가이고 HP 는 구간 단위로 WallState 가 관리한다. */
    public static final DeferredBlock<WallBlock> WALL = BLOCKS.registerBlock("wall", WallBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .sound(SoundType.DEEPSLATE_BRICKS)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    /** 킬존 바리케이드: 성벽 밖에 설치 가능, HP 는 KillzoneManager 가 관리 */
    public static final DeferredBlock<BarricadeBlock> BARRICADE = BLOCKS.registerBlock("barricade", BarricadeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(2.0F, 6.0F));

    /** 킬존 가시 함정: 통과 가능, 웨이브 몹 감속 + 피해 */
    public static final DeferredBlock<SpikeTrapBlock> SPIKE_TRAP = BLOCKS.registerBlock("spike_trap", SpikeTrapBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(1.5F, 6.0F)
                    .noCollission()
                    .noOcclusion());

    /** 기초 포탑 (공학 1단계) */
    public static final DeferredBlock<BasicTurretBlock> BASIC_TURRET = BLOCKS.registerBlock("basic_turret", BasicTurretBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.STONE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops());

    /** 보급 송신기 (디펜스 차원 밖) / 수신기 (디펜스 차원 안) */
    public static final DeferredBlock<SupplyBlock> SUPPLY_SENDER = BLOCKS.registerBlock("supply_sender",
            properties -> new SupplyBlock(properties, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).sound(SoundType.METAL).strength(3.0F, 6.0F));
    public static final DeferredBlock<SupplyBlock> SUPPLY_RECEIVER = BLOCKS.registerBlock("supply_receiver",
            properties -> new SupplyBlock(properties, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).sound(SoundType.METAL).strength(3.0F, 6.0F));

    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);
    public static final DeferredItem<BlockItem> BASIC_TURRET_ITEM = ITEMS.registerSimpleBlockItem(BASIC_TURRET);
    public static final DeferredItem<BlockItem> SUPPLY_SENDER_ITEM = ITEMS.registerSimpleBlockItem(SUPPLY_SENDER);
    public static final DeferredItem<BlockItem> SUPPLY_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem(SUPPLY_RECEIVER);
    public static final DeferredItem<BlockItem> WALL_ITEM = ITEMS.registerSimpleBlockItem(WALL);
    public static final DeferredItem<BlockItem> BARRICADE_ITEM = ITEMS.registerSimpleBlockItem(BARRICADE);
    public static final DeferredItem<BlockItem> SPIKE_TRAP_ITEM = ITEMS.registerSimpleBlockItem(SPIKE_TRAP);

}
