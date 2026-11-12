package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.event.WorldRenderEvent;
import odinclient.module.Module;
import odinclient.module.hud.ThemeModule;
import odinclient.render.Color;
import odinclient.render.world.StableLineRenderer;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.NumberSetting;

import java.util.ArrayDeque;
import java.util.Deque;

@Environment(EnvType.CLIENT)
public final class JumpCircles extends Module {
    private static final long LIFETIME_NANOS = 1050000000L;
    private static final int RING_SEGMENTS = 72;
    private static final int MAX_CIRCLES = 8;
    private static final double MAX_RENDER_DISTANCE_SQ = 16384.0;
    private static final int LOGO_SLOTS = 22;
    private static final String LOGO_TEXT = "ODINCLIENT";
    private static final float[][][] GLYPH_STROKES;
    private final Setting<Double> size;
    private final Setting<Boolean> onlyWhileJumping;
    private final Deque<JumpCircles.JumpCircle> circles = new ArrayDeque<>();
    private Object trackedWorld;
    private boolean initialized;
    private boolean wasOnGround;
    private double lastGroundX;
    private double lastGroundY;
    private double lastGroundZ;

    public JumpCircles() {
        super(
                Categories.MAIN,
                "jump-circles",
                "Creates a rotating OdinClient circle whenever you jump or land.");
        this.title = "Jump Circles";
        SettingGroup general = this.settings.getGeneral();
        this.size =
                general.add(
                        new NumberSetting.Builder()
                                .name("size")
                                .description("How large the jump and landing circles should be.")
                                .defaultValue(2.25)
                                .range(0.5, 6.0)
                                .sliderRange(0.5, 6.0)
                                .build());
        this.onlyWhileJumping =
                general.add(
                        new BooleanSetting.Builder()
                                .name("only-while-jumping")
                                .description(
                                        "Only creates a circle when jumping, not when landing.")
                                .defaultValue(false)
                                .build());
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.circles.clear();
        this.resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!ThemeModule.areAnimationsEnabled()) {
            this.circles.clear();
            this.resetState();
        } else if (this.mc.world != null && this.mc.player != null) {
            if (this.trackedWorld != this.mc.world) {
                this.circles.clear();
                this.resetState();
                this.trackedWorld = this.mc.world;
            }

            boolean onGround = this.mc.player.isOnGround();
            double x = this.mc.player.getX();
            double y = this.mc.player.getY() + 0.025;
            double z = this.mc.player.getZ();
            if (!this.initialized) {
                this.initialized = true;
                this.wasOnGround = onGround;
                if (onGround) {
                    this.rememberGroundPosition(x, y, z);
                }
            } else {
                if (this.wasOnGround && !onGround) {
                    if (this.mc.player.getVelocity().y > 0.04) {
                        this.addCircle(this.lastGroundX, this.lastGroundY, this.lastGroundZ, false);
                    }
                } else if (!this.wasOnGround && onGround && !this.onlyWhileJumping.getValue()) {
                    this.addCircle(x, y, z, true);
                }

                if (onGround) {
                    this.rememberGroundPosition(x, y, z);
                }

                this.wasOnGround = onGround;
                this.removeExpired(System.nanoTime());
            }
        } else {
            this.circles.clear();
            this.resetState();
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (!ThemeModule.areAnimationsEnabled()) {
            this.circles.clear();
        } else if (this.mc.world != null && this.mc.player != null && !this.circles.isEmpty()) {
            long now = System.nanoTime();
            this.removeExpired(now);
            double size = Math.max(0.5, Math.min(6.0, this.size.getValue()));
            double playerX = this.mc.player.getX();
            double playerY = this.mc.player.getY();
            double playerZ = this.mc.player.getZ();

            for (JumpCircles.JumpCircle circle : this.circles) {
                double dx = circle.x - playerX;
                double dy = circle.y - playerY;
                double dz = circle.z - playerZ;
                if (dx * dx + dy * dy + dz * dz <= 16384.0) {
                    double progress = clamp01((now - circle.createdAt) / 1.05E9);
                    double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
                    double scale = circle.landing ? 0.58 + 0.42 * eased : 0.12 + 0.88 * eased;
                    double radius = size * scale;
                    double direction = circle.landing ? -1.0 : 1.0;
                    double rotation =
                            circle.rotationSeed + direction * progress * 3.141592653589793 * 1.45;
                    int alpha = computeAlpha(progress);
                    if (alpha > 0) {
                        this.drawRings(event, circle, radius, rotation, alpha);
                        this.drawLogoText(event, circle, radius, rotation, alpha);
                    }
                }
            }
        }
    }

