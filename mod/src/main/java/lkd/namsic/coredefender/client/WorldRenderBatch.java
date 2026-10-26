package lkd.namsic.coredefender.client;

import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Shared world-space quad setup for barrier surfaces and turret range overlays.
 */
record WorldRenderBatch(PoseStack pose, MultiBufferSource.BufferSource buffers, RenderType type,
                        VertexConsumer vertices, Matrix4f matrix) {
    static WorldRenderBatch begin(RenderLevelStageEvent event) {
        return begin(event, Vec3.ZERO);
    }

    static WorldRenderBatch begin(RenderLevelStageEvent event, Vec3 origin) {
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.debugQuads();
        return new WorldRenderBatch(pose, buffers, type, buffers.getBuffer(type), pose.last().pose());
    }

    static void quad(VertexConsumer out, Matrix4f matrix, int r, int g, int b, int alpha, float... coordinates) {
        for (int i = 0; i < coordinates.length; i += 3) {
            out.addVertex(matrix, coordinates[i], coordinates[i + 1], coordinates[i + 2]).setColor(r, g, b, alpha);
        }
    }

    void finish() {
        this.buffers.endBatch(this.type);
        this.pose.popPose();
    }
}
