package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

@Environment(EnvType.CLIENT)
public final class NumberSetting extends Setting<Double> {
    public final double min;
    public final double max;
    public final double sliderMin;
    public final double sliderMax;
    public final int decimals;

    private NumberSetting(NumberSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
        this.min = builder.min;
        this.max = builder.max;
        this.sliderMin = builder.sliderMin;
        this.sliderMax = builder.sliderMax;
        this.decimals = 2;
    }

    protected Double parseValue(String text) {
        return Math.max(this.min, Math.min(this.max, Double.parseDouble(text.trim())));
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<NumberSetting.Builder, NumberSetting, Double> {
        private double min = -1.7976931348623157E308;
        private double max = 1.7976931348623157E308;
        private double sliderMin = 0.0;
        private double sliderMax = 10.0;

        public Builder() {
            this.defaultValue = 0.0;
        }

        public NumberSetting.Builder defaultValue(double value) {
            this.defaultValue = value;
            return this;
        }

        public NumberSetting.Builder min(double minValue) {
            this.min = minValue;
            return this;
        }

        public NumberSetting.Builder max(double maxValue) {
            this.max = maxValue;
            return this;
        }

        public NumberSetting.Builder range(double minValue, double maxValue) {
            this.min = minValue;
            this.max = maxValue;
            return this;
        }

        public NumberSetting.Builder sliderMax(double maxValue) {
            this.sliderMax = maxValue;
            return this;
        }

        public NumberSetting.Builder sliderRange(double minValue, double maxValue) {
            this.sliderMin = minValue;
            this.sliderMax = maxValue;
            return this;
        }

        public NumberSetting build() {
            return new NumberSetting(this);
        }
    }
}
