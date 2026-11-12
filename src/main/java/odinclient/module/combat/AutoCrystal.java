package odinclient.module.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.world.World;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.NumberSetting;
import odinclient.util.WorldUtils;

import org.lwjgl.glfw.GLFW;

import java.util.List;

@Environment(EnvType.CLIENT)
public class AutoCrystal extends Module {
    private static AutoCrystal instance;
    private final Setting<Double> breakDelay;
    private final Setting<Double> placeDelay;
    private double breakTimer;
    private double placeTimer;
    private int placeWait;
    private BlockPos lastPlacePos;
    private int lastBrokenCrystalId = -1;
    private boolean wasActing;

    public AutoCrystal(Category category) {
        super(
                category,
                "auto-crystal",
                "Hold RMB to place on existing bases; breaks only crystals directly under your"
                    + " crosshair.");
        instance = this;
        SettingGroup general = this.settings.getGeneral();
        this.breakDelay =
                general.add(
                        new NumberSetting.Builder()
                                .name("break-delay")
                                .description("Ticks between crystal breaks.")
                                .defaultValue(1.0)
                                .range(0.0, 20.0)
                                .sliderRange(0.0, 20.0)
                                .build());
        this.placeDelay =
                general.add(
                        new NumberSetting.Builder()
                                .name("place-delay")
                                .description("Ticks between crystal placements.")
                                .defaultValue(1.0)
                                .range(0.0, 20.0)
                                .sliderRange(0.0, 20.0)
                                .build());
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        MinecraftClient client = this.mc;
        if (this.shouldAct()) {
            this.wasActing = true;
            this.tickDelays();
            HitResult target = client.crosshairTarget;
            if (target instanceof EntityHitResult
                    && ((EntityHitResult) target).getEntity() instanceof EndCrystalEntity crystal) {
                if (this.tryBreakCrystal(client, crystal)) {
                    this.lastBrokenCrystalId = crystal.getId();
                }
            } else if (target instanceof BlockHitResult blockHit
                    && blockHit.getType() == Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                if (isCrystalBase(client.world, pos)) {
                    this.tryPlaceCrystal(client, blockHit, pos);
                }
            }
        } else {
            this.resetIfActive();
        }
    }

    public boolean shouldAct() {
        MinecraftClient client = this.mc;
        if (!this.isActive()
                || client.player == null
                || client.world == null
                || client.interactionManager == null
                || client.currentScreen != null
                || !client.player.isAlive()
                || client.player.isSpectator()
                || !this.isUseButtonHeld(client)) {
            return false;
        }

        boolean holdingCrystal = client.player.getMainHandStack().isOf(Items.END_CRYSTAL);
        boolean mainHandEmpty = client.player.getMainHandStack().isEmpty();
        boolean offHandCrystal = client.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        if (client.player.isUsingItem() || !holdingCrystal && !(mainHandEmpty && offHandCrystal)) {
            return false;
        }

        HitResult target = client.crosshairTarget;
        if (target instanceof EntityHitResult
                && ((EntityHitResult) target).getEntity() instanceof EndCrystalEntity crystal) {
            return crystal.isAlive()
                    && !crystal.isRemoved()
                    && this.isCrystalUnderCrosshair(client, crystal);
        } else if (!(client.crosshairTarget instanceof BlockHitResult blockHit)) {
            return false;
        } else {
            return blockHit.getType() == Type.BLOCK
                    && isCrystalBase(client.world, blockHit.getBlockPos())
                    && this.isPlaceTargetValid(client, blockHit);
        }
    }

    public static boolean isActing() {
        AutoCrystal module = instance;
        return module != null && module.shouldAct();
    }

    private void tryPlaceCrystal(MinecraftClient client, BlockHitResult hit, BlockPos pos) {
        if (this.placeWait > 0) {
            return;
        }

        if (!client.world.getBlockState(hit.getBlockPos().up()).isAir()) {
            return;
        }

        if (pos.equals(this.lastPlacePos) && this.placeTimer > 0.0) {
            return;
        }

        if (this.isPlaceTargetValid(client, hit)) {
            if (getCrystalAbove(client.world, pos) == null) {
                this.lastBrokenCrystalId = -1;
                if (this.placeTimer <= 0.0 && canPlaceCrystalAt(client.world, pos)) {
                    Hand hand = this.getCrystalHand(client);
                    if (hand != null) {
                        interactBlock(client, hit, hand, true);
                        this.lastPlacePos = pos;
                        this.placeTimer = this.placeTimer + this.placeDelay.getValue();
                        // One placement per tick, and nothing else this tick.
                        this.placeWait = 1;
                        return;
                    }
                }
            }
        }
    }

