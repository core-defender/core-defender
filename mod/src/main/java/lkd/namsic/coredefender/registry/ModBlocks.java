package lkd.namsic.coredefender.registry;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.block.CoreBlock;
import lkd.namsic.coredefender.block.WallBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
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

    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);
    public static final DeferredItem<BlockItem> WALL_ITEM = ITEMS.registerSimpleBlockItem(WALL);

    private ModBlocks() {
    }
}
