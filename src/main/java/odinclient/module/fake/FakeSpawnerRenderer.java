package odinclient.module.fake;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FakeSpawnerRenderer {
    private static LivingEntity spawnerMob;
    private static FakeSpawner.MobType spawnerMobType;
    private static Object cachedWorld;
    private static BlockPos cachedCenter;
    private static int cachedRadius;
    private static long cachedTimeNanos;
    private static final List<BlockPos> glassPositions = new ArrayList<>();
    private static final Set<BlockPos> glassPositionSet = new HashSet<>();

    private FakeSpawnerRenderer() {}

    public static void render(
            MatrixStack matrices, WorldRenderState worldState, OrderedRenderCommandQueue queue) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (FakeSpawner.isEnabled() && client.world != null && client.player != null) {
            FakeSpawner.MobType mobType = FakeSpawner.getMobType();
            if (spawnerMob == null
                    || spawnerMob.getEntityWorld() != client.world
                    || spawnerMobType != mobType) {
                if (!(mobType.getEntityType().create(client.world, SpawnReason.NATURAL)
                        instanceof LivingEntity livingEntity)) {
                    return;
                }

                spawnerMob = livingEntity;
                spawnerMobType = mobType;
            }

            spawnerMob.bodyYaw = 0.0F;
            spawnerMob.lastBodyYaw = 0.0F;
            spawnerMob.headYaw = 0.0F;
            float tickProgress = client.getRenderTickCounter().getTickProgress(false);
            Vec3d camera = worldState.cameraRenderState.pos;
            float age = (float) (client.world.getTime() % 400L) + tickProgress;
            float rotation = age * 32.0F % 360.0F;
            float bob = MathHelper.abs(MathHelper.sin(age * 0.15F)) * 0.06F;
            updateSpawnerCache(
                    client, client.player.getBlockPos(), FakeSpawner.getRenderDistance());
            EntityRenderManager dispatcher = client.getEntityRenderDispatcher();

            for (BlockPos pos : glassPositions) {
                if (client.world.getBlockState(pos).isOf(Blocks.YELLOW_STAINED_GLASS)) {
                    matrices.push();
                    matrices.translate(
                            pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                    Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
                    client.getBlockRenderManager()
                            .renderBlockAsEntity(
                                    Blocks.SPAWNER.getDefaultState(),
                                    matrices,
                                    consumers,
                                    15728880,
                                    OverlayTexture.DEFAULT_UV);
                    consumers.draw();
                    matrices.pop();
                    double x = pos.getX() + 0.5;
                    double y = pos.getY() + 0.1 + bob;
                    double z = pos.getZ() + 0.5;
                    spawnerMob.setPos(x, y, z);
                    spawnerMob.lastRenderX = x;
                    spawnerMob.lastRenderY = y;
                    spawnerMob.lastRenderZ = z;
                    matrices.push();
                    matrices.translate(x - camera.x, y - camera.y, z - camera.z);
                    matrices.scale(mobType.scale(), mobType.scale(), mobType.scale());
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-45.0F));
                    dispatcher.render(
                            dispatcher.getAndUpdateRenderState(spawnerMob, tickProgress),
                            worldState.cameraRenderState,
                            0.0,
                            0.0,
                            0.0,
                            matrices,
                            queue);
                    matrices.pop();
                    if (client.world.getRandom().nextFloat() < 0.06F) {
                        double particleX =
                                pos.getX() - 0.1 + client.world.getRandom().nextDouble() * 1.2;
                        double particleY =
                                pos.getY() - 0.1 + client.world.getRandom().nextDouble() * 1.2;
                        double particleZ =
                                pos.getZ() - 0.1 + client.world.getRandom().nextDouble() * 1.2;
                        client.world.addParticleClient(
                                ParticleTypes.FLAME,
                                particleX,
                                particleY,
                                particleZ,
                                0.0,
                                0.0,
                                0.0);
                    }
                }
            }
        }
    }

    private static void updateSpawnerCache(MinecraftClient client, BlockPos center, int radius) {
        long now = System.nanoTime();
        if (cachedWorld == client.world
                && cachedCenter != null
                && cachedRadius == radius
                && Math.abs(center.getX() - cachedCenter.getX()) < 2
                && Math.abs(center.getY() - cachedCenter.getY()) < 2
                && Math.abs(center.getZ() - cachedCenter.getZ()) < 2
                && now - cachedTimeNanos < 500000000L) {
            scanForSpawnerBlocks(client, center, Math.min(radius, 7));
        } else {
            cachedWorld = client.world;
            cachedCenter = center;
            cachedRadius = radius;
            cachedTimeNanos = now;
            glassPositions.clear();
            glassPositionSet.clear();
            scanForSpawnerBlocks(client, center, radius);
        }
    }

    private static void scanForSpawnerBlocks(MinecraftClient client, BlockPos center, int radius) {
        int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        BlockPos pos = center.add(dx, dy, dz);
                        if (client.world.getBlockState(pos).isOf(Blocks.YELLOW_STAINED_GLASS)
                                && glassPositionSet.add(pos)) {
                            glassPositions.add(pos);
                        }
                    }
                }
            }
        }
    }
}
