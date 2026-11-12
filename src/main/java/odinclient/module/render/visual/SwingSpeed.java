package odinclient.module.render.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

import odinclient.core.Categories;
import odinclient.event.AttackBlockEvent;
import odinclient.event.AttackEntityEvent;
import odinclient.event.EventHandler;
import odinclient.event.PacketEvent;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.module.Module;
import odinclient.module.ModuleContext;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.IntSetting;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class SwingSpeed extends Module {
    private static final int BASE_SWING_TICKS = 6;
    private static final int DISABLED_SPEED = 10;
    private static final int DEFAULT_SPEED = 5;
    private static SwingSpeed instance;
    private final SettingGroup general = this.settings.getGeneral();
    private final Setting<Integer> speed =
            this.general.add(
                    new IntSetting.Builder()
                            .name("speed")
                            .description(
                                    "Visual swing animation speed. 0 is slow, 9 is fast, 10"
                                        + " disables the animation.")
                            .defaultValue(5)
                            .range(0, 10)
                            .sliderRange(0, 10)
                            .build());
    private boolean customSwingActive;
    private int tickCounter;
    private int swingTicks;
    private float prevSwingProgress;
    private float swingProgress;
    private int lastSwingStartTick = -1000;
    private int lastUseTick = -1000;
    private int lastAttackTick = -1000;
    private int lastPlayerAge = -2147483648;
    private Hand swingHand;
    private Hand lastSwingHand;
    private Hand useHand;
    private Object lastPlayer;
    private Object lastWorld;

    public SwingSpeed() {
        super(Categories.MAIN, "swingspeed", "Visual-only hand swing animation speed.");
        instance = this;
    }

    public static void forceReset() {
        if (instance != null) {
            instance.resetAndReregister();
        }
    }

    public static void tickIfActive() {
        if (instance != null && instance.isActive()) {
            instance.onTick(null);
        }
    }

    @Override
    public void onActivate() {
        this.reset(true);
    }

    @Override
    public void onDeactivate() {
        this.reset(true);
    }

    @EventHandler
    private void onWorldJoin(WorldJoinEvent event) {
        this.reset(true);
    }

    @EventHandler
    private void onWorldLeave(WorldLeaveEvent event) {
        this.reset(true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player != null) {
            int playerAge = this.mc.player.age;
            if (playerAge == this.lastPlayerAge) {
                return;
            }

            this.lastPlayerAge = playerAge;
        } else {
            this.lastPlayerAge = -2147483648;
        }

        this.tickCounter++;
        if (this.mc.player == null) {
            this.clearSwing(false);
            this.lastPlayer = null;
            this.lastWorld = null;
        } else {
            if (this.lastPlayer != this.mc.player || this.lastWorld != this.mc.world) {
                this.reset(true);
                this.lastPlayer = this.mc.player;
                this.lastWorld = this.mc.world;
            }

            if (this.useHand != null && this.tickCounter - this.lastUseTick > 2) {
                this.useHand = null;
            }

            int speedValue = this.speed.getValue();
            if (speedValue == 5) {
                this.clearSwing(false);
            } else if (speedValue >= 10 && this.mc.player.handSwinging) {
                this.disableSwingAnimation(this.mc.player.preferredHand);
            } else {
                if (this.mc.player.handSwinging && !this.customSwingActive) {
                    Hand hand = this.normalizeHand(this.mc.player.preferredHand);
                    if (this.shouldSkipVanillaSwing(hand)) {
                        this.cancelVanillaSwing(hand);
                        return;
                    }

                    this.startSwing(hand, false);
                }

                if (this.customSwingActive) {
                    int duration = this.getSwingDuration();
                    this.prevSwingProgress = this.swingProgress;
                    int nextTicks = this.swingTicks + 1;
                    this.swingTicks = nextTicks;
                    this.swingProgress =
                            Math.max(0.0F, Math.min(1.0F, (float) nextTicks / duration));
                    if (this.swingTicks >= duration) {
                        this.mc.player.lastHandSwingProgress = 1.0F;
                        this.mc.player.handSwingProgress = 1.0F;
                        this.mc.player.handSwingTicks = 6;
                        this.mc.player.handSwinging = false;
                        this.clearSwing(false);
                    } else {
                        this.mc.player.handSwinging = true;
                        this.mc.player.preferredHand = this.normalizeHand(this.swingHand);
                        this.mc.player.lastHandSwingProgress = this.prevSwingProgress;
                        this.mc.player.handSwingProgress = this.swingProgress;
                        int ticks = Math.round(this.swingProgress * 6.0F);
                        this.mc.player.handSwingTicks = Math.max(0, Math.min(5, ticks));
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (this.mc.player != null && event.packet != null) {
            if (event.packet instanceof HandSwingC2SPacket swingPacket) {
                Hand hand = this.normalizeHand(swingPacket.getHand());
                if (this.shouldSkipVanillaSwing(hand)) {
                    this.cancelVanillaSwing(hand);
                    return;
                }

                this.startSwing(hand, false);
            }
        }
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        this.lastAttackTick = this.tickCounter;
        this.startSwing(Hand.MAIN_HAND, true);
    }

    @EventHandler
    private void onAttackBlock(AttackBlockEvent event) {
        this.lastAttackTick = this.tickCounter;
        this.startSwing(Hand.MAIN_HAND, false);
    }

    private void onUse(Hand hand) {
        Hand normalized = this.normalizeHand(hand);
        if (!this.isUseItem(normalized)) {
            this.useHand = normalized;
            this.lastUseTick = this.tickCounter;
            this.cancelVanillaSwing(normalized);
        } else {
            this.startSwing(normalized, true);
        }
    }

    private void startSwing(Hand hand, boolean force) {
        if (this.mc.player != null) {
            Hand normalized = this.normalizeHand(hand);
            int speedValue = this.speed.getValue();
            if (speedValue != 5) {
                if (speedValue >= 10) {
                    this.disableSwingAnimation(normalized);
                } else if (!this.alreadySwungThisTick(normalized)
                        && (!this.customSwingActive || force)) {
                    this.beginCustomSwing(normalized);
                }
            }
        }
    }

    private boolean shouldSkipVanillaSwing(Hand hand) {
        if (hand == Hand.MAIN_HAND && this.tickCounter - this.lastAttackTick <= 1) {
            return false;
        } else {
            return this.useHand == hand && this.tickCounter - this.lastUseTick <= 2
                    || hand == Hand.OFF_HAND && !this.isUseItem(hand);
        }
    }

    private boolean alreadySwungThisTick(Hand hand) {
        return this.tickCounter == this.lastSwingStartTick && hand == this.lastSwingHand;
    }

    private void beginCustomSwing(Hand hand) {
        this.swingHand = hand;
        this.lastSwingHand = hand;
        this.lastSwingStartTick = this.tickCounter;
        this.customSwingActive = true;
        this.swingTicks = 0;
        this.prevSwingProgress = 0.0F;
        this.swingProgress = 0.0F;
        this.mc.player.handSwinging = true;
        this.mc.player.preferredHand = this.swingHand;
        this.mc.player.handSwingTicks = 0;
        this.mc.player.lastHandSwingProgress = 0.0F;
        this.mc.player.handSwingProgress = 0.0F;
    }

    private void disableSwingAnimation(Hand hand) {
        this.swingHand = this.normalizeHand(hand);
        this.lastSwingHand = this.swingHand;
        this.lastSwingStartTick = this.tickCounter;
        this.customSwingActive = false;
        this.swingTicks = 0;
        this.prevSwingProgress = 0.0F;
        this.swingProgress = 0.0F;
        this.mc.player.preferredHand = this.swingHand;
        this.mc.player.lastHandSwingProgress = 0.0F;
        this.mc.player.handSwingProgress = 0.0F;
        this.mc.player.handSwingTicks = 0;
        this.mc.player.handSwinging = false;
    }

    private void cancelVanillaSwing(Hand hand) {
        if (this.mc.player != null) {
            Hand normalized = this.normalizeHand(hand);
            if (this.swingHand == normalized) {
                this.customSwingActive = false;
            }

            if (this.mc.player.preferredHand == normalized) {
                this.mc.player.handSwinging = false;
                this.mc.player.lastHandSwingProgress = 0.0F;
                this.mc.player.handSwingProgress = 0.0F;
                this.mc.player.handSwingTicks = 0;
            }
        }
    }

    private Hand normalizeHand(Hand hand) {
        return hand == null ? Hand.MAIN_HAND : hand;
    }

    private boolean isUseItem(Hand hand) {
        ItemStack stack = this.getHandStack(hand);
        if (stack != null && !stack.isEmpty()) {
            Item item = stack.getItem();
            String itemName =
                    String.valueOf(item).toLowerCase(Locale.ROOT)
                            + " "
                            + item.getTranslationKey().toLowerCase(Locale.ROOT);
            if (itemName.contains("totem_of_undying")) {
                return false;
            } else if (item instanceof BlockItem) {
                return true;
            } else {
                return stack.getUseAction() != UseAction.NONE
                        || itemName.contains("firework")
                        || itemName.contains("ender_pearl")
                        || itemName.contains("experience_bottle")
                        || itemName.contains("snowball")
                        || itemName.contains("egg")
                        || itemName.contains("potion")
                        || itemName.contains("bow")
                        || itemName.contains("crossbow")
                        || itemName.contains("trident")
                        || itemName.contains("shield")
                        || itemName.contains("fishing_rod")
                        || itemName.contains("spyglass")
                        || itemName.contains("goat_horn")
                        || itemName.contains("brush")
                        || itemName.contains("bucket")
                        || itemName.contains("flint_and_steel")
                        || itemName.contains("bone_meal");
            }
        } else {
            return false;
        }
    }

    private ItemStack getHandStack(Hand hand) {
        return this.mc.player == null
                ? null
                : this.mc.player.getStackInHand(this.normalizeHand(hand));
    }

    private void reset(boolean clearPlayerState) {
        this.clearSwing(clearPlayerState);
        this.useHand = null;
        this.lastUseTick = -1000;
        this.lastAttackTick = -1000;
        this.lastPlayerAge = -2147483648;
        this.lastSwingHand = null;
        this.lastSwingStartTick = -1000;
        if (this.mc.player == null) {
            this.lastPlayer = null;
        }

        if (this.mc.world == null) {
            this.lastWorld = null;
        }
    }

    private void resetAndReregister() {
        this.reset(true);
        this.lastPlayer = null;
        this.lastWorld = null;
        if (this.isActive()) {
            try {
                ModuleContext.EVENT_BUS.unregister(this);
            } catch (Throwable unregisterError) {
            }

            try {
                ModuleContext.EVENT_BUS.register(this);
            } catch (Throwable registerError) {
            }
        }
    }

    private void clearSwing(boolean clearPlayerState) {
        if (clearPlayerState && this.mc.player != null) {
            this.mc.player.handSwinging = false;
            this.mc.player.lastHandSwingProgress = 0.0F;
            this.mc.player.handSwingProgress = 0.0F;
            this.mc.player.handSwingTicks = 0;
        }

        this.customSwingActive = false;
        this.swingTicks = 0;
        this.prevSwingProgress = 0.0F;
        this.swingProgress = 0.0F;
        this.swingHand = null;
    }

    private int getSwingDuration() {
        int clamped = Math.max(0, Math.min(9, this.speed.getValue()));
        if (clamped < 5) {
            double slowness = (5 - clamped) / 5.0;
            return 6 + (int) Math.round(slowness * slowness * 34.0);
        } else {
            return Math.max(2, 6 - (clamped - 5));
        }
    }
}
