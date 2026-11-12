package odinclient.module.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import odinclient.event.EventHandler;
import odinclient.event.Render2DEvent;
import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;
import odinclient.ui.draw.BoldText;
import odinclient.ui.draw.CircleRenderer;
import odinclient.ui.draw.DashboardRenderer;
import odinclient.ui.draw.SdfShapeRenderer;
import odinclient.ui.hud.HudLayout;
import odinclient.ui.hud.HudLayoutEditor;
import odinclient.ui.hud.ModuleNotifications;
import odinclient.ui.screen.ClickGuiScreen;
import odinclient.ui.screen.IntroSplashScreen;
import odinclient.ui.screen.OdinGuiScreen;
import odinclient.ui.screen.PanelGuiScreen;
import odinclient.util.input.KeyNames;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

@Environment(EnvType.CLIENT)
public final class HudModule extends Module {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int TEXT_PRIMARY = -723974;
    private static final int TEXT_SECONDARY = -5789512;
    private static final int TEXT_MUTED = -9275508;
    private static final int PANEL_FILL_COLOR = 1120288;
    private static final int PANEL_OUTLINE_COLOR = 527122;
    private static final int DURABILITY_HIGH_COLOR = -11603801;
    private static final int DURABILITY_MEDIUM_COLOR = -18355;
    private static final int DURABILITY_LOW_COLOR = -39558;
    private static final float METRICS_TEXT_SCALE = 0.48F;
    private static final long EFFECT_REFRESH_NANOS = 250000000L;
    private static final long PING_REFRESH_NANOS = 200000000L;
    private static final long CLOCK_REFRESH_NANOS = 1000000000L;
    private static final long MODULE_REFRESH_NANOS = 1000000000L;
    private static final EquipmentSlot[] ARMOR_SLOTS;
    private final Setting<Boolean> watermark;
    private final Setting<Boolean> info;
    private final Setting<Boolean> coords;
    private final Setting<Boolean> moduleList;
    private final Setting<Boolean> potionEffects;
    private final Setting<Boolean> armor;
    private final Setting<Boolean> keybinds;
    private final Setting<Boolean> radar;
    private final Setting<Boolean> background;
    private final Setting<Integer> radarRange;
    private final Setting<Integer> radarDiameter;
    private final Setting<Double> backgroundOpacity;
    private final Setting<Double> hudScale;
    private final List<StatusEffectInstance> activeEffects = new ArrayList<>();
    private final List<String> effectLabels = new ArrayList<>();
    private final List<String> effectDurationTexts = new ArrayList<>();
    private final List<Integer> effectDurationWidths = new ArrayList<>();
    private final List<Integer> effectColors = new ArrayList<>();
    private final List<HudModule.ModuleListEntry> moduleEntries = new ArrayList<>();
    private final List<HudModule.KeybindEntry> keybindEntries = new ArrayList<>();
    private final Map<Module, Float> moduleAnimations = new IdentityHashMap<>();
    private int effectsHash = -2147483648;
    private int modulesHash = -2147483648;
    private int effectsPanelWidth = 92;
    private int keybindsPanelWidth = 78;
    private int effectCountWidth = 1;
    private int keybindCountWidth = 1;
    private int metricsWidth = 1;
    private int clockWidth = 1;
    private int odinWidth = 1;
    private int watermarkWidth = 1;
    private int coordsWidth;
    private int lastX = -2147483648;
    private int lastY = -2147483648;
    private int lastZ = -2147483648;
    private int compassLetterWidth = 1;
    private int cachedPing;
    private boolean widthsInitialized;
    private long nextEffectUpdate;
    private long nextModuleUpdate;
    private long nextPingUpdate;
    private long nextClockUpdate;
    private String metricsText = "";
    private String coordsText = "";
    private String clockText = "";
    private long lastFrameNanos = System.nanoTime();
    private boolean entriesBuilt;
    private boolean animating;
    private long lastToggleCounter = -9223372036854775808L;