    private void drawRings(
            WorldRenderEvent event,
            JumpCircles.JumpCircle circle,
            double radius,
            double rotation,
            int alpha) {
        Color innerColor = new Color(166, 67, 255, Math.max(26, alpha / 3));
        Color outerColor = new Color(229, 151, 255, Math.min(255, alpha + 18));
        double spokeOuterRadius = radius * 0.975;
        double glyphRadius = radius * 0.83;
        double innerRadius = radius * 0.68;
        double spokeInnerRadius = radius * 0.705;

        for (int i = 0; i < 72; i++) {
            double angle = rotation + 6.283185307179586 * i / 72.0;
            double nextAngle = rotation + 6.283185307179586 * (i + 1) / 72.0;
            drawLine(
                    event,
                    ringPoint(circle, radius, angle),
                    ringPoint(circle, radius, nextAngle),
                    outerColor,
                    2.8F);
            drawLine(
                    event,
                    ringPoint(circle, glyphRadius, angle),
                    ringPoint(circle, glyphRadius, nextAngle),
                    innerColor,
                    1.35F);
            drawLine(
                    event,
                    ringPoint(circle, innerRadius, angle),
                    ringPoint(circle, innerRadius, nextAngle),
                    outerColor,
                    2.8F);
            if (i % 3 == 0) {
                drawLine(
                        event,
                        ringPoint(circle, spokeInnerRadius, angle),
                        ringPoint(circle, spokeOuterRadius, angle),
                        innerColor,
                        1.1F);
            }
        }

        for (int j = 0; j < 2; j++) {
            int slot = j == 0 ? 10 : 21;
            double markerAngle = rotation + 6.283185307179586 * (slot + 0.5) / 22.0;
            double markerSize = radius * 0.025;
            Vec3d left = glyphPoint(circle, glyphRadius, markerAngle, -markerSize, 0.0);
            Vec3d top = glyphPoint(circle, glyphRadius, markerAngle, 0.0, markerSize);
            Vec3d right = glyphPoint(circle, glyphRadius, markerAngle, markerSize, 0.0);
            Vec3d bottom = glyphPoint(circle, glyphRadius, markerAngle, 0.0, -markerSize);
            drawLine(event, left, top, outerColor, 3.0F);
            drawLine(event, top, right, outerColor, 3.0F);
            drawLine(event, right, bottom, outerColor, 3.0F);
            drawLine(event, bottom, left, outerColor, 3.0F);
        }
    }

    private void drawLogoText(
            WorldRenderEvent event,
            JumpCircles.JumpCircle circle,
            double radius,
            double rotation,
            int alpha) {
        double glyphRadius = radius * 0.83;
        double slotAngle = 0.28559933214452665;
        double glyphWidth = radius * 0.13;
        double glyphHeight = radius * 0.17;
        Color shadowColor = new Color(102, 17, 168, Math.max(48, alpha * 4 / 5));
        Color textColor = new Color(255, 242, 255, Math.min(255, alpha + 24));

        for (int row = 0; row < 2; row++) {
            int offset = row == 0 ? 0 : 11;

            for (int index = 0; index < "ODINCLIENT".length(); index++) {
                double angle = rotation + (offset + index + 0.5) * slotAngle;

                for (float[] stroke : GLYPH_STROKES[index]) {
                    double endX = (stroke[2] - 0.5) * glyphWidth;
                    double endY = (0.5 - stroke[3]) * glyphHeight;
                    Vec3d start =
                            glyphPoint(
                                    circle,
                                    glyphRadius,
                                    angle,
                                    (stroke[0] - 0.5) * glyphWidth,
                                    (0.5 - stroke[1]) * glyphHeight);
                    Vec3d end = glyphPoint(circle, glyphRadius, angle, endX, endY);
                    drawLine(event, start, end, shadowColor, 6.2F);
                    drawLine(event, start, end, textColor, 3.6F);
                }
            }
        }
    }

    private static Vec3d ringPoint(JumpCircles.JumpCircle circle, double radius, double angle) {
        return new Vec3d(
                circle.x + Math.cos(angle) * radius, circle.y, circle.z + Math.sin(angle) * radius);
    }

