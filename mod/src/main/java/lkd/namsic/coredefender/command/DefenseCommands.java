package lkd.namsic.coredefender.command;

import org.jspecify.annotations.Nullable;

import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import lombok.experimental.UtilityClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import lkd.namsic.coredefender.wave.TestMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 관리자/테스트용 명령어: /cd admin tp | /cd admin wall info|damage|repair|expand|rebuild
 */
@EventBusSubscriber(modid = CoreDefender.MOD_ID)
@UtilityClass
public class DefenseCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // KubeJS 가 등록하는 /cd 와 같은 트리에 합쳐진다 (Brigadier 가 같은 이름의 노드를 병합)
        dispatcher.register(Commands.literal("cd").then(Commands.literal("admin")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("tp").executes(DefenseCommands::teleport))
            .then(Commands.literal("spawn")
                .then(Commands.literal("clear").executes(ctx -> {
                    TestMobs.clear(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("테스트 적을 제거했습니다."), true);
                    return 1;
                }))
                .then(Commands.argument("entity", ResourceLocationArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                    .executes(ctx -> spawn(ctx, 1, null, "normal", new CompoundTag()))
                    .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                        .executes(ctx -> spawn(ctx, 1, null, "normal", CompoundTagArgument.getCompoundTag(ctx, "nbt"))))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), null, "normal", new CompoundTag()))
                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                            .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), null, "normal", CompoundTagArgument.getCompoundTag(ctx, "nbt"))))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), Vec3Argument.getVec3(ctx, "pos"), "normal", new CompoundTag()))
                            .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), Vec3Argument.getVec3(ctx, "pos"), "normal", CompoundTagArgument.getCompoundTag(ctx, "nbt"))))
                            .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"normal", "elite", "breaker", "elite_breaker"}, builder))
                                .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), Vec3Argument.getVec3(ctx, "pos"), StringArgumentType.getString(ctx, "variant"), new CompoundTag()))
                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                    .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), Vec3Argument.getVec3(ctx, "pos"), StringArgumentType.getString(ctx, "variant"), CompoundTagArgument.getCompoundTag(ctx, "nbt")))))))))
            .then(Commands.literal("wall")
                .then(Commands.literal("info").executes(DefenseCommands::info))
                .then(Commands.literal("damage")
                    .then(Commands.argument("segment", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(DefenseCommands::damage))))
                .then(Commands.literal("repair")
                    .executes(ctx -> repair(ctx, null, 0))
                    .then(Commands.argument("segment", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            ArrayList<String> targets = new ArrayList<>(List.of("all", "N", "E", "S", "W"));
                            ServerLevel level = WallManager.level(ctx.getSource().getServer());
                            if (level != null) {
                                targets.addAll(WallManager.state(level).segments().keySet());
                            }

                            return SharedSuggestionProvider.suggest(targets, builder);
                        })
                        .executes(ctx -> repair(ctx, StringArgumentType.getString(ctx, "segment"), 0))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> repair(ctx, StringArgumentType.getString(ctx, "segment"),
                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("expand").executes(DefenseCommands::expand))
                .then(Commands.literal("rebuild").executes(DefenseCommands::rebuild)))));
    }

    private static @Nullable ServerLevel defense(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = WallManager.level(ctx.getSource().getServer());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("Defense dimension not loaded"));
        }

        return level;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int count, @Nullable Vec3 position, String variant, CompoundTag nbt) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        if (ctx.getSource().getLevel() != level) {
            ctx.getSource().sendFailure(Component.literal("디펜스 차원에서 실행하세요. /cd enter"));
            return 0;
        }

        if (!Set.of("normal", "elite", "breaker", "elite_breaker").contains(variant)) {
            ctx.getSource().sendFailure(Component.literal("variant: normal / elite / breaker / elite_breaker"));
            return 0;
        }

        ResourceLocation id = ResourceLocationArgument.getId(ctx, "entity");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || !type.canSummon()) {
            ctx.getSource().sendFailure(Component.literal("소환할 수 없는 엔티티: " + id));
            return 0;
        }

        WallManager.ensureBuilt(ctx.getSource().getServer());
        Vec3 pos = position == null ? ctx.getSource().getPosition() : position;

        if (!level.isInWorldBounds(BlockPos.containing(pos)) || WallManager.state(level).geometry().distanceToWall(pos.x, pos.z) > 64) {
            ctx.getSource().sendFailure(Component.literal("성벽에서 64블록 이내의 유효한 위치를 지정하세요."));
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (TestMobs.spawn(level, type, pos, variant.contains("elite"), variant.contains("breaker"), nbt)) {
                spawned++;
            }
        }

        int result = spawned;
        if (result == 0) {
            ctx.getSource().sendFailure(Component.literal("Mob 종류만 소환할 수 있으며, 소환 위치에 빈 공간이 필요합니다."));
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("테스트 적 " + id + " " + result + "마리 소환 (웨이브 보상/진행 제외)"), true);
        }

        return result;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        WallManager.ensureBuilt(ctx.getSource().getServer());
        BlockPos core = WallManager.state(level).geometry().corePos();
        player.teleportTo(level, core.getX() + 0.5, core.getY(), core.getZ() + 3.5, 180, 0);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        WallState state = WallManager.state(level);
        WallGeometry geo = state.geometry();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Wall: interior %dx%d chunks, x[%d..%d] z[%d..%d] y%d h%d",
            geo.half() * 2, geo.half() * 2, geo.minX(), geo.maxX(), geo.minZ(), geo.maxZ(), geo.baseY(), geo.height())), false);
        state.segments().forEach((id, segment) -> ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("  %s: %d/%d%s", id, segment.hp(), segment.maxHp(), segment.collapsed() ? " [collapsed]" : "")), false));
        return 1;
    }

    private static int damage(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        String segment = StringArgumentType.getString(ctx, "segment");
        if (!WallManager.state(level).segments().containsKey(segment)) {
            ctx.getSource().sendFailure(Component.literal("Unknown segment: " + segment));
            return 0;
        }

        WallManager.damage(level, segment, IntegerArgumentType.getInteger(ctx, "amount"));
        return 1;
    }

    private static int repair(CommandContext<CommandSourceStack> ctx, @Nullable String segment, int amount) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        String target = segment == null || segment.equalsIgnoreCase("all") ? null : segment.toUpperCase(Locale.ROOT);
        if (target != null && Set.of("N", "E", "S", "W").contains(target)) {
            for (String id : WallManager.state(level).segments().keySet()) {
                if (id.startsWith(target)) {
                    WallManager.repair(level, id, amount);
                }
            }
        } else {
            if (target != null && !WallManager.state(level).segments().containsKey(target)) {
                ctx.getSource().sendFailure(Component.literal("알 수 없는 성벽 구간: " + target + ". all, N/E/S/W 또는 /cd admin wall info의 구간 ID를 입력하세요."));
                return 0;
            }

            WallManager.repair(level, target, amount);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Repaired " + (target == null ? "all segments" : target)
            + (amount > 0 ? " (각 구간 +" + amount + " HP, 최대 체력까지)" : " (최대 체력까지)")), true);
        return 1;
    }

    private static int expand(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        WallManager.expand(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Wall expanded"), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }

        WallManager.rebuild(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Wall rebuilt"), true);
        return 1;
    }
}