    public HudModule(Category category) {
        super(category, "hud", "A minimal flat layout for essential in-game information.");
        this.title = "HUD";
        this.watermark =
                this.addToggle(
                        "watermark", "Shows the OdinClient identity in the telemetry bar.", true);
        this.info = this.addToggle("info", "Shows FPS, ping, speed and local time.", true);
        this.coords =
                this.addToggle(
                        "coords",
                        "Shows X, Y and Z below the statistics in the OdinClient panel.",
                        false);
        this.moduleList =
                this.addToggle("module-list", "Shows active modules as a clean flat list.", true);
        this.potionEffects =
                this.addToggle(
                        "potion-effects",
                        "Shows active status effects in a dedicated panel.",
                        true);
        this.armor = this.addToggle("armor", "Shows equipped armor in the equipment dock.", true);
        this.keybinds =
                this.addToggle(
                        "keybinds", "Shows configured module keybinds and their keys.", true);
        this.radar = this.addToggle("radar", "Shows the existing circular player radar.", true);
        this.background =
                this.addToggle(
                        "background",
                        "Draws the original v89 backgrounds behind HUD components.",
                        true);
        ModuleNotifications.registerSettings(this.settings);
        this.radarRange =
                this.settings
                        .getGeneral()
                        .add(
                                new IntSetting.Builder()
                                        .name("radar-range")
                                        .description("Maximum radar distance in blocks.")
                                        .defaultValue(32)
                                        .range(16, 256)
                                        .sliderRange(16, 256)
                                        .build());
        this.radarDiameter =
                this.settings
                        .getGeneral()
                        .add(
                                new IntSetting.Builder()
                                        .name("radar-diameter")
                                        .description("Diameter of the circular radar.")
                                        .defaultValue(56)
                                        .range(28, 128)
                                        .sliderRange(44, 128)
                                        .build());
        this.backgroundOpacity =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("background-opacity")
                                        .description("Opacity of the glass HUD panels.")
                                        .defaultValue(0.72)
                                        .range(0.0, 1.0)
                                        .sliderRange(0.0, 1.0)
                                        .build());
        this.hudScale =
                this.settings
                        .getGeneral()
                        .add(
                                new NumberSetting.Builder()
                                        .name("hud-scale")
                                        .description("Changes the size of the complete HUD.")
                                        .defaultValue(1.0)
                                        .range(0.5, 2.0)
                                        .sliderRange(0.5, 2.0)
                                        .build());
    }

    private Setting<Boolean> addToggle(String name, String description, boolean defaultValue) {
        return this.settings
                .getGeneral()
                .add(
                        new BooleanSetting.Builder()
                                .name(name)
                                .description(description)
                                .defaultValue(defaultValue)
                                .build());
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.shouldRender()) {
            DrawContext context = event.context;
            this.updateCache(System.nanoTime());
            float scale = this.hudScale.getValue().floatValue();
            int scaledWidth = Math.max(1, (int) (event.scaledWidth / scale));
            int scaledHeight = Math.max(1, (int) (event.scaledHeight / scale));
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);

            try {
                this.drawInfoPanel(context, scale);
                this.drawClock(context, scaledWidth, scale);
                int radarY = this.getRadarY();
                if (this.radar.getValue()) {
                    float[] radarOffset = HudLayoutEditor.getPosition("hud-radar");
                    this.drawRadar(
                            context, 8 + (int) radarOffset[0], radarY + (int) radarOffset[1]);
                    HudLayoutEditor.registerOffsetElement(
                            context,
                            "hud-radar",
                            "Radar",
                            8.0F + radarOffset[0],
                            radarY + radarOffset[1],
                            this.radarDiameter.getValue().intValue(),
                            this.radarDiameter.getValue().intValue(),
                            scale);
                }

                if (this.potionEffects.getValue()) {
                    int effectsY =
                            this.radar.getValue()
                                    ? radarY + this.radarDiameter.getValue() + 8
                                    : radarY;
                    float[] effectsOffset = HudLayoutEditor.getPosition("hud-effects");
                    this.drawStatusEffects(
                            context,
                            8 + (int) effectsOffset[0],
                            effectsY + (int) effectsOffset[1],
                            scale);
                }

                if (this.armor.getValue()) {
                    this.drawArmorDock(context, scaledWidth, scaledHeight);
                }

                if (this.moduleList.getValue()) {
                    float[] modulesOffset = HudLayoutEditor.getPosition("hud-modules");
                    this.drawModuleList(
                            context,
                            scaledWidth - 8 + (int) modulesOffset[0],
                            8 + (int) modulesOffset[1],
                            scale);
                }

                if (this.keybinds.getValue()) {
                    float[] keybindsOffset = HudLayoutEditor.getPosition("hud-keybinds");
                    this.drawKeybindList(
                            context,
                            4 + (int) keybindsOffset[0],
                            scaledHeight,
                            (int) keybindsOffset[1],
                            scale);
                }
            } catch (Throwable e) {
                context.getMatrices().popMatrix();
                throw e;
            }

            context.getMatrices().popMatrix();
        }
    }

    private boolean shouldRender() {
        return this.mc.player != null
                && this.mc.world != null
                && this.mc.options != null
                && (!ThemeModule.isPanelStyle() || this.mc.currentScreen == null)
                && !(this.mc.currentScreen instanceof ClickGuiScreen)
                && !(this.mc.currentScreen instanceof PanelGuiScreen)
                && !(this.mc.currentScreen instanceof OdinGuiScreen)
                && !(this.mc.currentScreen instanceof IntroSplashScreen)
                && !this.mc.getDebugHud().shouldShowDebugHud();
    }

    private void drawInfoPanel(DrawContext context, float scale) {
        HudLayout.HudPanelLayout layout =
                HudLayout.computeLayout(
                        this.watermark.getValue(),
                        this.info.getValue(),
                        this.coords.getValue(),
                        this.watermarkWidth,
                        this.metricsWidth,
                        this.coordsWidth);
        if (layout.height() != 0) {
            int width = layout.width();
            int height = layout.height();
            float[] offset = HudLayoutEditor.getPosition("hud-watermark");
            int x = 8 + (int) offset[0];
            int y = 8 + (int) offset[1];
            HudLayoutEditor.registerOffsetElement(
                    context, "hud-watermark", "Watermark", x, y, width, height, scale);
            this.drawPanelBackground(context, x, y, width, height, 6);
            if (layout.watermarkY() >= 0) {
                int textY = y + layout.watermarkY();
                this.drawScaledText(context, "ODIN", x + 5, textY, 0.52F, getThemeColor(0.05F));
                this.drawScaledText(
                        context, "CLIENT", x + 5 + this.odinWidth + 1, textY, 0.52F, -723974);
            }

            if (layout.metricsY() >= 0) {
                this.drawScaledText(
                        context, this.metricsText, x + 5, y + layout.metricsY(), 0.48F, -5789512);
            }

            if (layout.coordsY() >= 0) {
                this.drawScaledText(
                        context, this.coordsText, x + 5, y + layout.coordsY(), 0.48F, -5789512);
            }
        }
    }

    private void drawClock(DrawContext context, int scaledWidth, float scale) {
        if (this.info.getValue()) {
            String text = this.clockText;
            float[] offset = HudLayoutEditor.getPosition("hud-clock");
            int x = (scaledWidth - 50) / 2 + (int) offset[0];
            int y = 4 + (int) offset[1];
            HudLayoutEditor.registerOffsetElement(
                    context, "hud-clock", "Clock", x, y, 50, 16, scale);
            this.drawPanelBackground(context, x, y, 50, 16, 6);
            this.drawStretchedText(
                    context, text, this.clockWidth, x, y, 50, 16, 44, getThemeColor(0.75F));
        }
    }

    private void drawStatusEffects(DrawContext context, int x, int y, float scale) {
        List<StatusEffectInstance> effects = this.activeEffects;
        if (!effects.isEmpty()) {
            int width = this.effectsPanelWidth;
            int height = 17 + effects.size() * 13;
            HudLayoutEditor.registerOffsetElement(
                    context, "hud-effects", "Status effects", x, y, width, height, scale);
            this.drawPanelBackground(context, x, y, width, height, 4);
            this.drawScaledText(context, "STATUS EFFECTS", x + 7, y + 5, 0.48F, -5789512);
            int countRight = x + width - 6;
            this.drawTextEndingAt(
                    context,
                    Integer.toString(effects.size()),
                    this.effectCountWidth,
                    countRight,
                    y + 5,
                    0.48F,
                    getThemeColor(0.2F));
            int dividerColor =
                    ThemeModule.isPanelStyle() ? 1428894012 : withAlpha(getThemeColor(0.16F), 40);
            context.fill(x + 6, y + 13, x + width - 6, y + 14, dividerColor);
            int rowY = y + 15;
            int maxIndex = Math.max(1, effects.size() - 1);

            for (int i = 0; i < effects.size(); i++) {
                StatusEffectInstance effect = effects.get(i);
                context.fill(
                        x + 5,
                        rowY + 3,
                        x + 6,
                        rowY + 10,
                        withAlpha(this.effectColors.get(i), 210));
                context.drawGuiTexture(
                        RenderPipelines.GUI_TEXTURED,
                        InGameHud.getEffectTexture(effect.getEffectType()),
                        x + 8,
                        rowY + 2,
                        7,
                        7);
                this.drawScaledText(
                        context,
                        this.effectLabels.get(i),
                        x + 18,
                        rowY + 3,
                        0.48F,
                        withAlpha(-723974, 245));
                this.drawTextEndingAt(
                        context,
                        this.effectDurationTexts.get(i),
                        this.effectDurationWidths.get(i),
                        x + width - 7,
                        rowY + 3,
                        0.48F,
                        withAlpha(getThemeColor((float) i / maxIndex), 235));
                rowY += 13;
            }
        }
    }

    private int drawModuleList(DrawContext context, int right, int top, float scale) {
        List<HudModule.ModuleListEntry> entries = this.moduleEntries;
        if (entries.isEmpty()) {
            return top;
        } else {
            int maxWidth = 0;

            for (HudModule.ModuleListEntry moduleEntry : entries) {
                maxWidth = Math.max(maxWidth, moduleEntry.drawWidth() + 6);
            }

            HudLayoutEditor.registerOffsetElement(
                    context,
                    "hud-modules",
                    "Module list",
                    right - maxWidth,
                    top,
                    maxWidth,
                    entries.size() * 9,
                    scale);
            int maxIndex = Math.max(1, entries.size() - 1);
            int rowY = top;

            for (int i = 0; i < entries.size(); i++) {
                HudModule.ModuleListEntry entry = entries.get(i);
                float progress =
                        ThemeModule.areAnimationsEnabled()
                                ? this.moduleAnimations.getOrDefault(entry.module(), 1.0F)
                                : 1.0F;
                float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
                int slide = Math.round((1.0F - eased) * 8.0F);
                int textX = right - 2 + slide - entry.drawWidth();
                int backgroundX = textX - 2;
                int backgroundWidth = entry.drawWidth() + 4;
                int accentColor = getThemeColor((float) i / maxIndex);
                this.drawFadingBackground(context, backgroundX, rowY, backgroundWidth, 9, eased);
                this.drawScaledText(
                        context,
                        entry.title(),
                        textX,
                        rowY + 1,
                        0.61F,
                        withAlpha(-723974, Math.round(242.0F * eased)));
                SdfShapeRenderer.fill(
                        context,
                        right - 1 + slide,
                        rowY,
                        1.6F,
                        9.0F,
                        0.8F,
                        withAlpha(accentColor, Math.round(235.0F * eased)));
                rowY += 9;
            }

            return rowY;
        }
    }

    private int drawKeybindList(
            DrawContext context, int x, int screenHeight, int offsetY, float scale) {
        List<HudModule.KeybindEntry> entries = this.keybindEntries;
        if (entries.isEmpty()) {
            return screenHeight;
        } else {
            int width = this.keybindsPanelWidth;
            int height = 15 + entries.size() * 10 + 3;
            int y = Math.max(4, screenHeight - height - 4) + offsetY;
            HudLayoutEditor.registerOffsetElement(
                    context, "hud-keybinds", "Keybind list", x, y, width, height, scale);
            this.drawPanelBackground(context, x, y, width, height, 6);
            this.drawScaledText(context, "KEYBINDS", x + 6, y + 5, 0.48F, -5789512);
            int countRight = x + width - 6;
            this.drawTextEndingAt(
                    context,
                    Integer.toString(entries.size()),
                    this.keybindCountWidth,
                    countRight,
                    y + 5,
                    0.48F,
                    getThemeColor(0.42F));
            int dividerColor =
                    ThemeModule.isPanelStyle() ? 1428894012 : withAlpha(getThemeColor(0.4F), 40);
            context.fill(x + 6, y + 13, x + width - 6, y + 14, dividerColor);
            int rowY = y + 16;

            for (HudModule.KeybindEntry entry : entries) {
                this.drawScaledText(
                        context,
                        entry.title(),
                        x + 6,
                        rowY + 1,
                        0.46F,
                        entry.module().isActive() ? -723974 : -5789512);
                this.drawTextEndingAt(
                        context,
                        entry.key(),
                        entry.keyWidth(),
                        x + width - 6,
                        rowY + 1,
                        0.45F,
                        entry.module().isActive() ? getThemeColor(0.52F) : -9275508);
                rowY += 10;
            }

            return y + height;
        }
    }

    private void drawArmorDock(DrawContext context, int scaledWidth, int scaledHeight) {
        int count = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!this.mc.player.getEquippedStack(slot).isEmpty()) {
                count++;
            }
        }

        if (count != 0) {
            int width = 10 + count * 31;
            int x = scaledWidth - width - 8;
            int y = scaledHeight - 35;
            this.drawPanelBackground(context, x, y, width, 27, 6);
            int slotX = x + 7;

            for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
                ItemStack stack = this.mc.player.getEquippedStack(armorSlot);
                if (!stack.isEmpty()) {
                    this.drawScaledItem(context, stack, slotX + 4, y + 5, 0.78F);
                    int durability = getDurabilityPercent(stack);
                    int barColor =
                            durability >= 60 ? -11603801 : (durability >= 30 ? -18355 : -39558);
                    int barWidth = Math.max(1, Math.round(19 * durability / 100.0F));
                    DashboardRenderer.drawRoundedRect(
                            context, slotX + 3, y + 21, 19, 2, 1, withAlpha(barColor, 36));
                    DashboardRenderer.drawRoundedRect(
                            context, slotX + 3, y + 21, barWidth, 2, 1, barColor);
                    slotX += 31;
                }
            }
        }
    }

    private void updateCache(long now) {
        if (!this.widthsInitialized && this.mc.textRenderer != null) {
            this.odinWidth = this.scaledTextWidth("ODIN", 0.52F);
            this.watermarkWidth = this.odinWidth + 1 + this.scaledTextWidth("CLIENT", 0.52F);
            this.compassLetterWidth = this.scaledTextWidth("N", 0.55F);
            this.widthsInitialized = true;
        }

        if (now >= this.nextPingUpdate) {
            this.nextPingUpdate = now + 200000000L;
            this.cachedPing = this.ping();
        }

        if (this.info.getValue()) {
            String metrics =
                    this.mc.getCurrentFps()
                            + " FPS   "
                            + this.cachedPing
                            + " MS   "
                            + formatSpeed(this.speed())
                            + " BPS";
            if (!metrics.equals(this.metricsText)) {
                this.metricsText = metrics;
                this.metricsWidth = this.scaledTextWidth(this.metricsText, 0.48F);
            }
        }

        if (this.coords.getValue()) {
            int x = (int) Math.floor(this.mc.player.getX());
            int y = (int) Math.floor(this.mc.player.getY());
            int z = (int) Math.floor(this.mc.player.getZ());
            if (this.coordsText.isEmpty()
                    || x != this.lastX
                    || y != this.lastY
                    || z != this.lastZ) {
                this.lastX = x;
                this.lastY = y;
                this.lastZ = z;
                this.coordsText = HudLayout.formatCoords(x, y, z);
                this.coordsWidth = this.scaledTextWidth(this.coordsText, 0.48F);
            }
        }

        if (now >= this.nextClockUpdate) {
            this.nextClockUpdate = now + 1000000000L;
            this.clockText = LocalTime.now().format(CLOCK_FORMAT);
            this.clockWidth = Math.max(1, BoldText.width(this.mc.textRenderer, this.clockText));
        }

        if (now >= this.nextEffectUpdate) {
            this.nextEffectUpdate = now + 250000000L;
            int effectsHashValue = 1;

            for (StatusEffectInstance activeEffect : this.mc.player.getStatusEffects()) {
                effectsHashValue =
                        31
                                        * (31
                                                        * (31 * effectsHashValue
                                                                + System.identityHashCode(
                                                                        activeEffect))
                                                + activeEffect.getAmplifier())
                                + getEffectDurationDigits(activeEffect);
            }

            if (effectsHashValue != this.effectsHash) {
                this.effectsHash = effectsHashValue;
                this.activeEffects.clear();
                this.activeEffects.addAll(this.mc.player.getStatusEffects());
                this.activeEffects.sort(Comparator.comparing(HudModule::getEffectName));
                this.effectLabels.clear();
                this.effectColors.clear();
                this.effectsPanelWidth = 92;

                for (StatusEffectInstance effect : this.activeEffects) {
                    String effectName = getEffectName(effect).toUpperCase(Locale.ROOT);
                    String label =
                            effect.getAmplifier() > 0
                                    ? effectName + " " + (effect.getAmplifier() + 1)
                                    : effectName;
                    this.effectLabels.add(label);
                    this.effectColors.add(0xFF000000 | effect.getEffectType().value().getColor());
                    this.effectsPanelWidth =
                            Math.max(
                                    this.effectsPanelWidth,
                                    this.scaledTextWidth(
                                                    effectName + " " + formatEffectDuration(effect),
                                                    0.48F)
                                            + 25);
                }
            }

            this.updateEffectDurations();
        }

        long toggleCounter = ModuleNotifications.getToggleCounter();
        if (toggleCounter != this.lastToggleCounter || now >= this.nextModuleUpdate) {
            this.lastToggleCounter = toggleCounter;
            this.nextModuleUpdate = now + 1000000000L;
            int modulesHashValue = 1;

            for (Module module : ModuleManager.get().getAll()) {
                int partialHash = 31 * (31 * modulesHashValue + System.identityHashCode(module));
                int activeFlag = module.isActive() ? 1 : 0;
                modulesHashValue =
                        31 * (31 * (partialHash + activeFlag) + module.keybind.getValue())
                                + getDisplayName(module).hashCode();
            }

            if (modulesHashValue != this.modulesHash) {
                this.rebuildModuleEntries(modulesHashValue);
            }
        }

        boolean animationsEnabled = ThemeModule.areAnimationsEnabled();
        float deltaSeconds =
                Math.min(0.05F, Math.max(0.0F, (float) (now - this.lastFrameNanos) / 1.0E9F));
        this.lastFrameNanos = now;
        if (!animationsEnabled) {
            this.moduleAnimations.replaceAll((key, value) -> 1.0F);
            this.animating = false;
        } else if (this.animating) {
            float step = deltaSeconds * 5.0F;
            boolean settled = true;

            for (Entry<Module, Float> animation : this.moduleAnimations.entrySet()) {
                float current = animation.getValue();
                if (current < 1.0F) {
                    float next = Math.min(1.0F, current + step);
                    animation.setValue(next);
                    if (next < 1.0F) {
                        settled = false;
                    }
                }
            }

            this.animating = !settled;
        }
    }

    private void rebuildModuleEntries(int hash) {
        this.modulesHash = hash;
        this.moduleEntries.clear();
        this.keybindEntries.clear();
        this.keybindsPanelWidth = 78;

        for (Module module : ModuleManager.get().getAll()) {
            if (module != this && !"theme".equalsIgnoreCase(module.name)) {
                String displayName = getDisplayName(module).toUpperCase(Locale.ROOT);
                if (module.isActive()) {
                    this.moduleEntries.add(
                            new HudModule.ModuleListEntry(
                                    module,
                                    displayName,
                                    this.scaledTextWidth(displayName, 0.59F),
                                    this.scaledTextWidth(displayName, 0.61F)));
                    if (!this.moduleAnimations.containsKey(module)) {
                        float initialProgress =
                                ThemeModule.areAnimationsEnabled() && this.entriesBuilt
                                        ? 0.0F
                                        : 1.0F;
                        this.moduleAnimations.put(module, initialProgress);
                        if (initialProgress < 1.0F) {
                            this.animating = true;
                        }
                    }
                }

                if (module.keybind.getValue() >= 0) {
                    String keyName = this.getKeyName(module.keybind.getValue());
                    int keyWidth = this.scaledTextWidth(keyName, 0.45F);
                    this.keybindEntries.add(
                            new HudModule.KeybindEntry(module, displayName, keyName, keyWidth));
                    this.keybindsPanelWidth =
                            Math.max(
                                    this.keybindsPanelWidth,
                                    this.scaledTextWidth(displayName, 0.46F) + keyWidth + 18);
                }
            }
        }

        this.moduleEntries.sort(
                Comparator.comparingInt(HudModule.ModuleListEntry::sortWidth)
                        .reversed()
                        .thenComparing(HudModule.ModuleListEntry::title));
        this.keybindEntries.sort(Comparator.comparing(HudModule.KeybindEntry::title));
        this.keybindCountWidth =
                this.scaledTextWidth(Integer.toString(this.keybindEntries.size()), 0.48F);
        this.moduleAnimations
                .keySet()
                .removeIf(m -> this.moduleEntries.stream().noneMatch(entry -> entry.module() == m));
        this.entriesBuilt = true;
    }

    public void renderRadarBackground(DrawContext context) {
        if (this.shouldRender() && this.radar.getValue()) {
            float scale = this.hudScale.getValue().floatValue();
            int radarY = this.getRadarY();
            int radius = this.radarDiameter.getValue() / 2;
            int centerX = 8 + radius;
            int centerY = radarY + radius;
            int outerColor = getThemeColor(0.05F);
            int innerColor =
                    ThemeModule.isLegacyRenderingEnabled()
                            ? getThemeColor(0.5F)
                            : blendColors(outerColor, 790552, 0.62F);
            int accentColor = getThemeColor(0.95F);
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);

            try {
                CircleRenderer.drawCircle(
                        context, centerX, centerY, radius, withAlpha(outerColor, 110));
                CircleRenderer.drawCircle(
                        context, centerX, centerY, radius - 2, withAlpha(innerColor, 112));
                CircleRenderer.drawCircle(
                        context,
                        centerX,
                        centerY,
                        Math.max(4, radius / 2),
                        withAlpha(accentColor, 24));
            } catch (Throwable e) {
                context.getMatrices().popMatrix();
                throw e;
            }

            context.getMatrices().popMatrix();
        }
    }

    private int getRadarY() {
        int panelHeight = this.getInfoPanelHeight();
        return panelHeight == 0 ? 8 : 16 + panelHeight;
    }

    private int getInfoPanelHeight() {
        return HudLayout.computeHeight(
                this.watermark.getValue(), this.info.getValue(), this.coords.getValue());
    }

    private int drawRadar(DrawContext context, int x, int y) {
        int diameter = this.radarDiameter.getValue();
        int radius = diameter / 2;
        int centerX = x + radius;
        int centerY = y + radius;
        int centerColor = getThemeColor(0.5F);
        int crossRadius = radius - 5;
        int horizontalColor = withAlpha(getThemeColor(0.18F), 68);
        int verticalColor = withAlpha(getThemeColor(0.82F), 68);
        fillHalfScale(
                context,
                (centerX - crossRadius) * 2,
                centerY * 2,
                (crossRadius * 2 + 1) * 2,
                1,
                horizontalColor);
        fillHalfScale(
                context,
                centerX * 2,
                (centerY - crossRadius) * 2,
                1,
                (crossRadius * 2 + 1) * 2,
                verticalColor);
        double yawRadians = Math.toRadians(this.mc.player.getYaw());
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        this.drawCompassLabel(context, "N", 0.0, -1.0, centerX, centerY, radius, cos, sin);
        this.drawCompassLabel(context, "E", 1.0, 0.0, centerX, centerY, radius, cos, sin);
        this.drawCompassLabel(context, "S", 0.0, 1.0, centerX, centerY, radius, cos, sin);
        this.drawCompassLabel(context, "W", -1.0, 0.0, centerX, centerY, radius, cos, sin);
        int range = this.radarRange.getValue();
        double pixelsPerBlock = (radius - 9.0) / range;

        for (PlayerEntity other : this.mc.world.getPlayers()) {
            if (other != this.mc.player && other.isAlive()) {
                double dx = other.getX() - this.mc.player.getX();
                double dz = other.getZ() - this.mc.player.getZ();
                if (!(dx * dx + dz * dz > (double) range * range)) {
                    double rotatedY = -dx * sin + dz * cos;
                    int dotX = centerX + (int) Math.round((dx * cos + dz * sin) * pixelsPerBlock);
                    int dotY = centerY - (int) Math.round(rotatedY * pixelsPerBlock);
                    int offsetX = dotX - centerX;
                    int offsetY = dotY - centerY;
                    if (offsetX * offsetX + offsetY * offsetY <= (radius - 2) * (radius - 2)
                            && other instanceof AbstractClientPlayerEntity) {
                        PlayerSkinDrawer.draw(
                                context,
                                ((AbstractClientPlayerEntity) other).getSkin(),
                                dotX - 2,
                                dotY - 2,
                                4);
                    }
                }
            }
        }

        CircleRenderer.drawCircle(context, centerX, centerY, 1.0F, withAlpha(centerColor, 255));
        return y + diameter;
    }

    private void drawCompassLabel(
            DrawContext context,
            String label,
            double dirX,
            double dirZ,
            int centerX,
            int centerY,
            int radius,
            double cos,
            double sin) {
        double rotatedY = -dirX * sin + dirZ * cos;
        int textX =
                centerX
                        + (int) Math.round((dirX * cos + dirZ * sin) * (radius - 8))
                        - this.compassLetterWidth / 2;
        int textY = centerY - (int) Math.round(rotatedY * (radius - 8)) - 2;

        this.drawScaledText(
                context,
                label,
                textX,
                textY,
                0.55F,
                withAlpha(
                        getThemeColor(
                                switch (label) {
                                    case "N" -> 0.0F;
                                    case "E" -> 0.33F;
                                    case "S" -> 0.66F;
                                    default -> 1.0F;
                                }),
                        210));
    }

    private void drawPanelBackground(
            DrawContext context, int x, int y, int width, int height, int radius) {
        if (this.background.getValue() && width > 0 && height > 0) {
            int alpha =
                    Math.max(
                            0,
                            Math.min(
                                    255,
                                    (int) Math.round(this.backgroundOpacity.getValue() * 215.0)));
            SdfShapeRenderer.fill(context, x, y, width, height, 2.0F, withAlpha(394251, alpha));
            SdfShapeRenderer.drawOutline(
                    context,
                    x,
                    y,
                    width,
                    height,
                    2.0F,
                    0.6F,
                    withAlpha(1906217, Math.min(255, alpha + 30)));
        }
    }

    private void drawFadingBackground(
            DrawContext context, int x, int y, int width, int height, float fade) {
        if (this.background.getValue() && width > 0 && height > 0) {
            SdfShapeRenderer.fill(
                    context,
                    x,
                    y,
                    width,
                    height,
                    2.0F,
                    withAlpha(
                            394251,
                            Math.max(
                                    0,
                                    Math.min(
                                            255,
                                            (int)
                                                    Math.round(
                                                            this.backgroundOpacity.getValue()
                                                                    * 225.0
                                                                    * fade)))));
        }
    }

    private static void fillRoundedRectHalfScale(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);
            fillRoundedRect(context, x * 2, y * 2, width * 2, height * 2, radius * 2, color);
            context.getMatrices().popMatrix();
        }
    }

    private static void fillRoundedRect(
            DrawContext context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
        } else {
            context.fill(x, y + r, x + width, y + height - r, color);

            for (int i = 0; i < r; i++) {
                float dy = r - i - 0.5F;
                float inset = r - (float) Math.sqrt(r * r - dy * dy);
                fillRoundedRow(context, x, y + i, width, inset, color);
                fillRoundedRow(context, x, y + height - i - 1, width, inset, color);
            }
        }
    }

    private static void fillRoundedRow(
            DrawContext context, int x, int y, int width, float inset, int color) {
        float left = x + inset;
        float right = x + width - inset;
        int leftPixel = (int) Math.ceil(left);
        int rightPixel = (int) Math.floor(right);
        if (rightPixel > leftPixel) {
            context.fill(leftPixel, y, rightPixel, y + 1, color);
        }

        int alpha = color >>> 24;
        float leftCoverage = leftPixel - left;
        if (leftCoverage > 0.001F) {
            context.fill(
                    leftPixel - 1,
                    y,
                    leftPixel,
                    y + 1,
                    withAlpha(color, Math.max(1, Math.round(alpha * leftCoverage))));
        }

        float rightCoverage = right - rightPixel;
        if (rightCoverage > 0.001F) {
            context.fill(
                    rightPixel,
                    y,
                    rightPixel + 1,
                    y + 1,
                    withAlpha(color, Math.max(1, Math.round(alpha * rightCoverage))));
        }
    }

    private void drawGradientLine(DrawContext context, int x, int y, int length) {
        for (int i = 0; i < length; i++) {
            float t = length <= 1 ? 0.5F : (float) i / (length - 1);

            float fade = (float) Math.sin(3.141592653589793 * t);
            context.fill(
                    x + i,
                    y,
                    x + i + 1,
                    y + 1,
                    withAlpha(getThemeColor(t), Math.round(220.0F * fade)));
        }
    }

    private void drawScaledText(
            DrawContext context, String text, int x, int y, float scale, int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) x, (float) y);
        context.getMatrices().scale(scale, scale);
        BoldText.draw(context, this.mc.textRenderer, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    private void drawStretchedText(
            DrawContext context,
            String text,
            int textWidth,
            int x,
            int y,
            int boxWidth,
            int boxHeight,
            int targetWidth,
            int color) {
        float scaleX = Math.max(0.1F, (float) targetWidth / textWidth);
        int drawX = x + (boxWidth - targetWidth) / 2;
        int drawY = y + Math.round((boxHeight - 9.0F) / 2.0F);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) drawX, (float) drawY);
        context.getMatrices().scale(scaleX, 1.0F);
        BoldText.draw(context, this.mc.textRenderer, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    private void drawRightAlignedText(
            DrawContext context, String text, int right, int y, float scale, int color) {
        this.drawScaledText(
                context, text, right - this.scaledTextWidth(text, scale), y, scale, color);
    }

    private void drawTextEndingAt(
            DrawContext context,
            String text,
            int textWidth,
            int right,
            int y,
            float scale,
            int color) {
        this.drawScaledText(context, text, right - textWidth, y, scale, color);
    }

    private void updateEffectDurations() {
        this.effectDurationTexts.clear();
        this.effectDurationWidths.clear();

        for (StatusEffectInstance effect : this.activeEffects) {
            String duration = formatEffectDuration(effect);
            this.effectDurationTexts.add(duration);
            this.effectDurationWidths.add(this.scaledTextWidth(duration, 0.48F));
        }

        this.effectCountWidth =
                this.scaledTextWidth(Integer.toString(this.activeEffects.size()), 0.48F);
    }

    private int scaledTextWidth(String text, float scale) {
        return Math.max(1, Math.round(BoldText.width(this.mc.textRenderer, text) * scale));
    }

    private void drawScaledItem(DrawContext context, ItemStack stack, int x, int y, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) x, (float) y);
        context.getMatrices().scale(scale, scale);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    private int ping() {
        if (this.mc.getNetworkHandler() == null) {
            return 0;
        } else {
            PlayerListEntry entry =
                    this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());
            return entry == null ? 0 : entry.getLatency();
        }
    }

    private double speed() {
        return Math.hypot(this.mc.player.getVelocity().x, this.mc.player.getVelocity().z) * 20.0;
    }

    private static String getDisplayName(Module module) {
        return module.title != null && !module.title.isBlank() ? module.title : module.name;
    }

    private String getKeyName(int key) {
        return KeyNames.getKeyName(key);
    }

    private static String getEffectName(StatusEffectInstance effect) {
        return effect.getEffectType().value().getName().getString();
    }

    private static String formatEffectDuration(StatusEffectInstance effect) {
        if (effect.getDuration() >= 32767) {
            return "\u221e";
        } else {
            int totalSeconds = Math.max(0, effect.getDuration() / 20);
            int seconds = totalSeconds % 60;
            int minutes = totalSeconds / 60;
            return minutes + (seconds < 10 ? ":0" : ":") + seconds;
        }
    }

    private static int getEffectDurationDigits(StatusEffectInstance effect) {
        if (effect.getDuration() >= 32767) {
            return -1;
        } else {
            int minutes = Math.max(0, effect.getDuration() / 1200);

            int digits;
            for (digits = 1; minutes >= 10; digits++) {
                minutes /= 10;
            }

            return digits;
        }
    }

    private static String formatSpeed(double speed) {
        long tenths = Math.round(speed * 10.0);
        return tenths / 10L + "." + Math.abs(tenths % 10L);
    }

    private static int getDurabilityPercent(ItemStack stack) {
        return stack.isDamageable() && stack.getMaxDamage() > 0
                ? Math.max(
                        0,
                        Math.min(
                                100,
                                (int)
                                        Math.round(
                                                (1.0
                                                                - (double) stack.getDamage()
                                                                        / stack.getMaxDamage())
                                                        * 100.0)))
                : 100;
    }

    private static int getThemeColor(float position) {
        return ThemeModule.getAccentColorAt(Math.max(0.0F, Math.min(1.0F, position)));
    }

    private static int withAlpha(int color, int alpha) {
        int rgb = color & 16777215;
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    private static int blendColors(int from, int to, float delta) {
        float t = Math.max(0.0F, Math.min(1.0F, delta));
        return Math.round((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t) << 16
                | Math.round((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t) << 8
                | Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
    }

    private static void fillHalfScale(
            DrawContext context, int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(0.5F, 0.5F);
            context.fill(x, y, x + width, y + height, color);
            context.getMatrices().popMatrix();
        }
    }

    static {
        ARMOR_SLOTS =
                new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                };
    }

    private record KeybindEntry(Module module, String title, String key, int keyWidth) {}

    private record ModuleListEntry(Module module, String title, int sortWidth, int drawWidth) {}
}
