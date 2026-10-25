package lkd.namsic.coredefender.registry;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.supply.RestrictedItemHandler;
import lkd.namsic.coredefender.supply.SupplyBlockEntity;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lkd.namsic.coredefender.wall.CoreStorage;
import lombok.experimental.UtilityClass;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@UtilityClass
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CoreDefender.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicTurretBlockEntity>> BASIC_TURRET =
            BLOCK_ENTITIES.register("basic_turret",
                    () -> BlockEntityType.Builder.of(BasicTurretBlockEntity::new, ModBlocks.BASIC_TURRET.get(), ModBlocks.STONE_TURRET.get(), ModBlocks.FLAME_TURRET.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupplyBlockEntity>> SUPPLY =
            BLOCK_ENTITIES.register("supply",
                    () -> BlockEntityType.Builder.of(SupplyBlockEntity::new, ModBlocks.SUPPLY_SENDER.get(), ModBlocks.SUPPLY_RECEIVER.get()).build(null));

    /** 호퍼/파이프 연동: 포탑은 화살 넣기, 송신기는 넣기 전용, 수신기는 빼기 전용, 코어는 창고 입출력 */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> level instanceof ServerLevel serverLevel && DefenseDimension.is(serverLevel)
                        ? new InvWrapper(CoreStorage.get(serverLevel).container()) : null,
                ModBlocks.CORE.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BASIC_TURRET.get(),
                BasicTurretBlockEntity::itemPort);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BASIC_TURRET.get(), BasicTurretBlockEntity::fluidPort);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SUPPLY.get(),
                (supply, side) -> new RestrictedItemHandler(supply.getInventory(), supply.isSender(), !supply.isSender()));
    }
}
