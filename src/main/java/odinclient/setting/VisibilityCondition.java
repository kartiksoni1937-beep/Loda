package odinclient.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface VisibilityCondition {
    boolean isVisible();
}