    private boolean tryBreakCrystal(MinecraftClient client, EndCrystalEntity crystal) {
        if (this.breakTimer > 0.0 || crystal.getId() == this.lastBrokenCrystalId) {
            return false;
        } else if (crystal.isAlive()
                && !crystal.isRemoved()
                && this.isCrystalUnderCrosshair(client, crystal)) {
            WorldUtils.hitEntity(crystal);
            client.player.swingHand(Hand.MAIN_HAND);
            this.breakTimer = this.breakTimer + this.breakDelay.getValue();
            return true;
        } else {
            return false;
        }
    }

    private boolean isCrystalUnderCrosshair(MinecraftClient client, EndCrystalEntity crystal) {
        if (client.getCameraEntity() != client.player) {
            return false;
        } else {
            double range = client.player.getEntityInteractionRange();
            Vec3d start = client.player.getEyePos();
            Vec3d direction = client.player.getRotationVec(1.0F).multiply(range);
            Vec3d end = start.add(direction);
            BlockHitResult blockHit =
                    client.world.raycast(
                            new RaycastContext(
                                    start,
                                    end,
                                    ShapeType.OUTLINE,
                                    FluidHandling.NONE,
                                    client.player));
            double maxDistanceSq = range * range;
            if (blockHit.getType() == Type.BLOCK) {
                maxDistanceSq = Math.max(0.0, start.squaredDistanceTo(blockHit.getPos()) - 1.0E-6);
            }

            EntityHitResult entityHit =
                    ProjectileUtil.raycast(
                            client.player,
                            start,
                            end,
                            client.player.getBoundingBox().stretch(direction).expand(1.0),
                            e -> !e.isSpectator() && e.canHit(),
                            maxDistanceSq);
            return entityHit != null
                    && entityHit.getEntity() == crystal
                    && start.squaredDistanceTo(entityHit.getPos()) <= maxDistanceSq;
        }
    }

    private boolean isPlaceTargetValid(MinecraftClient client, BlockHitResult hit) {
        if (!hit.isInsideBlock() && client.getCameraEntity() == client.player) {
            double range = client.player.getBlockInteractionRange();
            double rangeSq = range * range;
            if (client.player.getEyePos().squaredDistanceTo(hit.getPos()) > rangeSq) {
                return false;
            } else if (!(client.player.raycast(range, 1.0F, false)
                    instanceof BlockHitResult blockHit)) {
                return false;
            } else {
                return blockHit.getType() == Type.BLOCK
                        && blockHit.getBlockPos().equals(hit.getBlockPos())
                        && blockHit.getSide() == hit.getSide()
                        && !blockHit.isInsideBlock();
            }
        } else {
            return false;
        }
    }

    private Hand getCrystalHand(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            return Hand.MAIN_HAND;
        } else {
            return player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null;
        }
    }

    private static boolean isCrystalBase(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    private static EndCrystalEntity getCrystalAbove(World world, BlockPos pos) {
        BlockPos above = pos.up();
        List<EndCrystalEntity> crystals =
                world.getEntitiesByClass(
                        EndCrystalEntity.class,
                        new Box(
                                above.getX() + 0.125,
                                above.getY() - 0.1,
                                above.getZ() + 0.125,
                                above.getX() + 0.875,
                                above.getY() + 2.5,
                                above.getZ() + 0.875),
                        e -> true);
        return crystals.isEmpty() ? null : crystals.get(0);
    }

    private static boolean canPlaceCrystalAt(World world, BlockPos pos) {
        BlockPos above = pos.up();
        if (!world.getBlockState(above).isAir()) {
            return false;
        } else {
            return world.getNonSpectatingEntities(
                            Entity.class,
                            new Box(
                                    above.getX(),
                                    above.getY(),
                                    above.getZ(),
                                    above.getX() + 1.0,
                                    above.getY() + 2.0,
                                    above.getZ() + 1.0))
                    .isEmpty();
        }
    }

    private static void interactBlock(
            MinecraftClient client, BlockHitResult hit, Hand hand, boolean swing) {
        if (WorldUtils.interactBlock(hit, hand)
                && swing) {
            client.player.swingHand(hand);
        }
    }

    private void tickDelays() {
        if (this.breakTimer > 0.0) {
            this.breakTimer--;
        }

        if (this.placeTimer > 0.0) {
            this.placeTimer--;
        }

        if (this.placeWait > 0) {
            this.placeWait--;
        }
    }

    private boolean isUseButtonHeld(MinecraftClient client) {
        return client.isWindowFocused()
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 1) == 1;
    }

    private void resetIfActive() {
        if (this.wasActing) {
            this.breakTimer = 0.0;
            this.placeTimer = 0.0;
            this.lastBrokenCrystalId = -1;
            this.wasActing = false;
        }
    }

    private void resetState() {
        this.breakTimer = 0.0;
        this.placeTimer = 0.0;
        this.lastBrokenCrystalId = -1;
        this.wasActing = false;
    }
}
