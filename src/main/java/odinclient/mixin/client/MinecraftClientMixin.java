package odinclient.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;

import odinclient.event.ScreenOpenEvent;
import odinclient.event.TickEvent;
import odinclient.event.WorldJoinEvent;
import odinclient.event.WorldLeaveEvent;
import odinclient.manager.ConfigStore;
import odinclient.manager.ModuleManager;
import odinclient.module.Module;
import odinclient.module.ModuleContext;
import odinclient.util.ActionLock;
import odinclient.util.ActionScheduler;
import odinclient.module.automation.FastPlace;
import odinclient.module.combat.AutoCrystal;
import odinclient.module.combat.MaceSwap;
import odinclient.module.render.visual.SwingSpeed;
import odinclient.ui.screen.IntroSplashScreen;
import odinclient.ui.screen.OdinGuiScreen;
import odinclient.ui.widget.GuiKeybind;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Unique private static final String ODINCLIENT_MENU_CLASS = "odinclient.ui.screen.ClickGuiScreen";

    @Unique
    private static final String ODINCLIENT_PANEL_MENU_CLASS = "odinclient.ui.screen.PanelGuiScreen";

    @Unique private static final String ODINCLIENT_NEW_MENU_CLASS = "odinclient.ui.screen.OdinGuiScreen";
    @Unique private boolean odinclient$inWorld;
    @Unique private boolean odinclient$menuKeyDown;
    @Unique private int odinclient$lastMenuKey = 48;
    @Unique private final Map<Module, Boolean> odinclient$keyStates = new IdentityHashMap<>();
    @Unique private final Map<Module, Integer> odinclient$boundKeys = new IdentityHashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void odinclient$preTick(CallbackInfo ci) {
        ActionLock.clear();
        this.odinclient$updateWorldState();
        ActionScheduler.beginTickStart((MinecraftClient) (Object) this);
        ModuleContext.EVENT_BUS.post(new TickEvent.Pre());
        ActionScheduler.endTickStart();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void odinclient$postTick(CallbackInfo ci) {
        SwingSpeed.tickIfActive();
        ModuleContext.EVENT_BUS.post(new TickEvent.Post());
        ConfigStore.onTick();
        this.odinclient$updateWorldState();
        this.odinclient$handleMenuHotkey();
        this.odinclient$handleModuleKeybinds();
        IntroSplashScreen.tick((MinecraftClient) (Object) this);
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void odinclient$postItemUse(CallbackInfo ci) {
        FastPlace.applyFastPlace((MinecraftClient) (Object) this);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void odinclient$cancelAutoCrystalItemUse(CallbackInfo ci) {
        if (AutoCrystal.isActing()) {
            ci.cancel();
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void odinclient$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MaceSwap.onAttack()) {
            // The swap is sent this tick; the hit is re-issued on the following tick.
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void odinclient$openScreen(Screen screen, CallbackInfo ci) {
        ScreenOpenEvent event = ModuleContext.EVENT_BUS.post(new ScreenOpenEvent(screen));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Unique
    private void odinclient$updateWorldState() {
        MinecraftClient client = (MinecraftClient) (Object) this;
        boolean inWorld = client.world != null && client.player != null;
        if (inWorld != this.odinclient$inWorld) {
            this.odinclient$inWorld = inWorld;
            ModuleContext.EVENT_BUS.post(inWorld ? new WorldJoinEvent() : new WorldLeaveEvent());
        }
    }

    @Unique
    private void odinclient$handleMenuHotkey() {
        MinecraftClient client = (MinecraftClient) (Object) this;
        int keyCode = GuiKeybind.getKeyCode();
        boolean down = this.odinclient$isBindDown(client, keyCode);
        if (this.odinclient$isTextInputActive(client.currentScreen)) {
            this.odinclient$lastMenuKey = keyCode;
            this.odinclient$menuKeyDown = down;
        } else if (keyCode != this.odinclient$lastMenuKey) {
            this.odinclient$lastMenuKey = keyCode;
            this.odinclient$menuKeyDown = down;
        } else {
            if (down && !this.odinclient$menuKeyDown) {
                Screen current = client.currentScreen;
                String screenClass = current == null ? "" : current.getClass().getName();
                if (screenClass.equals("odinclient.ui.screen.ClickGuiScreen")
                        || screenClass.equals("odinclient.ui.screen.PanelGuiScreen")
                        || screenClass.equals("odinclient.ui.screen.OdinGuiScreen")) {
                    current.close();
                } else if (!(current instanceof IntroSplashScreen)) {
                    client.setScreen(new OdinGuiScreen(current));
                }
            }

            this.odinclient$menuKeyDown = down;
        }
    }

    @Unique
    private boolean odinclient$isTextInputActive(Screen screen) {
        if (screen instanceof OdinGuiScreen gui && gui.isCapturingInput()) {
            return true;
        } else if (screen instanceof ChatScreen) {
            return true;
        } else if (screen == null) {
            return false;
        } else {
            for (Element child : screen.children()) {
                if (child instanceof TextFieldWidget field && field.isFocused()) {
                    return true;
                }
            }

            return false;
        }
    }

    @Unique
    private boolean odinclient$isBindDown(MinecraftClient client, int key) {
        return key >= 1000 && key <= 1007
                ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key - 1000) == 1
                : key >= 0 && key <= 348 && InputUtil.isKeyPressed(client.getWindow(), key);
    }

    @Unique
    private void odinclient$handleModuleKeybinds() {
        MinecraftClient client = (MinecraftClient) (Object) this;
        boolean noScreen = client.currentScreen == null;
        int menuKey = GuiKeybind.getKeyCode();

        for (Module module : ModuleManager.get().getAll()) {
            int key = module.keybind.getValue();
            boolean down = key != menuKey && this.odinclient$isBindDown(client, key);
            Integer previousKey = this.odinclient$boundKeys.put(module, key);
            Boolean wasDown = this.odinclient$keyStates.put(module, down);
            if (previousKey != null
                    && previousKey == key
                    && wasDown != null
                    && noScreen
                    && down
                    && !wasDown) {
                module.toggle();
            }
        }
    }
}
