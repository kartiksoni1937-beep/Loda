package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;

@Environment(EnvType.CLIENT)
public final class AutoMine extends Module {
    public AutoMine(Category category) {
        super(
                category,
                "auto-mine",
                "Toggles vanilla Attack/Destroy on to mine continuously without click spam.");
        this.title = "Auto Mine";
    }

    @Override
    public void onActivate() {
        this.setAttackPressed(true);
    }

    @Override
    public void onDeactivate() {
        this.setAttackPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        this.setAttackPressed(true);
    }

    private void setAttackPressed(boolean pressed) {
        this.mc.options.attackKey.setPressed(pressed);
    }
}
