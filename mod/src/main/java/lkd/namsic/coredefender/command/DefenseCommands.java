package lkd.namsic.coredefender.command;

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
                .then(Commands.literal("wall")
                        .then(Commands.literal("info").executes(DefenseCommands::info))
                        .then(Commands.literal("damage")
                                .then(Commands.argument("segment", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(DefenseCommands::damage))))
                        .then(Commands.literal("repair")
                                .executes(ctx -> repair(ctx, null))
                                .then(Commands.argument("segment", StringArgumentType.word())
                                        .executes(ctx -> repair(ctx, StringArgumentType.getString(ctx, "segment")))))
                        .then(Commands.literal("expand").executes(DefenseCommands::expand))
                        .then(Commands.literal("rebuild").executes(DefenseCommands::rebuild)))));
    }

    private static ServerLevel defense(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = WallManager.level(ctx.getSource().getServer());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("Defense dimension not loaded"));
        }
        return level;
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
        var geo = state.geometry();
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

    private static int repair(CommandContext<CommandSourceStack> ctx, String segment) {
        ServerLevel level = defense(ctx);
        if (level == null) {
            return 0;
        }
        WallManager.repair(level, segment, 0);
        ctx.getSource().sendSuccess(() -> Component.literal("Repaired " + (segment == null ? "all segments" : segment)), true);
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
