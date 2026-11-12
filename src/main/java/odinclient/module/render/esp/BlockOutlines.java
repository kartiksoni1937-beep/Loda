package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import odinclient.event.EventHandler;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;

@Environment(EnvType.CLIENT)
public final class BlockOutlines extends Module {
    private static BlockOutlines instance;
    private final Setting<EntityColor> color;
    private final Setting<Boolean> fullOutline;

    public BlockOutlines(Category category) {
        super(
                category,
                "block-outlines",
                "Customizes the outline of the block under your crosshair.");
        this.title = "Block Outlines";
        instance = this;
        SettingGroup general = this.settings.getGeneral();
        this.color =
                general.add(
                        new ColorSetting.Builder()
                                .name("outline-color")
                                .description("Color used for the selected block outline.")
                                .defaultValue(new EntityColor(190, 85, 255, 255))
                                .build());
        this.fullOutline =
                general.add(
                        new BooleanSetting.Builder()
                                .name("full-outline")
                                .description(
                                        "Shows every edge, including the edges hidden behind the"
                                            + " block.")
                                .defaultValue(false)
                                .build());
    }

    public static int getOutlineColor(int fallback) {
        BlockOutlines module = instance;
        if (module != null
                && module.isActive()
                && !Boolean.TRUE.equals(module.fullOutline.getValue())) {
            EntityColor outlineColor = module.color.getValue();
            return outlineColor == null ? fallback : outlineColor.toArgb();
        } else {
            return fallback;
        }
    }

    public static boolean isFullOutline() {
        BlockOutlines module = instance;
        return module != null
                && module.isActive()
                && Boolean.TRUE.equals(module.fullOutline.getValue());
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (Boolean.TRUE.equals(this.fullOutline.getValue())
                && this.mc.world != null
                && this.mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() != Type.MISS) {
            BlockPos blockPos = hit.getBlockPos();
            BlockState state = this.mc.world.getBlockState(blockPos);
            if (!state.isAir()) {
                Entity entity = this.mc.getCameraEntity();
                if (entity == null) {
                    entity = this.mc.player;
                }

                if (entity != null) {
                    VoxelShape shape =
                            state.getOutlineShape(this.mc.world, blockPos, ShapeContext.of(entity));
                    if (!shape.isEmpty()) {
                        EntityColor entityColor = this.color.getValue();
                        if (entityColor != null) {
                            Color edgeColor =
                                    new Color(
                                            entityColor.r,
                                            entityColor.g,
                                            entityColor.b,
                                            entityColor.a);
                            double originX = blockPos.getX();
                            double originY = blockPos.getY();
                            double originZ = blockPos.getZ();
                            shape.forEachEdge(
                                    (x1, y1, z1, x2, y2, z2) ->
                                            event.renderer.stableLine(
                                                    new Vec3d(
                                                            originX + x1,
                                                            originY + y1,
                                                            originZ + z1),
                                                    new Vec3d(
                                                            originX + x2,
                                                            originY + y2,
                                                            originZ + z2),
                                                    edgeColor,
                                                    1.15F));
                        }
                    }
                }
            }
        }
    }
}
