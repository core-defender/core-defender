package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.network.OilSurfacePayload;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lkd.namsic.coredefender.turret.TurretStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class OilSurfaceRenderer {
    private static @Nullable ClientLevel world;
    private static final Map<UUID, Surface> SURFACES = new HashMap<>();

    private record Surface(BlockPos turret, long received, List<OilSurfacePayload.Cell> cells) {
    }

    private OilSurfaceRenderer() {
    }

    public static void accept(OilSurfacePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(payload.dimension())) {
            return;
        }

        if (world != level) {
            SURFACES.clear();
            world = level;
        }

        if (payload.cells().isEmpty()) {
            SURFACES.remove(payload.identity());
        } else {
            SURFACES.put(payload.identity(), new Surface(payload.turret(), level.getGameTime(), payload.cells()));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level || level == null) {
            SURFACES.clear();
            world = level;
            return;
        }

        SURFACES.values().removeIf(surface -> level.getGameTime() - surface.received > 15
            || !(level.getBlockEntity(surface.turret) instanceof BasicTurretBlockEntity turret)
            || turret.kind() != TurretStats.OIL);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || level == null || world != level || SURFACES.isEmpty()) {
            return;
        }

        // Merge overlapping turrets before drawing to avoid coplanar quads and cumulative opacity.
        Map<BlockPos, OilSurfacePayload.Cell> visible = new HashMap<>();
        for (Surface surface : SURFACES.values()) {
            long elapsed = level.getGameTime() - surface.received;
            for (OilSurfacePayload.Cell cell : surface.cells) {
                if (elapsed < cell.remainingTicks()) {
                    visible.put(cell.block(), cell);
                }
            }
        }

        Vec3 camera = event.getCamera().getPosition();
        WorldRenderBatch batch = WorldRenderBatch.begin(event, camera);
        for (OilSurfacePayload.Cell cell : visible.values()) {
            float x = (float) (cell.block().getX() - camera.x);
            float z = (float) (cell.block().getZ() - camera.z);
            float y = (float) (cell.block().getY() + cell.height() + .018 - camera.y);
            // Disjoint strips leave a small seam between cells and never overlap their dark centers.
            rectangle(batch.vertices(), batch.matrix(), x + .065F, z + .065F, x + .935F, z + .935F, y, 48, 24, 8, 90);
            rectangle(batch.vertices(), batch.matrix(), x + .02F, z + .02F, x + .98F, z + .065F, y, 255, 160, 35, 215);
            rectangle(batch.vertices(), batch.matrix(), x + .02F, z + .935F, x + .98F, z + .98F, y, 255, 160, 35, 215);
            rectangle(batch.vertices(), batch.matrix(), x + .02F, z + .065F, x + .065F, z + .935F, y, 255, 160, 35, 215);
            rectangle(batch.vertices(), batch.matrix(), x + .935F, z + .065F, x + .98F, z + .935F, y, 255, 160, 35, 215);
        }

        batch.finish();
    }

    private static void rectangle(VertexConsumer out, Matrix4f matrix, float x0, float z0, float x1, float z1,
                                  float y, int r, int g, int b, int alpha) {
        WorldRenderBatch.quad(out, matrix, r, g, b, alpha,
            x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1);
    }
}
