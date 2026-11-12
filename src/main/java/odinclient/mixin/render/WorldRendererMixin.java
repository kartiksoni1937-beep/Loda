package odinclient.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;

import odinclient.event.WorldRenderEvent;
import odinclient.module.ModuleContext;
import odinclient.module.world.ChunkMarkerRenderer;
import odinclient.module.world.LightFinderRenderer;
import odinclient.render.projection.WorldToScreen;
import odinclient.render.world.GizmoRenderer;
import odinclient.render.world.StableLineRenderer;
import odinclient.render.world.TracerBatchRenderer;
import odinclient.render.world.TracerLineBatch;
import odinclient.render.world.WorldBoxQueue;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void odinclient$renderWorld(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f modelView,
            Matrix4f projection,
            Matrix4f positionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci) {
        TracerLineBatch.clear();
        ChunkMarkerRenderer.captureCamera(camera, modelView, projection);
        WorldToScreen.updateMatrices(camera, modelView, projection);
        ModuleContext.EVENT_BUS.post(
                new WorldRenderEvent(new GizmoRenderer(camera, modelView, projection)));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void odinclient$flushWorldQueues(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f modelView,
            Matrix4f projection,
            Matrix4f positionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci) {
        LightFinderRenderer.renderQueued(camera, modelView);
        WorldBoxQueue.renderQueued(camera, modelView);
        StableLineRenderer.flush();
        TracerBatchRenderer.flush();
    }
}
