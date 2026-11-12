package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.IntSetting;
import odinclient.setting.type.NumberSetting;

@Environment(EnvType.CLIENT)
public class RenderCycle extends Module {
    private final SettingGroup generalSettings = this.settings.getGeneral();
    private final Setting<Double> triggerY =
            this.generalSettings.add(
                    new NumberSetting.Builder()
                            .name("trigger-y")
                            .description(
                                    "Below this Y level the render-distance reload fires once."
                                        + " Above it, it resets.")
                            .defaultValue(-2.0)
                            .min(-64.0)
                            .sliderRange(-64.0, 320.0)
                            .build());
    private final Setting<Integer> lowDistance =
            this.generalSettings.add(
                    new IntSetting.Builder()
                            .name("low-distance")
                            .description("Render distance to drop down to first.")
                            .defaultValue(2)
                            .min(2)
                            .sliderRange(2, 32)
                            .build());
    private final Setting<Integer> highDistance =
            this.generalSettings.add(
                    new IntSetting.Builder()
                            .name("high-distance")
                            .description("Render distance to go back up to.")
                            .defaultValue(32)
                            .min(2)
                            .sliderRange(2, 64)
                            .build());
    private final Setting<Integer> raiseDelay =
            this.generalSettings.add(
                    new IntSetting.Builder()
                            .name("raise-delay")
                            .description(
                                    "Ticks to stay at low distance before going back up (20 = 1"
                                        + " second).")
                            .defaultValue(40)
                            .min(1)
                            .sliderRange(1, 200)
                            .build());
    private final Setting<Boolean> restoreAbove =
            this.generalSettings.add(
                    new BooleanSetting.Builder()
                            .name("restore-above")
                            .description(
                                    "Put your original render distance back when you go above the"
                                        + " trigger Y.")
                            .defaultValue(true)
                            .build());
    private boolean active = false;
    private boolean raised = false;
    private int ticksAtLow = 0;
    private int originalDistance = -1;

    public RenderCycle() {
        super(
                Categories.MAIN,
                "render-cycle",
                "Drops render distance to low then back to high once, when you go below a Y level."
                    + " Forces a chunk reload.");
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        if (this.active && this.restoreAbove.getValue() && this.originalDistance > 0) {
            this.setRenderDistance(this.originalDistance);
        }

        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player != null && this.mc.options != null) {
            boolean belowTrigger = this.mc.player.getY() < this.triggerY.getValue();
            if (belowTrigger) {
                if (!this.active) {
                    this.originalDistance = this.mc.options.getViewDistance().getValue();
                    this.active = true;
                    this.raised = false;
                    this.ticksAtLow = 0;
                    this.setRenderDistance(this.lowDistance.getValue());
                } else if (!this.raised) {
                    if (this.ticksAtLow++ >= this.raiseDelay.getValue()) {
                        this.setRenderDistance(this.highDistance.getValue());
                        this.raised = true;
                    }
                }
            } else if (this.active) {
                this.active = false;
                this.raised = false;
                this.ticksAtLow = 0;
                if (this.restoreAbove.getValue() && this.originalDistance > 0) {
                    this.setRenderDistance(this.originalDistance);
                }
            }
        }
    }

    private void setRenderDistance(int distance) {
        this.mc.options.getViewDistance().setValue(distance);
        this.mc.options.sendClientSettings();
    }

    private void reset() {
        this.active = false;
        this.raised = false;
        this.ticksAtLow = 0;
        this.originalDistance = -1;
    }
}
