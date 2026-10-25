package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Shared world-space quad setup for barrier surfaces and turret range overlays. */
record WorldRenderBatch(PoseStack pose, MultiBufferSource.BufferSource buffers, RenderType type,
                        VertexConsumer vertices, Matrix4f matrix) {
    static WorldRenderBatch begin(RenderLevelStageEvent event) {
        PoseStack pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.debugQuads();
        return new WorldRenderBatch(pose, buffers, type, buffers.getBuffer(type), pose.last().pose());
    }

    void finish() {
        this.buffers.endBatch(this.type);
        this.pose.popPose();
    }
}
