package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

@Environment(EnvType.CLIENT)
public final class IntSetting extends Setting<Integer> {
    public final int min;
    public final int max;
    public final int sliderMin;
    public final int sliderMax;

    private IntSetting(IntSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
        this.min = builder.minValue;
        this.max = builder.maxValue;
        this.sliderMin = builder.sliderMinValue;
        this.sliderMax = builder.sliderMaxValue;
    }

    protected Integer parseValue(String text) {
        return Math.max(this.min, Math.min(this.max, Integer.parseInt(text.trim())));
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<IntSetting.Builder, IntSetting, Integer> {
        private int minValue = -2147483648;
        private int maxValue = 2147483647;
        private int sliderMinValue = 0;
        private int sliderMaxValue = 10;

        public Builder() {
            this.defaultValue = 0;
        }

        public IntSetting.Builder min(int value) {
            this.minValue = value;
            return this;
        }

        public IntSetting.Builder max(int value) {
            this.maxValue = value;
            return this;
        }

        public IntSetting.Builder range(int minValue, int maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        public IntSetting.Builder sliderMax(int value) {
            this.sliderMaxValue = value;
            return this;
        }

        public IntSetting.Builder sliderRange(int minValue, int maxValue) {
            this.sliderMinValue = minValue;
            this.sliderMaxValue = maxValue;
            return this;
        }

        public IntSetting build() {
            return new IntSetting(this);
        }
    }
}
