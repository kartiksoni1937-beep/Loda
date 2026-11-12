package odinclient.core;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.manager.ConfigStore;
import odinclient.manager.ModuleManager;
import odinclient.module.Category;
import odinclient.module.Module;
import odinclient.module.antivanish.AntiVanishActivityMonitor;
import odinclient.module.antivanish.AntiVanishDetection;
import odinclient.module.automation.AutoLog;
import odinclient.module.automation.AutoMine;
import odinclient.module.automation.AutoRelog;
import odinclient.module.automation.AutoTool;
import odinclient.module.automation.FastPlace;
import odinclient.module.combat.AimAssist;
import odinclient.module.combat.AnchorMacro;
import odinclient.module.combat.AutoCrystal;
import odinclient.module.combat.AutoInvTotem;
import odinclient.module.combat.AutoTotem;
import odinclient.module.combat.CrystalOptimizer;
import odinclient.module.combat.HoverTotem;
import odinclient.module.combat.KeyPearl;
import odinclient.module.combat.MaceSwap;
import odinclient.module.combat.SafeAnchor;
import odinclient.module.combat.ShieldBreaker;
import odinclient.module.combat.Triggerbot;
import odinclient.module.fake.FakeElytra;
import odinclient.module.fake.FakePay;
import odinclient.module.fake.FakeRoles;
import odinclient.module.fake.FakeSpawner;
import odinclient.module.gamble.GambleRigger;
import odinclient.module.hud.HudModule;
import odinclient.module.hud.InventoryHud;
import odinclient.module.hud.StaffTracker;
import odinclient.module.hud.ThemeModule;
import odinclient.module.hud.regionmap.RegionMap;
import odinclient.module.movement.AutoFirework;
import odinclient.module.movement.Freelook;
import odinclient.module.playerchunk.LightChunkTracker;
import odinclient.module.playerchunk.PlayerFinder;
import odinclient.module.playerdebug.PlayerFinderV2;
import odinclient.module.privacy.CordSnapper;
import odinclient.module.privacy.NameProtect;
import odinclient.module.privacy.PlayerNotify;
import odinclient.module.render.esp.BedrockHoleESP;
import odinclient.module.render.esp.BlockEsp;
import odinclient.module.render.esp.BlockOutlines;
import odinclient.module.render.esp.HoleESP;
import odinclient.module.render.esp.PearlESP;
import odinclient.module.render.esp.PlayerEsp;
import odinclient.module.render.esp.StorageEsp;
import odinclient.module.render.itemglow.ItemGlow;
import odinclient.module.render.sky.SkyArts;
import odinclient.module.render.visual.ChinaHead;
import odinclient.module.render.visual.Fullbright;
import odinclient.module.render.visual.JumpCircles;
import odinclient.module.render.visual.PearlTrajectory;
import odinclient.module.render.visual.ToolSize;
import odinclient.module.shortcut.ChatMacros;
import odinclient.module.shortcut.HomeReset;
import odinclient.module.shortcut.KeyHomeSetter;
import odinclient.module.shortcut.KeyRtp;
import odinclient.module.world.AmethystESP;
import odinclient.module.world.ChunkFinder;
import odinclient.module.world.SusChunkFinder;
import odinclient.ui.widget.GuiKeybind;

