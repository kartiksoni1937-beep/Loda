package odinclient.ui.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.Toast.Visibility;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import odinclient.ui.draw.BoldText;

@Environment(EnvType.CLIENT)
public final class NotificationToast implements Toast {
    private final String title;
    private final String description;
    private final Item icon;
    private final long durationMs;
    private final boolean compact;
    private long firstUpdateTime = -1L;
    private Visibility visibility = Visibility.SHOW;

    private NotificationToast(NotificationToast.Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.icon = builder.icon;
        this.durationMs = builder.durationMs;
        this.compact = builder.compact;
    }

    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (this.firstUpdateTime < 0L) {
            this.firstUpdateTime = time;
        }

        if (time - this.firstUpdateTime >= this.durationMs) {
            this.visibility = Visibility.HIDE;
        }
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        if (this.compact) {
            this.drawCompact(context, textRenderer);
        } else {
            context.fill(0, 0, this.getWidth(), this.getHeight(), -434431197);
            context.fill(0, 0, 3, this.getHeight(), -14455809);
            int textX = this.icon == null ? 9 : 30;

            if (this.icon != null) {
                context.drawItem(new ItemStack(this.icon), 8, 8);
            }

            context.drawTextWithShadow(textRenderer, this.title, textX, 8, -1);
            context.drawTextWithShadow(textRenderer, this.description, textX, 20, -4603697);
        }
    }

    private void drawCompact(DrawContext context, TextRenderer textRenderer) {
        int width = this.getWidth();
        int height = this.getHeight();
        context.fill(0, 0, width, height, -10329502);
        context.fill(3, 3, width - 3, height - 3, -14803426);
        context.fill(4, 4, width - 4, 5, -13882324);
        context.fill(4, height - 5, width - 4, height - 4, -15658735);
        context.fill(6, 7, 27, 28, -15658735);
        if (this.icon != null) {
            context.drawItem(new ItemStack(this.icon), 8, 9);
        }

        this.drawScaledText(context, textRenderer, this.title, 31, 7, 0.62F, -203);
        this.drawScaledText(context, textRenderer, this.description, 31, 18, 0.56F, -855310);
    }

    private void drawScaledText(
            DrawContext context,
            TextRenderer textRenderer,
            String text,
            int x,
            int y,
            float scale,
            int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) x, (float) y);
        context.getMatrices().scale(scale, scale);
        BoldText.draw(context, textRenderer, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder {
        private final String title;
        private String description = "";
        private Item icon;
        private long durationMs = 5000L;
        private boolean compact;

        public Builder(String title) {
            this.title = title;
        }

        public NotificationToast.Builder description(String text) {
            this.description = text;
            return this;
        }

        public NotificationToast.Builder icon(Item item) {
            this.icon = item;
            return this;
        }

        public NotificationToast.Builder durationMs(long millis) {
            this.durationMs = millis;
            return this;
        }

        public NotificationToast.Builder compactStyle() {
            this.compact = true;
            return this;
        }

        public NotificationToast build() {
            return new NotificationToast(this);
        }
    }
}
