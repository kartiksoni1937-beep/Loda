package odinclient.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class Color {
    public static final Color CLEAR = new Color(0, 0, 0, 0);
    public int r;
    public int g;
    public int b;
    public int a;

    public Color(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public Color(int r, int g, int b, int a) {
        this.r = clampChannel(r);
        this.g = clampChannel(g);
        this.b = clampChannel(b);
        this.a = clampChannel(a);
    }

    public int toArgb() {
        return this.a << 24 | this.r << 16 | this.g << 8 | this.b;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
