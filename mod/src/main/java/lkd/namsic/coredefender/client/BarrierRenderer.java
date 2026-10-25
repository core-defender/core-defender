package lkd.namsic.coredefender.client;

import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexConsumer;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.wall.BarrierGeometry;
import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class BarrierRenderer {
    private BarrierRenderer() {}
    private record Health(int hp, int max, boolean collapsed, long flashUntil) {}
    private static ClientLevel world;
    private static List<BarrierGeometry.Panel> panels = List.of();
    private static int segmentsPerSide;
    private static final Map<String, Health> HEALTH = new HashMap<>();
    private static final Map<String, List<BarrierSurfaceMesh.Face>> SURFACES = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (world != Minecraft.getInstance().level) {
            world = null;
            panels = List.of();
            HEALTH.clear();
            SURFACES.clear();
        }
    }

    public static void accept(String json) {
        var level = Minecraft.getInstance().level;
        if (level == null || !DefenseDimension.is(level)) return;
        if (world != level) { HEALTH.clear(); world = level; }
        var data = JsonParser.parseString(json).getAsJsonObject();
        var geo = new WallGeometry(data.get("coreX").getAsInt(), data.get("coreZ").getAsInt(),
                data.get("baseY").getAsInt(), data.get("interiorChunks").getAsInt() / 2,
                data.get("height").getAsInt(), data.get("thickness").getAsInt());
        panels = BarrierGeometry.panels(geo);
        segmentsPerSide = geo.segmentsPerSide();
        Map<String, Health> updated = new HashMap<>();
        for (var value : data.getAsJsonArray("segments")) {
            var s = value.getAsJsonObject();
            String id = s.get("id").getAsString();
            int hp = s.get("hp").getAsInt();
            Health old = HEALTH.get(id);
            long flash = old != null && hp < old.hp ? level.getGameTime() + 6 : old == null ? 0 : old.flashUntil;
            updated.put(id, new Health(hp, s.get("maxHp").getAsInt(), s.get("collapsed").getAsBoolean(), flash));
        }
        HEALTH.clear();
        HEALTH.putAll(updated);
        SURFACES.clear();
        for (var panel : panels) {
            Health health = HEALTH.get(panel.id());
            if (health == null || health.collapsed) continue;
            var box = panel.box();
            boolean alongX = panel.id().startsWith("N") || panel.id().startsWith("S");
            int index = Integer.parseInt(panel.id().substring(1));
            float width = (float) (alongX ? box.maxX - box.minX : box.maxZ - box.minZ)
                    - (index == 0 ? .06F : 0) - (index == segmentsPerSide - 1 ? .06F : 0);
            SURFACES.put(panel.id(), BarrierSurfaceMesh.create(width, (float) (box.maxY - box.minY) - .1F,
                    (float) health.hp / Math.max(1, health.max), panel.id().hashCode()));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (mc.level != world || mc.level == null || !DefenseDimension.is(mc.level)) {
            world = null; panels = List.of(); HEALTH.clear(); SURFACES.clear(); return;
        }
        var batch = WorldRenderBatch.begin(event);
        var out = batch.vertices();
        Matrix4f matrix = batch.matrix();
        for (var panel : panels) {
            Health health = HEALTH.get(panel.id());
            if (health == null || health.collapsed || !event.getFrustum().isVisible(panel.box())) continue;
            float ratio = (float) health.hp / Math.max(1, health.max);
            boolean flash = health.flashUntil > mc.level.getGameTime();
            int r = ratio > 0.5 ? 60 : 255, g = ratio > 0.5 ? 190 : ratio > 0.25 ? 180 : 70;
            int b = ratio > 0.5 ? 255 : 70;
            var box = panel.box();
            boolean alongX = panel.id().startsWith("N") || panel.id().startsWith("S");
            float x0 = (float) (alongX ? box.minX : (box.minX + box.maxX) / 2);
            float x1 = (float) (alongX ? box.maxX : (box.minX + box.maxX) / 2);
            float z0 = (float) (alongX ? (box.minZ + box.maxZ) / 2 : box.minZ);
            float z1 = (float) (alongX ? (box.minZ + box.maxZ) / 2 : box.maxZ);
            int segmentIndex = Integer.parseInt(panel.id().substring(1));
            float dx = alongX ? 0.06F : 0;
            float dz = alongX ? 0 : 0.06F;
            boolean startCorner = segmentIndex == 0;
            boolean endCorner = segmentIndex == segmentsPerSide - 1;
            // 중앙 면과 테두리 영역을 분할해 같은 평면에 두 면이 겹치지 않게 한다.
            float surfaceX = x0 + (startCorner ? dx : 0), surfaceZ = z0 + (startCorner ? dz : 0);
            for (var face : SURFACES.getOrDefault(panel.id(), List.of())) {
                int alpha = face.edge() ? (int) (80 + (1 - ratio) * 100) : flash ? 95 : 22;
                int red = face.edge() ? Math.min(255, r + 45) : r;
                surface(out, matrix, face, alongX, surfaceX, (float) box.minY + .06F, surfaceZ, red, g, b, alpha);
            }
            // 위아래 경계는 선명하게, 면은 적이 잘 보이도록 옅게 표시한다.
            quad(out, matrix, x0, z0, x1, z1, (float) box.minY, (float) box.minY + 0.06F, r, g, b, 160);
            quad(out, matrix, x0, z0, x1, z1, (float) box.maxY - 0.04F, (float) box.maxY, r, g, b, 110);
            // 사각형의 네 모서리만 세로로 연결한다. 내부 구간 경계에는 띠를 그리지 않는다.
            if (startCorner) {
                quad(out, matrix, x0, z0, x0 + dx, z0 + dz,
                        (float) box.minY + 0.06F, (float) box.maxY - 0.04F, r, g, b, 160);
            }
            if (endCorner) {
                quad(out, matrix, x1 - dx, z1 - dz, x1, z1,
                        (float) box.minY + 0.06F, (float) box.maxY - 0.04F, r, g, b, 160);
            }
        }
        batch.finish();
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, float x0, float z0, float x1, float z1,
                             float bottom, float top, int r, int g, int b, int a) {
        out.addVertex(matrix, x0, bottom, z0).setColor(r, g, b, a);
        out.addVertex(matrix, x1, bottom, z1).setColor(r, g, b, a);
        out.addVertex(matrix, x1, top, z1).setColor(r, g, b, a);
        out.addVertex(matrix, x0, top, z0).setColor(r, g, b, a);
    }

    private static void surface(VertexConsumer out, Matrix4f matrix, BarrierSurfaceMesh.Face face, boolean alongX,
                                float x, float y, float z, int r, int g, int b, int alpha) {
        vertex(out, matrix, face.a(), alongX, x, y, z, r, g, b, alpha);
        vertex(out, matrix, face.b(), alongX, x, y, z, r, g, b, alpha);
        vertex(out, matrix, face.c(), alongX, x, y, z, r, g, b, alpha);
        vertex(out, matrix, face.d(), alongX, x, y, z, r, g, b, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, BarrierSurfaceMesh.Point point, boolean alongX,
                               float x, float y, float z, int r, int g, int b, int alpha) {
        out.addVertex(matrix, x + (alongX ? point.u() : 0), y + point.v(), z + (alongX ? 0 : point.u()))
                .setColor(r, g, b, alpha);
    }
}