import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public final class OdinClient implements ClientModInitializer {
    private static final String[] MODULE_CLASS_NAMES =
            new String[] {
                "odinclient.module.spawner.SpawnerFlagger",
                "odinclient.module.render.esp.Nametags",
                "odinclient.module.render.visual.SwingSpeed",
                "odinclient.module.fake.FakeStats",
                "odinclient.module.render.visual.RenderCycle",
                "odinclient.module.movement.Freecam",
                "odinclient.module.spotify.SpotifyHud",
                "odinclient.module.world.BlockNotifier",
                "odinclient.module.world.WeatherNotifier",
                "odinclient.module.privacy.SkinProtect",
                "odinclient.module.discord.DiscordPresents",
                "odinclient.module.debug.BlockEntityDebug",
                "odinclient.module.world.LightFinder",
                "odinclient.module.render.esp.SuspiciousESP",
                "odinclient.module.render.esp.MobESP",
                "odinclient.module.movement.Sprint",
                "odinclient.module.render.visual.Zoom"
            };
    private static boolean initialized;

    @Override
    public void onInitializeClient() {
        initialize();
    }

    public static synchronized void initialize() {
        if (!initialized) {
            ModuleManager manager = ModuleManager.get();
            Category category = Categories.MAIN;
            ModuleManager.registerCategory(category);
            ModuleManager.registerCategory(ModuleCategories.MAIN);
            LightChunkTracker.register();

            for (String className : MODULE_CLASS_NAMES) {
                try {
                    Module module =
                            (Module)
                                    Class.forName(className)
                                            .getConstructor()
                                            .newInstance();
                    if ("odinclient-freecam".equals(module.name)) {
                        module.rename("freecam");
                    } else if ("odinclient-spawner-protect".equals(module.name)) {
                        module.rename("spawner-protect");
                    } else if ("odinclient-block-notifier".equals(module.name)) {
                        module.rename("block-notifier");
                    }

                    if (manager.getByName(module.name) == null) {
                        manager.register(module);
                    }
                } catch (Throwable ignored) {
                }
            }

            registerIfAbsent(manager, "theme", () -> new ThemeModule(category));
            registerIfAbsent(manager, "auto-tool", () -> new AutoTool(category));
            registerIfAbsent(manager, "auto-mine", () -> new AutoMine(category));
            registerIfAbsent(manager, "key-pearl", () -> new KeyPearl(category));
            registerIfAbsent(manager, "fast-place", () -> new FastPlace(category));
            registerIfAbsent(manager, "freelook", () -> new Freelook(category));
            registerIfAbsent(manager, "auto-totem", () -> new AutoTotem(category));
            registerIfAbsent(manager, "shield-breaker", () -> new ShieldBreaker(category));
            registerIfAbsent(manager, "triggerbot", () -> new Triggerbot(category));
            registerIfAbsent(manager, "aim-assist", () -> new AimAssist(category));
            registerIfAbsent(manager, "mace-swap", () -> new MaceSwap(category));
            registerIfAbsent(manager, "crystal-optimizer", () -> new CrystalOptimizer(category));
            registerIfAbsent(manager, "auto-inv-totem", () -> new AutoInvTotem(category));
            registerIfAbsent(manager, "safe-anchor", () -> new SafeAnchor(category));
            registerIfAbsent(manager, "anchor-macro", () -> new AnchorMacro(category));
            registerIfAbsent(manager, "auto-crystal", () -> new AutoCrystal(category));
            registerIfAbsent(manager, "hud", () -> new HudModule(category));
            registerIfAbsent(manager, "hover-totem", () -> new HoverTotem(category));
            registerIfAbsent(manager, "fullbright", () -> new Fullbright(category));
            registerIfAbsent(manager, "block-esp", () -> new BlockEsp(category));
            registerIfAbsent(manager, "block-outlines", () -> new BlockOutlines(category));
            registerIfAbsent(manager, "item-glow", () -> new ItemGlow(category));
            registerIfAbsent(manager, "inventory-hud", () -> new InventoryHud(category));
            registerIfAbsent(manager, "chat-macros", () -> new ChatMacros(category));
            registerIfAbsent(manager, "fake-pay", () -> new FakePay(ModuleCategories.MAIN));
            registerIfAbsent(manager, "fake-elytra", () -> new FakeElytra(ModuleCategories.MAIN));
            registerIfAbsent(manager, "fake-spawner", () -> new FakeSpawner(ModuleCategories.MAIN));
            registerIfAbsent(
                    manager, "gamble-rigger", () -> new GambleRigger(ModuleCategories.MAIN));
            registerIfAbsent(manager, "bedrock-hole-esp", () -> new BedrockHoleESP(category));
            registerIfAbsent(manager, "pearl-esp", PearlESP::new);
            registerIfAbsent(manager, "pearl-trajectory", PearlTrajectory::new);
            registerIfAbsent(manager, "hole-esp", HoleESP::new);
            registerIfAbsent(manager, "chunkfinder", ChunkFinder::new);
            registerIfAbsent(manager, "jump-circles", JumpCircles::new);
            registerIfAbsent(manager, "sus-chunk-finder", () -> new SusChunkFinder(category));
            registerIfAbsent(manager, "player-chunk", () -> new PlayerFinder(category));
            registerIfAbsent(manager, "player-debug-v2", PlayerFinderV2::new);
            registerIfAbsent(manager, "antivanish-detection", AntiVanishDetection::new);
            registerIfAbsent(manager, "auto-firework", AutoFirework::new);
            registerIfAbsent(manager, "auto-relog", AutoRelog::new);
            registerIfAbsent(manager, "autolog", AutoLog::new);
            registerIfAbsent(manager, "player-notify", PlayerNotify::new);
            registerIfAbsent(manager, "region-map", () -> new RegionMap(category));
            registerIfAbsent(manager, "key-homesetter", () -> new KeyHomeSetter(category));
            registerIfAbsent(manager, "home-reset", () -> new HomeReset(category));
            registerIfAbsent(manager, "key-rtp", () -> new KeyRtp(category));
            registerIfAbsent(manager, "player-esp", () -> new PlayerEsp(category));
            registerIfAbsent(manager, "storage-esp", () -> new StorageEsp(category));
            registerIfAbsent(manager, "fake-roles", () -> new FakeRoles(category));
            registerIfAbsent(manager, "name-protect", () -> new NameProtect(category));
            registerIfAbsent(manager, "tool-size", () -> new ToolSize(category));
            registerIfAbsent(manager, "staf-tracker", () -> new StaffTracker(category));
            registerIfAbsent(manager, "cord-snapper", () -> new CordSnapper(category));
            registerIfAbsent(manager, "sky-arts", () -> new SkyArts(category));
            registerIfAbsent(manager, "china-head", ChinaHead::new);
            AmethystESP.install();
            AntiVanishActivityMonitor.register();
            manager.sortByName();
            initialized = true;
            GuiKeybind.init();

            try {
                ConfigStore.load();
            } catch (Throwable e) {
            }
        }
    }

    private static void registerIfAbsent(
            ModuleManager manager, String name, Supplier<Module> factory) {
        if (manager.getByName(name) == null) {
            try {
                manager.register(factory.get());
            } catch (Throwable ignored) {
            }
        }
    }
}
