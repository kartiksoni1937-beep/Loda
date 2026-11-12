package odinclient.setting.type;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;

import odinclient.setting.Setting;
import odinclient.setting.SettingBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class BlockListSetting extends Setting<List<Block>> {
    private final Predicate<Block> filter;

    private BlockListSetting(BlockListSetting.Builder builder) {
        super(
                builder.name,
                builder.description,
                builder.defaultValue,
                builder.visibility,
                builder.changeListener);
        this.filter = builder.filter;
    }

    protected List<Block> parseValue(String text) {
        return new ArrayList<>(this.getValue());
    }

    public boolean isAllowed(Block block) {
        return this.filter == null || this.filter.test(block);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder
            extends SettingBuilder<BlockListSetting.Builder, BlockListSetting, List<Block>> {
        private Predicate<Block> filter;

        public Builder() {
            this.defaultValue = new ArrayList<>();
        }

        public BlockListSetting.Builder filter(Predicate<Block> predicate) {
            this.filter = predicate;
            return this;
        }

        public BlockListSetting build() {
            return new BlockListSetting(this);
        }
    }
}
