package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.util.InputSimulator;

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
        this.holdAttack();
    }

    @Override
    public void onDeactivate() {
        if (this.mc != null && this.mc.options != null) {
            InputSimulator.release(this.mc.options.attackKey);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        this.holdAttack();
    }

    private void holdAttack() {
        if (this.mc != null && this.mc.options != null) {
            InputSimulator.hold(this.mc.options.attackKey, 2);
        }
    }
}
