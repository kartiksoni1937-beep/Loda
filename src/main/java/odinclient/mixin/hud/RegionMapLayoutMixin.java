package odinclient.mixin.hud;

import net.minecraft.client.gui.DrawContext;

import odinclient.mixin.accessor.SettingGroupAccessor;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.module.hud.regionmap.RegionMap;
import odinclient.module.hud.regionmap.RegionMapHud;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RegionMap.class)
public abstract class RegionMapLayoutMixin extends Module {
    @Shadow(remap = false) @Final private Setting<Integer> xPosition;
    @Shadow(remap = false) @Final private Setting<Integer> yPosition;
    @Shadow(remap = false) @Final private Setting<Double> scale;
    @Shadow(remap = false) @Final private Setting<Boolean> showCoordinates;
    @Shadow(remap = false) @Final private Setting<Boolean> showRegionNumbers;
    @Shadow(remap = false) @Final private Setting<Double> backgroundOpacity;
    @Unique private Setting<Boolean> odinclient$newRegionMap;

    protected RegionMapLayoutMixin(Category category, String title, String description) {
        super(category, title, description);
    }

    @Shadow(remap = false)
    private int getRegionColor(int region) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void odinclient$installNewMap(Category category, CallbackInfo ci) {
        SettingGroup general = this.settings.getGeneral();
        this.odinclient$newRegionMap =
                general.add(
                        new BooleanSetting.Builder()
                                .name("new-region-map")
                                .description(
                                        "Use the updated region boundaries and shard positions.")
                                .defaultValue(false)
                                .build());
        List<Setting<?>> orderedSettings =
                ((SettingGroupAccessor) (Object) general).odinclient$orderedSettings();
        orderedSettings.remove(this.odinclient$newRegionMap);
        orderedSettings.add(0, this.odinclient$newRegionMap);
    }

    @Inject(method = "drawPanel", at = @At("HEAD"), cancellable = true, remap = false)
    private void odinclient$renderNewMap(DrawContext context, int x, int y, CallbackInfo ci) {
        if (this.odinclient$newRegionMap != null
                && Boolean.TRUE.equals(this.odinclient$newRegionMap.getValue())) {
            float roundedScale = Math.round(this.scale.getValue().floatValue() * 20.0F) / 20.0F;
            RegionMapHud.render(
                    context,
                    this.mc,
                    x,
                    y,
                    this.xPosition.getValue(),
                    this.yPosition.getValue(),
                    roundedScale,
                    Boolean.TRUE.equals(this.showCoordinates.getValue()),
                    Boolean.TRUE.equals(this.showRegionNumbers.getValue()),
                    this.backgroundOpacity.getValue(),
                    this::getRegionColor);
            ci.cancel();
        }
    }
}
