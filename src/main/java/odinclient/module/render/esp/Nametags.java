package odinclient.module.render.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.event.TickEvent;
import odinclient.manager.FriendList;
import odinclient.module.Module;
import odinclient.render.CameraDistance;
import odinclient.render.Color;
import odinclient.render.EntityColor;
import odinclient.render.RectRenderer;
import odinclient.render.TextBackend;
import odinclient.render.projection.ScreenProjection;
import odinclient.setting.FontSettings;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.ColorSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.util.EntityUtils;
import odinclient.util.ItemNames;
import odinclient.util.ItemRenderUtils;
import odinclient.util.PingUtil;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class Nametags extends Module {
    private final SettingGroup generalSettings = this.settings.getGeneral();
    private final SettingGroup playerSettings = this.settings.addGroup("Players");
    private final SettingGroup colorSettings = this.settings.addGroup("Colors");
    private final Setting<Boolean> players =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("players")
                            .description("Shows a nametag above players.")
                            .defaultValue(true)
                            .build());
    private final Setting<Boolean> items =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("items")
                            .description(
                                    "Shows the item name and stack amount above dropped items.")
                            .defaultValue(false)
                            .build());
    private final Setting<Boolean> spawners =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("spawners")
                            .description("Shows the mob name above loaded spawners.")
                            .defaultValue(false)
                            .build());
    private final Setting<Double> scale =
            this.generalSettings.add(
                    new NumberSetting.Builder()
                            .name("scale")
                            .description("Size of all nametags.")
                            .defaultValue(1.0)
                            .min(0.25)
                            .sliderRange(0.25, 3.0)
                            .build());
    private final Setting<Double> heightOffset =
            this.generalSettings.add(
                    new NumberSetting.Builder()
                            .name("height-offset")
                            .description(
                                    "Moves the complete player nametag higher above the player.")
                            .defaultValue(0.8)
                            .range(0.0, 2.0)
                            .sliderRange(0.0, 2.0)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<Boolean> ignoreSelf =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("ignore-self")
                            .description("Does not render your own nametag in third person.")
                            .defaultValue(true)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<Boolean> ignoreFriends =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("ignore-friends")
                            .description("Does not render nametags for friends.")
                            .defaultValue(false)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<Double> maxRange =
            this.generalSettings.add(
                    new NumberSetting.Builder()
                            .name("max-range")
                            .description("Maximum nametag render distance.")
                            .defaultValue(200.0)
                            .min(1.0)
                            .sliderRange(1.0, 500.0)
                            .build());
    private final Setting<Boolean> health =
            this.playerSettings.add(
                    new BooleanSetting.Builder()
                            .name("health")
                            .description("Shows the player's health.")
                            .defaultValue(true)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<Boolean> ping =
            this.playerSettings.add(
                    new BooleanSetting.Builder()
                            .name("ping")
                            .description("Shows the player's ping.")
                            .defaultValue(true)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<Boolean> distance =
            this.playerSettings.add(
                    new BooleanSetting.Builder()
                            .name("distance")
                            .description("Shows the distance to the player.")
                            .defaultValue(false)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<Boolean> armor =
            this.playerSettings.add(
                    new BooleanSetting.Builder()
                            .name("armor")
                            .description("Shows main hand, armor and offhand above the nametag.")
                            .defaultValue(true)
                            .visibleWhen(this.players::getValue)
                            .build());
    private final Setting<EntityColor> nameColor =
            this.colorSettings.add(
                    new ColorSetting.Builder()
                            .name("name-color")
                            .description("Color used for player and item names.")
                            .defaultValue(new EntityColor(255, 255, 255, 255))
                            .build());
    private final Setting<EntityColor> friendColor =
            this.colorSettings.add(
                    new ColorSetting.Builder()
                            .name("friend-color")
                            .description("Color used for friends.")
                            .defaultValue(new EntityColor(0, 190, 255, 255))
                            .build());
    private final Setting<EntityColor> background =
            this.colorSettings.add(
                    new ColorSetting.Builder()
                            .name("background")
                            .description("Nametag background color.")
                            .defaultValue(new EntityColor(0, 0, 0, 120))
                            .build());
    private final Color lowHealthColor = new Color(255, 45, 45);
    private final Color midHealthColor = new Color(255, 180, 35);
    private final Color highHealthColor = new Color(40, 255, 80);
    private final Color distanceColor = new Color(180, 180, 180);
    private final Color stackCountColor = new Color(255, 210, 65);
    private final Color healthBarBackColor = new Color(35, 35, 35, 230);
    private final Vector3d projectedPos = new Vector3d();
    private final List<Entity> visibleEntities = new ArrayList<>();
    private final List<Nametags.SpawnerLabel> spawnerLabels = new ArrayList<>();
    private int spawnerScanTicks;

    public Nametags() {
        super(
                Categories.MAIN,
                "name-tags",
                "Shows player equipment and stats, dropped item amounts and spawner names.");
        super.title = "Nametags";
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        this.visibleEntities.clear();
        if (this.mc.world != null && this.mc.player != null) {
            for (Entity entity : this.mc.world.getEntities()) {
                if (!(entity instanceof PlayerEntity player
                                ? !this.players.getValue()
                                        || this.ignoreSelf.getValue() && player == this.mc.player
                                        || this.ignoreFriends.getValue()
                                                && FriendList.getInstance().isFriend(player)
                                : !(entity instanceof ItemEntity) || !this.items.getValue())
                        && CameraDistance.isWithinDistance(entity, this.maxRange.getValue())) {
                    this.visibleEntities.add(entity);
                }
            }

            this.visibleEntities.sort(Comparator.comparingDouble(CameraDistance::distanceToCamera));
            if (!this.spawners.getValue()) {
                this.spawnerLabels.clear();
                this.spawnerScanTicks = 0;
            } else if (this.spawnerScanTicks++ >= 20) {
                this.spawnerScanTicks = 0;
                this.scanSpawners();
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            boolean customFont = FontSettings.getInstance().customFont.getValue();

            for (int i = this.visibleEntities.size() - 1; i >= 0; i--) {
                Entity entity = this.visibleEntities.get(i);
                EntityUtils.getInterpolatedPos(this.projectedPos, entity, event.tickDelta);
                if (entity instanceof PlayerEntity player) {
                    this.projectedPos.add(
                            0.0,
                            entity.getEyeHeight(entity.getPose()) + this.heightOffset.getValue(),
                            0.0);
                    if (ScreenProjection.project(this.projectedPos, this.scale.getValue())) {
                        this.renderPlayerTag(event, player, customFont);
                    }
                } else if (entity instanceof ItemEntity itemEntity) {
                    this.projectedPos.add(0.0, entity.getEyeHeight(entity.getPose()) + 0.2, 0.0);
                    if (ScreenProjection.project(this.projectedPos, this.scale.getValue())) {
                        this.renderItemTag(event, itemEntity.getStack(), customFont);
                    }
                }
            }

            if (this.spawners.getValue()) {
                this.renderSpawnerTags(event, customFont);
            }
        }
    }

    private void renderPlayerTag(Render2DEvent event, PlayerEntity player, boolean customFont) {
        TextBackend text = TextBackend.get();
        ScreenProjection.pushTransformTo(this.projectedPos, event.context);
        String name = player.getName().getString();
        int health = Math.round(player.getHealth() + player.getAbsorptionAmount());
        String healthText = " " + health;
        int ping = Math.max(0, PingUtil.getPing(player));
        String pingText = " " + ping + "ms";
        String distanceText =
                " " + Math.round(CameraDistance.distanceToCamera(player) * 10.0) / 10.0 + "m";
        float maxHealth = player.getMaxHealth() + player.getAbsorptionAmount();
        float healthRatio = maxHealth <= 0.0F ? 0.0F : Math.min(1.0F, health / maxHealth);
        Color healthColor =
                healthRatio <= 0.33F
                        ? this.lowHealthColor
                        : (healthRatio <= 0.66F ? this.midHealthColor : this.highHealthColor);
        Color pingColor =
                ping <= 100
                        ? this.highHealthColor
                        : (ping <= 200 ? this.midHealthColor : this.lowHealthColor);
        double width = text.getWidth(name, customFont);
        if (this.health.getValue()) {
            width += text.getWidth(healthText, customFont);
        }

        if (this.ping.getValue()) {
            width += text.getWidth(pingText, customFont);
        }

        if (this.distance.getValue()) {
            width += text.getWidth(distanceText, customFont);
        }

        double height = text.getHeight(customFont);
        double x = -width / 2.0;
        double y = -height;
        this.drawBackground(x, y, width, height);
        text.begin();
        Color nameColorValue =
                FriendList.getInstance().isFriend(player)
                        ? this.friendColor.getValue()
                        : this.nameColor.getValue();
        double cursor = text.render(name, x, y, nameColorValue, customFont);
        if (this.health.getValue()) {
            cursor = text.render(healthText, cursor, y, healthColor, customFont);
        }

        if (this.ping.getValue()) {
            cursor = text.render(pingText, cursor, y, pingColor, customFont);
        }

        if (this.distance.getValue()) {
            text.render(distanceText, cursor, y, this.distanceColor, customFont);
        }

        text.end();
        if (this.health.getValue()) {
            this.drawHealthBar(x, y + height + 1.0, width, healthRatio, healthColor);
        }

        if (this.armor.getValue()) {
            this.renderEquipment(event, player, y);
        }

        ScreenProjection.popTransformFrom(event.context);
    }

    private void renderEquipment(Render2DEvent event, PlayerEntity player, double baseY) {
        ItemStack[] stacks = {
            player.getMainHandStack(),
            player.getEquippedStack(EquipmentSlot.HEAD),
            player.getEquippedStack(EquipmentSlot.CHEST),
            player.getEquippedStack(EquipmentSlot.LEGS),
            player.getEquippedStack(EquipmentSlot.FEET),
            player.getOffHandStack()
        };
        int count = 0;

        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                count++;
            }
        }

        if (count != 0) {
            double spacing = 34.0;
            double x = -(count * spacing) / 2.0;
            double y = baseY - 37.0;

            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    ItemRenderUtils.drawItem(
                            event.context, stack, (int) x, (int) y, 2.0F, true, null, false);
                    x += spacing;
                }
            }
        }
    }

    private void renderItemTag(Render2DEvent event, ItemStack stack, boolean customFont) {
        if (!stack.isEmpty()) {
            TextBackend text = TextBackend.get();
            ScreenProjection.pushTransformTo(this.projectedPos, event.context);
            String name = ItemNames.getStackName(stack);
            String countText = " x" + stack.getCount();
            double width = text.getWidth(name, customFont) + text.getWidth(countText, customFont);
            double height = text.getHeight(customFont);
            double x = -width / 2.0;
            double y = -height;
            this.drawBackground(x, y, width, height);
            text.begin();
            double cursor = text.render(name, x, y, this.nameColor.getValue(), customFont);
            text.render(countText, cursor, y, this.stackCountColor, customFont);
            text.end();
            ScreenProjection.popTransformFrom(event.context);
        }
    }

    private void scanSpawners() {
        this.spawnerLabels.clear();
        int chunkRadius =
                Math.min(32, Math.max(1, (int) Math.ceil(this.maxRange.getValue() / 16.0)));
        int chunkX = this.mc.player.getChunkPos().x;
        int chunkZ = this.mc.player.getChunkPos().z;

        for (int cx = chunkX - chunkRadius; cx <= chunkX + chunkRadius; cx++) {
            for (int cz = chunkZ - chunkRadius; cz <= chunkZ + chunkRadius; cz++) {
                WorldChunk chunk = this.mc.world.getChunk(cx, cz);
                if (chunk != null) {
                    for (Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        if (entry.getValue() instanceof MobSpawnerBlockEntity spawner) {
                            BlockPos pos = entry.getKey().toImmutable();
                            if (CameraDistance.isPosWithinDistance(
                                    pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5,
                                    this.maxRange.getValue())) {
                                this.spawnerLabels.add(
                                        new Nametags.SpawnerLabel(
                                                pos, this.getSpawnerLabel(spawner)));
                            }
                        }
                    }
                }
            }
        }
    }

    private void renderSpawnerTags(Render2DEvent event, boolean customFont) {
        TextBackend text = TextBackend.get();

        for (Nametags.SpawnerLabel label : this.spawnerLabels) {
            Vector3d worldPos =
                    new Vector3d(
                            label.pos.getX() + 0.5,
                            label.pos.getY() + 1.25,
                            label.pos.getZ() + 0.5);
            if (ScreenProjection.project(worldPos, this.scale.getValue())) {
                ScreenProjection.pushTransformTo(worldPos, event.context);
                double width = text.getWidth(label.name, customFont);
                double height = text.getHeight(customFont);
                double x = -width / 2.0;
                double y = -height;
                this.drawBackground(x, y, width, height);
                text.begin();
                text.render(label.name, x, y, this.nameColor.getValue(), customFont);
                text.end();
                ScreenProjection.popTransformFrom(event.context);
            }
        }
    }

    private String getSpawnerLabel(MobSpawnerBlockEntity spawner) {
        try {
            NbtCompound nbt =
                    spawner.createNbtWithIdentifyingData(this.mc.world.getRegistryManager());
            Optional<NbtCompound> spawnData = nbt.getCompound("SpawnData");
            if (spawnData.isPresent()) {
                Optional<NbtCompound> entityData = spawnData.get().getCompound("entity");
                if (entityData.isPresent()) {
                    String id = entityData.get().getString("id").orElse("");
                    if (!id.isEmpty()) {
                        return this.formatEntityId(id) + " Spawner";
                    }
                }
            }
        } catch (Exception e) {
        }

        return "Spawner";
    }

    private String formatEntityId(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder builder = new StringBuilder();

        for (String word : path.replace('_', ' ').split(" ")) {
            if (!word.isEmpty()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }

                builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }

        return builder.toString();
    }

    private void drawBackground(double x, double y, double width, double height) {
        RectRenderer.COLOR.begin();
        RectRenderer.COLOR.fillRect(
                x - 1.0, y - 1.0, width + 2.0, height + 2.0, this.background.getValue());
        RectRenderer.COLOR.render();
    }

    private void drawHealthBar(double x, double y, double width, float ratio, Color color) {
        double barWidth = width + 2.0;
        RectRenderer.COLOR.begin();
        RectRenderer.COLOR.fillRect(x - 1.0, y, barWidth, 2.0, this.healthBarBackColor);
        RectRenderer.COLOR.fillRect(x - 1.0, y, barWidth * ratio, 2.0, color);
        RectRenderer.COLOR.render();
    }

    @Override
    public void onDeactivate() {
        this.visibleEntities.clear();
        this.spawnerLabels.clear();
        this.spawnerScanTicks = 0;
    }

    public String getInfo() {
        return Integer.toString(this.visibleEntities.size() + this.spawnerLabels.size());
    }

    @Environment(EnvType.CLIENT)
    private record SpawnerLabel(BlockPos pos, String name) {}
}
