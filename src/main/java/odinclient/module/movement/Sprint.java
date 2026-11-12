package odinclient.module.movement;

import net.minecraft.util.math.Vec2f;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Module;

public final class Sprint extends Module {
    private static final int STOP_DELAY_TICKS = 6;
    private boolean sprintStarted;
    private int ticksSinceMoving = 6;

    public Sprint() {
        super(Categories.MAIN, "sprint", "Automatically sprints while you are moving.");
        this.title = "Sprint";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player != null && this.mc.player.input != null) {
            Vec2f input = this.mc.player.input.getMovementInput();
            boolean moving =
                    input != null && (Math.abs(input.x) > 0.001F || Math.abs(input.y) > 0.001F);
            if (moving) {
                this.ticksSinceMoving = 0;
                this.sprintStarted = true;
                this.mc.options.sprintKey.setPressed(true);
            } else {
                this.ticksSinceMoving++;
                if (this.ticksSinceMoving >= STOP_DELAY_TICKS) {
                    this.ticksSinceMoving = STOP_DELAY_TICKS;
                    this.sprintStarted = false;
                    this.mc.options.sprintKey.setPressed(false);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.sprintStarted = false;
        this.ticksSinceMoving = 6;
        if (this.mc != null && this.mc.options != null) {
            this.mc.options.sprintKey.setPressed(false);
        }
    }
}
