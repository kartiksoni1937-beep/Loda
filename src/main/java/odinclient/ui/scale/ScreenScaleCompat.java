package odinclient.ui.scale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;

public final class ScreenScaleCompat {
    private static final ThreadLocal<ScreenScaleCompat.ScaleState> currentState =
            new ThreadLocal<>();

    private ScreenScaleCompat() {}

    public static float getScale(int scaleFactor) {
        Window window = getWindow();
        return window == null ? 1.0F : GuiScale.getScale(window.getScaleFactor());
    }

    public static int getScaledWidth() {
        ScreenScaleCompat.ScaleState state = currentState.get();
        if (state != null) {
            return state.width;
        } else {
            Window window = getWindow();
            return window == null ? 1 : GuiScale.getScaledSize(window.getFramebufferWidth());
        }
    }

    public static int getScaledHeight() {
        ScreenScaleCompat.ScaleState state = currentState.get();
        if (state != null) {
            return state.height;
        } else {
            Window window = getWindow();
            return window == null ? 1 : GuiScale.getScaledSize(window.getFramebufferHeight());
        }
    }

    public static boolean isScopeActive() {
        return currentState.get() != null;
    }

    public static float getCompensation() {
        ScreenScaleCompat.ScaleState state = currentState.get();
        return state == null ? 1.0F : state.compensation;
    }

    public static Click unscaleClick(Click click) {
        float scale = getScale(0);
        return new Click(click.x() / scale, click.y() / scale, click.buttonInfo());
    }

    public static boolean isScaledScreen(Object screen) {
        return screen != null && GuiScale.isConfigScreen(screen.getClass().getName());
    }

    public static ScreenScaleCompat.ScaleScope enter(DrawContext context) {
        return new ScreenScaleCompat.ScaleScope(context);
    }

    private static Window getWindow() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.getWindow();
    }

    public static final class ScaleScope implements AutoCloseable {
        private final DrawContext context;
        private final ScreenScaleCompat.ScaleState previousState;
        private boolean closed;

        private ScaleScope(DrawContext context) {
            this.context = context;
            this.previousState = ScreenScaleCompat.currentState.get();
            context.getMatrices().pushMatrix();
            if (this.previousState == null) {
                float scale = ScreenScaleCompat.getScale(0);
                ScreenScaleCompat.currentState.set(
                        new ScreenScaleCompat.ScaleState(
                                scale,
                                ScreenScaleCompat.getScaledWidth(),
                                ScreenScaleCompat.getScaledHeight()));
                context.getMatrices().scale(scale, scale);
            }
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;

                try {
                    this.context.getMatrices().popMatrix();
                } catch (Throwable t) {
                    if (this.previousState == null) {
                        ScreenScaleCompat.currentState.remove();
                    } else {
                        ScreenScaleCompat.currentState.set(this.previousState);
                    }

                    throw t;
                }

                if (this.previousState == null) {
                    ScreenScaleCompat.currentState.remove();
                } else {
                    ScreenScaleCompat.currentState.set(this.previousState);
                }
            }
        }
    }

    private record ScaleState(float compensation, int width, int height) {}
}
