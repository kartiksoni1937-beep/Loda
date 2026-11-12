package odinclient.module.fake;

import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardCriterion.RenderType;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import odinclient.core.Categories;
import odinclient.event.EventHandler;
import odinclient.event.TickEvent;
import odinclient.module.Module;
import odinclient.setting.Setting;
import odinclient.setting.SettingGroup;
import odinclient.setting.type.BooleanSetting;
import odinclient.setting.type.StringSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FakeStats extends Module {
    private static final String[] ICONS;
    private static volatile FakeStats instance;
    private final SettingGroup settingGroup;
    private final Setting<Boolean> displayScoreboard;
    private final Setting<String> title;
    private final Setting<String> money;
    private final Setting<String> shards;
    private final Setting<String> kills;
    private final Setting<String> deaths;
    private final Setting<String> playtime;
    private final Setting<String>[] statSettings;
    private final String[] realValues;
    private Text originalTitle;
    private final List<String> fakeTeamNames;
    private ScoreboardObjective fakeObjective;
    private ScoreboardObjective originalObjective;
    private boolean originalObjectiveCaptured;
    private boolean needsInit;
    private boolean fakeScoreboardActive;
    private long lastRebuildTime;

    public FakeStats() {
        super(Categories.MAIN, "fake-stats", "Changes your stats client side on Donut.");
        this.realValues = new String[ICONS.length];
        this.fakeTeamNames = new ArrayList<>();
        this.settingGroup = this.settings.getGeneral();
        this.displayScoreboard =
                this.booleanSetting(
                        "display-scoreboard", "Displays a custom fake sidebar scoreboard.", false);
        this.title = this.stringSetting("title", "1");
        this.money = this.stringSetting("money", "2");
        this.shards = this.stringSetting("shards", "3");
        this.kills = this.stringSetting("kills", "4");
        this.deaths = this.stringSetting("deaths", "5");
        this.playtime = this.stringSetting("playtime", "6");
        this.statSettings =
                new Setting[] {this.money, this.shards, this.kills, this.deaths, this.playtime};
        instance = this;
    }

    private Setting<Boolean> booleanSetting(String name, String description, boolean defaultValue) {
        BooleanSetting.Builder builder =
                new BooleanSetting.Builder()
                        .name(name)
                        .description(description)
                        .defaultValue(defaultValue);
        return this.settingGroup.add(builder.build());
    }

    private Setting<String> stringSetting(String name, String description) {
        StringSetting.Builder builder =
                new StringSetting.Builder().name(name).description(description).defaultValue("");
        return this.settingGroup.add(builder.build());
    }

    @Override
    public void onActivate() {
        Arrays.fill(this.realValues, null);
        this.originalTitle = null;
        this.needsInit = true;
    }

    @Override
    public void onDeactivate() {
        this.restoreScoreboard();
        if (this.mc.world != null && this.originalTitle != null) {
            ScoreboardObjective sidebar =
                    this.mc
                            .world
                            .getScoreboard()
                            .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (sidebar != null) {
                sidebar.setDisplayName(this.originalTitle);
            }
        }

        this.originalTitle = null;
    }

    public String replaceStats(String text) {
        if (this.isActive() && !this.displayScoreboard.getValue()) {
            for (int i = 0; i < ICONS.length; i++) {
                String fake = this.statSettings[i].getValue();
                if (!fake.isEmpty()
                        && this.realValues[i] != null
                        && !this.realValues[i].isEmpty()) {
                    text = text.replace(this.realValues[i], fake.replace('&', '\u00a7'));
                }
            }
        }

        return text;
    }

    public static String applyFakeStats(String text) {
        FakeStats stats = instance;
        return stats == null ? text : stats.replaceStats(text);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.displayScoreboard.getValue()) {
            this.updateFakeScoreboard();
        } else {
            if (this.fakeScoreboardActive
                    || this.originalObjectiveCaptured
                    || this.fakeObjective != null) {
                this.restoreScoreboard();
            }

            if (this.mc.world != null) {
                Scoreboard scoreboard = this.mc.world.getScoreboard();
                ScoreboardObjective objective =
                        scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                if (objective != null) {
                    if (!this.title.getValue().isEmpty()) {
                        if (this.originalTitle == null) {
                            this.originalTitle = objective.getDisplayName();
                        }

                        objective.setDisplayName(
                                Text.literal(this.title.getValue().replace('&', '\u00a7')));
                    }

                    for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                        Team team = scoreboard.getScoreHolderTeam(entry.owner());
                        if (team != null) {
                            String prefix = team.getPrefix().getString();
                            int spaceIndex = prefix.indexOf(32);
                            if (spaceIndex >= 0) {
                                String icon = prefix.substring(0, spaceIndex);
                                String value = prefix.substring(spaceIndex + 1).trim();
                                if (!value.isEmpty()) {
                                    for (int i = 0; i < ICONS.length; i++) {
                                        if (icon.contains(ICONS[i])) {
                                            this.realValues[i] = value;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateFakeScoreboard() {
        if (this.mc.world == null || this.mc.player == null) {
            this.originalObjectiveCaptured = false;
            this.originalObjective = null;
            this.fakeObjective = null;
            this.fakeTeamNames.clear();
            this.needsInit = true;
            this.fakeScoreboardActive = false;
        } else if (!this.needsInit && this.fakeScoreboardActive) {
            if (System.currentTimeMillis() - this.lastRebuildTime >= 1000L) {
                this.rebuildFakeScoreboard();
            }
        } else {
            this.initFakeScoreboard();
        }
    }

    private void initFakeScoreboard() {
        if (this.mc.world != null && this.mc.player != null) {
            if (!this.originalObjectiveCaptured) {
                this.originalObjective =
                        this.mc
                                .world
                                .getScoreboard()
                                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                this.originalObjectiveCaptured = true;
            }

            this.rebuildFakeScoreboard();
            this.needsInit = false;
        }
    }

    private void rebuildFakeScoreboard() {
        if (this.mc.world != null) {
            Scoreboard scoreboard = this.mc.world.getScoreboard();
            this.removeFakeTeams(scoreboard);
            if (this.fakeObjective != null) {
                try {
                    scoreboard.removeObjective(this.fakeObjective);
                } catch (Exception ignored) {
                }
            }

            this.fakeObjective =
                    scoreboard.addObjective(
                            "payfake_sb",
                            ScoreboardCriterion.DUMMY,
                            this.coloredText(this.title.getValue(), 16777215),
                            RenderType.INTEGER,
                            false,
                            BlankNumberFormat.INSTANCE);
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, this.fakeObjective);
            List<MutableText> lines = this.buildLines();

            for (int i = 0; i < lines.size(); i++) {
                String teamName = "payfake_" + i;
                this.fakeTeamNames.add(teamName);
                Team existingTeam = scoreboard.getTeam(teamName);
                if (existingTeam != null) {
                    scoreboard.removeTeam(existingTeam);
                }

                Team team = scoreboard.addTeam(teamName);
                team.setPrefix(lines.get(i));
                String holderName = "\u00a7" + Integer.toHexString(i);
                ScoreHolder holder = ScoreHolder.fromName(holderName);
                scoreboard.removeScore(holder, this.fakeObjective);
                scoreboard.getOrCreateScore(holder, this.fakeObjective).setScore(lines.size() - i);
                scoreboard.addScoreHolderToTeam(holderName, team);
            }

            this.lastRebuildTime = System.currentTimeMillis();
            this.fakeScoreboardActive = true;
        }
    }

    private void restoreScoreboard() {
        if (!this.fakeScoreboardActive
                && !this.originalObjectiveCaptured
                && this.fakeObjective == null
                && this.fakeTeamNames.isEmpty()) {
            this.needsInit = false;
        } else if (this.mc.world == null) {
            this.originalObjectiveCaptured = false;
            this.originalObjective = null;
            this.fakeObjective = null;
            this.fakeTeamNames.clear();
            this.needsInit = false;
            this.fakeScoreboardActive = false;
        } else {
            try {
                Scoreboard scoreboard = this.mc.world.getScoreboard();
                this.removeFakeTeams(scoreboard);
                if (this.fakeObjective != null) {
                    try {
                        scoreboard.removeObjective(this.fakeObjective);
                    } catch (Exception removeError) {
                    }

                    this.fakeObjective = null;
                }

                try {
                    scoreboard.setObjectiveSlot(
                            ScoreboardDisplaySlot.SIDEBAR, this.originalObjective);
                } catch (Exception fallbackError) {
                    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
                }
            } catch (Exception ignored) {
            }

            this.originalObjective = null;
            this.originalObjectiveCaptured = false;
            this.needsInit = false;
            this.fakeScoreboardActive = false;
        }
    }

    private void removeFakeTeams(Scoreboard scoreboard) {
        for (String teamName : this.fakeTeamNames) {
            try {
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    scoreboard.removeTeam(team);
                }
            } catch (Exception ignored) {
            }
        }

        this.fakeTeamNames.clear();
    }

    private List<MutableText> buildLines() {
        ArrayList<MutableText> lines = new ArrayList<>();
        lines.add(
                this.coloredText("$ ", 5635925)
                        .append(
                                this.coloredText(
                                        this.money.getValue().toUpperCase() + "  ", 16777215)));
        lines.add(
                this.coloredText("\u2605 ", 11163135)
                        .append(this.coloredText(this.shards.getValue() + "  ", 16777215)));
        lines.add(
                this.coloredText("\u2694 ", 16733525)
                        .append(this.coloredText(this.kills.getValue() + "  ", 16777215)));
        lines.add(
                this.coloredText("\u2620 ", 16755200)
                        .append(this.coloredText(this.deaths.getValue() + "  ", 16777215)));
        lines.add(
                this.coloredText("\u231a ", 16764928)
                        .append(this.coloredText(this.playtime.getValue() + "  ", 16777215)));
        return lines;
    }

    private MutableText coloredText(String text, int color) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    static {
        ICONS = new String[] {"$", "\u2605", "\ud83d\udde1", "\u2620", "\u231a"};
    }
}
