package odinclient.module.render.esp;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.event.WorldRenderEvent;
import odinclient.render.BoxRenderMode;
import odinclient.render.Color;
import odinclient.render.world.GizmoRenderer;

import java.util.HashSet;

public final class PistonEspRenderer {
    private PistonEspRenderer() {}

    public static void drawBlockBox(
            GizmoRenderer renderer,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            Color fill,
            Color outline,
            BoxRenderMode mode,
            Block block) {
        if (!"minecraft:moving_piston".equals(getBlockId(block))) {
            renderer.stableBox(x1, y1, z1, x2, y2, z2, fill, fill, BoxRenderMode.Sides);
        }
    }

    public static void drawBlockTracer(
            GizmoRenderer renderer,
            Vec3d target,
            Color color,
            float width,
            Block block,
            BlockPos pos) {
        if (!"minecraft:moving_piston".equals(getBlockId(block))) {
            renderer.blockEspHybridTracer(target, color, width);
        }
    }

    public static void renderMovingPistons(StorageEsp esp, WorldRenderEvent event) {
        if (esp.isPistonEspEnabled()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && client.player != null) {
                ChunkPos chunkPos = client.player.getChunkPos();
                Color fillColor = esp.getPistonFillColor();
                Color outlineColor = esp.getPistonOutlineColor();
                boolean tracers = esp.isTracersEnabled();
                float tracerWidth = esp.getTracerWidth();
                HashSet<Long> seen = new HashSet<>();

                for (int chunkX = chunkPos.x - 8; chunkX <= chunkPos.x + 8; chunkX++) {
                    for (int chunkZ = chunkPos.z - 8; chunkZ <= chunkPos.z + 8; chunkZ++) {
                        WorldChunk chunk =
                                client.world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
                        if (chunk != null && !chunk.isEmpty()) {
                            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                                if (blockEntity instanceof PistonBlockEntity piston) {
                                    BlockPos pos = piston.getPos();
                                    if (seen.add(pos.asLong())) {
                                        PistonEspRenderer.PistonOffsetPos offset =
                                                getRenderedPosition(pos, piston);
                                        event.renderer.stableBox(
                                                offset.x,
                                                offset.y,
                                                offset.z,
                                                offset.x + 1.0,
                                                offset.y + 1.0,
                                                offset.z + 1.0,
                                                fillColor,
                                                fillColor,
                                                BoxRenderMode.Sides);
                                        if (tracers) {
                                            event.renderer.blockEspHybridTracer(
                                                    new Vec3d(
                                                            offset.x + 0.5,
                                                            offset.y + 0.5,
                                                            offset.z + 0.5),
                                                    outlineColor,
                                                    tracerWidth);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getBlockId(Block block) {
        return Registries.BLOCK.getId(block).toString();
    }

    private static PistonEspRenderer.PistonOffsetPos getRenderedPosition(
            BlockPos pos, PistonBlockEntity piston) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        return new PistonEspRenderer.PistonOffsetPos(
                pos.getX() + piston.getRenderOffsetX(tickProgress),
                pos.getY() + piston.getRenderOffsetY(tickProgress),
                pos.getZ() + piston.getRenderOffsetZ(tickProgress));
    }

    private record PistonOffsetPos(double x, double y, double z) {}
}
