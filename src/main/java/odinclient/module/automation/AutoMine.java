package odinclient.module.automation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.hit.BlockHitResult;

import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.util.WorldUtils;

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
        WorldUtils.cancelBreaking();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        this.holdAttack();
    }

    /** Queues one breaking step per tick, so mining advances without extra packets. */
    private void holdAttack() {
        if (this.mc.player == null || !(this.mc.crosshairTarget instanceof BlockHitResult hit)) {
            return;
        }

        WorldUtils.startBreaking(hit.getBlockPos(), hit.getSide());
        this.mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }
}