    private static Vec3d glyphPoint(
            JumpCircles.JumpCircle circle,
            double radius,
            double angle,
            double offsetX,
            double offsetY) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3d(
                circle.x + cos * (radius + offsetY) - sin * offsetX,
                circle.y + 0.004,
                circle.z + sin * (radius + offsetY) + cos * offsetX);
    }

    private static void drawLine(
            WorldRenderEvent event, Vec3d from, Vec3d to, Color color, float width) {
        StableLineRenderer.setNextLineWidth(width);
        event.renderer.stableLine(from, to, color, width);
    }

    private void addCircle(double x, double y, double z, boolean landing) {
        if (ThemeModule.areAnimationsEnabled()) {
            while (this.circles.size() >= 8) {
                this.circles.removeFirst();
            }

            double rotationSeed = (x * 31.0 + z * 17.0) % 6.283185307179586;
            this.circles.addLast(
                    new JumpCircles.JumpCircle(x, y, z, System.nanoTime(), rotationSeed, landing));
        }
    }

    private void removeExpired(long now) {
        while (!this.circles.isEmpty() && now - this.circles.peekFirst().createdAt >= 1050000000L) {
            this.circles.removeFirst();
        }
    }

    private void rememberGroundPosition(double x, double y, double z) {
        this.lastGroundX = x;
        this.lastGroundY = y;
        this.lastGroundZ = z;
    }

    private void resetState() {
        this.trackedWorld = this.mc.world;
        this.initialized = false;
        this.wasOnGround = false;
    }

    private static int computeAlpha(double progress) {
        double fadeIn = Math.min(1.0, progress / 0.12);
        double fadeOut = Math.min(1.0, (1.0 - progress) / 0.34);
        double pulse = 0.9 + 0.1 * Math.sin(progress * 3.141592653589793);
        return (int) Math.round(235.0 * Math.min(fadeIn, fadeOut) * pulse);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    static {
        GLYPH_STROKES =
                new float[][][] {
                    {
                        {0.2F, 0.0F, 0.8F, 0.0F},
                        {0.8F, 0.0F, 1.0F, 0.2F},
                        {1.0F, 0.2F, 1.0F, 0.8F},
                        {1.0F, 0.8F, 0.8F, 1.0F},
                        {0.8F, 1.0F, 0.2F, 1.0F},
                        {0.2F, 1.0F, 0.0F, 0.8F},
                        {0.0F, 0.8F, 0.0F, 0.2F},
                        {0.0F, 0.2F, 0.2F, 0.0F}
                    },
                    {
                        {0.0F, 0.0F, 0.0F, 1.0F},
                        {0.0F, 0.0F, 0.75F, 0.0F},
                        {0.75F, 0.0F, 1.0F, 0.22F},
                        {1.0F, 0.22F, 1.0F, 0.78F},
                        {1.0F, 0.78F, 0.75F, 1.0F},
                        {0.75F, 1.0F, 0.0F, 1.0F}
                    },
                    {
                        {0.0F, 0.0F, 1.0F, 0.0F},
                        {0.5F, 0.0F, 0.5F, 1.0F},
                        {0.0F, 1.0F, 1.0F, 1.0F}
                    },
                    {
                        {0.0F, 1.0F, 0.0F, 0.0F},
                        {0.0F, 0.0F, 1.0F, 1.0F},
                        {1.0F, 1.0F, 1.0F, 0.0F}
                    },
                    {
                        {0.95F, 0.0F, 0.2F, 0.0F},
                        {0.2F, 0.0F, 0.0F, 0.2F},
                        {0.0F, 0.2F, 0.0F, 0.8F},
                        {0.0F, 0.8F, 0.2F, 1.0F},
                        {0.2F, 1.0F, 0.95F, 1.0F}
                    },
                    {
                        {0.0F, 0.0F, 0.0F, 1.0F},
                        {0.0F, 1.0F, 1.0F, 1.0F}
                    },
                    {
                        {0.0F, 0.0F, 1.0F, 0.0F},
                        {0.5F, 0.0F, 0.5F, 1.0F},
                        {0.0F, 1.0F, 1.0F, 1.0F}
                    },
                    {
                        {1.0F, 0.0F, 0.0F, 0.0F},
                        {0.0F, 0.0F, 0.0F, 1.0F},
                        {0.0F, 0.5F, 0.82F, 0.5F},
                        {0.0F, 1.0F, 1.0F, 1.0F}
                    },
                    {
                        {0.0F, 1.0F, 0.0F, 0.0F},
                        {0.0F, 0.0F, 1.0F, 1.0F},
                        {1.0F, 1.0F, 1.0F, 0.0F}
                    },
                    {
                        {0.0F, 0.0F, 1.0F, 0.0F},
                        {0.5F, 0.0F, 0.5F, 1.0F}
                    }
                };
    }

    private record JumpCircle(
            double x, double y, double z, long createdAt, double rotationSeed, boolean landing) {}
}
