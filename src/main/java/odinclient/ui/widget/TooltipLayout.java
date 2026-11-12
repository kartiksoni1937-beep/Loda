package odinclient.ui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class TooltipLayout {
    private TooltipLayout() {}

    public static TooltipLayout.TooltipBounds computeBounds(
            float mouseX,
            float mouseY,
            float width,
            float height,
            float screenWidth,
            float screenHeight) {
        float margin = Math.min(4.0F, Math.min(screenWidth, screenHeight) * 0.1F);
        float boundedWidth = Math.min(width, Math.max(0.0F, screenWidth - margin * 2.0F));
        float boundedHeight = Math.min(height, Math.max(0.0F, screenHeight - margin * 2.0F));
        float x = mouseX + 12.0F;
        float y = mouseY + 12.0F;
        if (x + boundedWidth > screenWidth - margin) {
            x = mouseX - boundedWidth - 12.0F;
        }

        if (y + boundedHeight > screenHeight - margin) {
            y = mouseY - boundedHeight - 12.0F;
        }

        float clampedX = Math.max(margin, Math.min(x, screenWidth - margin - boundedWidth));
        float clampedY = Math.max(margin, Math.min(y, screenHeight - margin - boundedHeight));
        return new TooltipLayout.TooltipBounds(clampedX, clampedY, boundedWidth, boundedHeight);
    }

    public static List<String> wrapText(
            String text, float maxWidth, ToDoubleFunction<String> measure) {
        if (text != null && !text.isBlank()) {
            if (maxWidth > 0.0F && Float.isFinite(maxWidth)) {
                List<String> lines = new ArrayList<>();
                String[] paragraphs = text.replace("\r", "").split("\n", -1);
                int count = paragraphs.length;

                for (int i = 0; i < count; i++) {
                    String line = "";

                    for (String word : paragraphs[i].strip().split("\\s+")) {
                        if (!word.isEmpty()) {
                            String candidate = line.isEmpty() ? word : line + " " + word;

                            if (measure.applyAsDouble(candidate) <= maxWidth) {
                                line = candidate;
                            } else {
                                if (!line.isEmpty()) {
                                    lines.add(line);
                                    line = "";
                                }

                                int start = 0;

                                while (start < word.length()) {
                                    int end = start + Character.charCount(word.codePointAt(start));
                                    String character = word.substring(start, end);
                                    if (!line.isEmpty()) {
                                        if (measure.applyAsDouble(line + character) > maxWidth) {
                                            lines.add(line);
                                            line = "";
                                        }
                                    }

                                    line = line + character;
                                    start = end;
                                }
                            }
                        }
                    }

                    lines.add(line);
                }

                return List.copyOf(lines);
            } else {
                throw new IllegalArgumentException("Invalid tooltip width");
            }
        } else {
            return List.of();
        }
    }

    public record TooltipBounds(float x, float y, float width, float height) {}

    public static final class TooltipFadeTimer {
        private Object target;
        private long startNanos;

        public float getFadeProgress(Object target, long nowNanos) {
            if (target != this.target || nowNanos < this.startNanos) {
                this.target = target;
                this.startNanos = nowNanos;
            }

            return this.target == null
                    ? 0.0F
                    : Math.max(
                            0.0F,
                            Math.min(
                                    1.0F,
                                    (float) (nowNanos - this.startNanos - 180000000L) / 1.2E8F));
        }
    }
}
