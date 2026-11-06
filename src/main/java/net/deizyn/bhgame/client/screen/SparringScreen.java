package net.deizyn.bhgame.client.screen;

import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringCommands;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringMove;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringPattern;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringSettings;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringStats;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.C2SSparringApply;
import net.deizyn.bhgame.network.S2COpenSparring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The sparring baton's settings screen (D74; English, tabs, D75; stop below and commands, D79).
 * <ul>
 *     <li><b>Moves:</b> the default moves and the conditional patterns, one at a time; Epic Fight's animations with a
 *     search box on the right; speed and transition of the selected move.</li>
 *     <li><b>Behaviour:</b> pause, play speed, chase mode and run speed, target, stop below.</li>
 *     <li><b>Stats &amp; Gear:</b> HP, damage, armour, walk speed, invulnerable; six gear slots copied from the
 *     player's own inventory.</li>
 *     <li><b>Commands:</b> one command per event, editable only with the command permission.</li>
 * </ul>
 * Nothing changes until "Save", and the server checks it all again.
 */
public final class SparringScreen extends Screen {
    private static final int ROW = 12;
    private static final int GLYPH = 11;
    private static final int LIST_BG = 0xA0000000;
    private static final int HOVER = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xA0A0A0;
    private static final int ACCENT = 0xFFFF80;
    private static final int WARN = 0xFF7070;
    private static final int SLOT = 18;
    private static final String[] GEAR_NAMES = {"Main hand", "Off hand", "Head", "Chest", "Legs", "Feet"};

    private enum Tab {
        MOVES("Moves"), BEHAVIOUR("Behaviour"), STATS("Stats & Gear"), COMMANDS("Commands");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** One editable move set. The first one is the default moves (no condition). */
    private static final class Draft {
        @Nullable
        SparringPattern.Condition condition;
        float value;
        SparringDummy.Order order;
        float range;
        final List<SparringMove> moves;

        Draft(@Nullable SparringPattern.Condition condition, float value, SparringDummy.Order order, float range,
              List<SparringMove> moves) {
            this.condition = condition;
            this.value = value;
            this.order = order;
            this.range = range;
            this.moves = new ArrayList<>(moves);
        }

        SparringPattern toPattern() {
            return new SparringPattern(condition, value, order, range, moves);
        }
    }

    /** Copy/paste between dummies, this client only, gone when the game closes. Gear and target are not copied. */
    @Nullable
    private static SparringSettings clipboardSettings;
    @Nullable
    private static SparringStats clipboardStats;
    @Nullable
    private static SparringCommands clipboardCommands;

    private final int entityId;
    private Tab tab = Tab.MOVES;

    // moves
    private final List<Draft> drafts = new ArrayList<>();
    private int draftIndex;
    private int selectedMove = -1;

    // behaviour
    private int pauseTicks;
    private SparringDummy.ChaseMode chaseMode;
    private float runSpeed;
    private float animationSpeed;
    private float stopBelow;
    private final List<C2SSparringApply.Target> targets = new ArrayList<>();
    private final String keepName;
    private int targetIndex;

    // stats and gear
    private float maxHealth;
    private float attackDamage;
    private float armor;
    private float moveSpeed;
    private boolean invulnerable;
    private final String originalName;
    private final boolean originalNameAlways;
    private String name;
    private boolean nameAlways;
    private final ItemStack[] gear = new ItemStack[SparringStats.GEAR_SLOTS.length];
    private final byte[] gearActions = new byte[SparringStats.GEAR_SLOTS.length];
    private int pickerSlot = -1;

    // commands
    private final String[] commandLines = new String[SparringCommands.Event.values().length];
    private final boolean canEditCommands;

    // animation list
    private final List<ResourceLocation> animations;
    private List<ResourceLocation> filtered;
    private String searchText = "";
    private int movesScroll;
    private int animScroll;

    @Nullable
    private String notice;
    private boolean noticeIsWarning;

    // layout, set by init()
    private int left;
    private int titleWidth = 200;
    private int panelWidth;
    private int colWidth;
    private int rightX;
    private int listTop;
    private int animTop;
    private int listBottom;
    private final List<Label> labels = new ArrayList<>();
    @Nullable
    private EditBox search;

    private record Label(int x, int y, String text, int color) {
    }

    public SparringScreen(S2COpenSparring message) {
        super(Component.literal("Sparring Dummy #" + message.entityId()));
        originalName = message.name();
        name = originalName;
        originalNameAlways = message.nameAlwaysShown();
        nameAlways = originalNameAlways;
        entityId = message.entityId();
        loadSettings(message.settings());
        loadStats(message.stats());
        for (int i = 0; i < gear.length; i++) {
            gear[i] = i < message.gear().size() ? message.gear().get(i) : ItemStack.EMPTY;
        }
        Arrays.fill(gearActions, C2SSparringApply.GEAR_KEEP);
        canEditCommands = message.canEditCommands();
        loadCommands(message.commands());

        targets.add(C2SSparringApply.Target.NEAREST);
        targets.add(C2SSparringApply.Target.NONE);
        targets.add(C2SSparringApply.Target.ME);
        keepName = message.targetName();
        targetIndex = switch (message.targetMode()) {
            case NEAREST_PLAYER -> 0;
            case NONE -> 1;
            case FIXED -> {
                if (message.targetIsYou()) {
                    yield 2;
                }
                targets.add(C2SSparringApply.Target.KEEP);
                yield 3;
            }
        };

        animations = new ArrayList<>(EpicFightCompat.animationIds().toList());
        animations.sort(null);
        filtered = animations;
    }

    private void loadSettings(SparringSettings settings) {
        drafts.clear();
        drafts.add(new Draft(null, 0F, settings.order(), settings.attackRange(), settings.moves()));
        for (SparringPattern pattern : settings.patterns()) {
            drafts.add(new Draft(pattern.condition(), pattern.value(), pattern.order(), pattern.range(),
                    pattern.moves()));
        }
        draftIndex = 0;
        selectedMove = -1;
        movesScroll = 0;
        pauseTicks = settings.pauseTicks();
        chaseMode = settings.chaseMode();
        runSpeed = settings.runSpeed();
        animationSpeed = settings.animationSpeed();
        stopBelow = settings.stopBelow();
    }

    private void loadCommands(SparringCommands commands) {
        for (SparringCommands.Event event : SparringCommands.Event.values()) {
            commandLines[event.ordinal()] = commands.get(event);
        }
    }

    private SparringCommands commands() {
        return new SparringCommands(Arrays.asList(commandLines));
    }

    private void loadStats(SparringStats stats) {
        maxHealth = stats.maxHealth();
        attackDamage = stats.attackDamage();
        armor = stats.armor();
        moveSpeed = stats.moveSpeed();
        invulnerable = stats.invulnerable();
    }

    private SparringSettings settings() {
        Draft base = drafts.get(0);
        List<SparringPattern> patterns = new ArrayList<>();
        for (int i = 1; i < drafts.size(); i++) {
            patterns.add(drafts.get(i).toPattern());
        }
        return new SparringSettings(base.moves, base.order, base.range, patterns, pauseTicks, chaseMode, runSpeed,
                animationSpeed, stopBelow);
    }

    private SparringStats stats() {
        return new SparringStats(maxHealth, attackDamage, armor, moveSpeed, invulnerable);
    }

    private Draft draft() {
        return drafts.get(Mth.clamp(draftIndex, 0, drafts.size() - 1));
    }

    // ---- layout ----------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        labels.clear();
        search = null;
        panelWidth = Math.min(width - 16, 480);
        left = (width - panelWidth) / 2;
        colWidth = (panelWidth - 8) / 2;
        rightX = left + colWidth + 8;

        int tabWidth = Math.min(76, (panelWidth - 100) / Tab.values().length - 2);
        titleWidth = panelWidth - Tab.values().length * (tabWidth + 2) - 6;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab value = Tab.values()[i];
            Button button = Button.builder(Component.literal(value.label), b -> switchTab(value))
                    .bounds(left + panelWidth - (Tab.values().length - i) * (tabWidth + 2), 4, tabWidth, 16).build();
            button.active = value != tab;
            addRenderableWidget(button);
        }

        int quarter = (panelWidth - 12) / 4;
        int y = height - 22;
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
            clipboardSettings = settings();
            clipboardStats = stats();
            clipboardCommands = canEditCommands ? commands() : null;
            say("Copied moves, patterns, behaviour, stats" + (canEditCommands ? " and commands" : "")
                    + " (not gear or target)", false);
            rebuildWidgets();
        }).bounds(left, y, quarter, 20).build());
        Button paste = Button.builder(Component.literal("Paste"), b -> {
            if (clipboardSettings != null && clipboardStats != null) {
                loadSettings(clipboardSettings);
                loadStats(clipboardStats);
                if (canEditCommands && clipboardCommands != null) {
                    loadCommands(clipboardCommands);
                }
                say("Pasted; press Save to apply", false);
                rebuildWidgets();
            }
        }).bounds(left + quarter + 4, y, quarter, 20).build();
        paste.active = clipboardSettings != null;
        addRenderableWidget(paste);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + 2 * (quarter + 4), y, quarter, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(left + 3 * (quarter + 4), y, panelWidth - 3 * (quarter + 4), 20).build());

        switch (tab) {
            case MOVES -> initMoves();
            case BEHAVIOUR -> initBehaviour();
            case STATS -> initStats();
            case COMMANDS -> initCommands();
        }
    }

    private void switchTab(Tab value) {
        tab = value;
        pickerSlot = -1;
        notice = null;
        rebuildWidgets();
    }

    private void initMoves() {
        Draft draft = draft();
        int y = 24;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> showDraft(draftIndex - 1))
                .bounds(left, y, 20, 20).build()).active = draftIndex > 0;
        int nameWidth = 120;
        labels.add(new Label(left + 24, y + 6, draftIndex == 0 ? "Default moves"
                : "Pattern " + draftIndex + " of " + (drafts.size() - 1), TEXT));
        addRenderableWidget(Button.builder(Component.literal(">"), b -> showDraft(draftIndex + 1))
                .bounds(left + 24 + nameWidth, y, 20, 20).build()).active = draftIndex < drafts.size() - 1;
        Button add = addRenderableWidget(Button.builder(Component.literal("+ Pattern"), b -> {
            drafts.add(new Draft(SparringPattern.Condition.CHANCE, SparringPattern.Condition.CHANCE.defaultValue,
                    SparringDummy.Order.SEQUENCE, 2.5F, List.of()));
            showDraft(drafts.size() - 1);
        }).bounds(left + 48 + nameWidth, y, 70, 20).build());
        add.active = drafts.size() - 1 < SparringPattern.MAX_PATTERNS;
        addRenderableWidget(Button.builder(Component.literal("Delete pattern"), b -> {
            drafts.remove(draftIndex);
            showDraft(draftIndex - 1);
        }).bounds(left + 122 + nameWidth, y, 86, 20).build()).active = draftIndex > 0;
        addRenderableWidget(Button.builder(Component.literal("Clear moves"), b -> {
            draft().moves.clear();
            selectedMove = -1;
            movesScroll = 0;
        }).bounds(left + panelWidth - 76, y, 76, 20).build());

        y = 46;
        int x = left;
        if (draft.condition != null) {
            SparringPattern.Condition condition = draft.condition;
            addRenderableWidget(Button.builder(Component.literal("If: " + condition.label), b -> {
                SparringPattern.Condition[] all = SparringPattern.Condition.values();
                draft.condition = all[(condition.ordinal() + 1) % all.length];
                draft.value = draft.condition.defaultValue;
                rebuildWidgets();
            }).bounds(x, y, 130, 20).build());
            x += 134;
            numberBox(x, y, 40, draft.value, value -> draft.value = value);
            labels.add(new Label(x + 44, y + 6, condition.unit, MUTED));
            x += 44 + font.width(condition.unit) + 10;
        } else {
            labels.add(new Label(x, y + 6, "Used when no pattern matches", MUTED));
            x += font.width("Used when no pattern matches") + 10;
        }
        addRenderableWidget(Button.builder(orderLabel(draft.order), b -> {
            draft.order = draft.order == SparringDummy.Order.SEQUENCE
                    ? SparringDummy.Order.RANDOM : SparringDummy.Order.SEQUENCE;
            b.setMessage(orderLabel(draft.order));
        }).bounds(x, y, 90, 20).build());
        x += 96;
        labels.add(new Label(x, y + 6, "Range", MUTED));
        x += font.width("Range") + 4;
        numberBox(x, y, 36, draft.range, value -> draft.range = value);
        labels.add(new Label(x + 40, y + 6, "blocks", MUTED));

        listTop = 70;
        listBottom = height - 60;
        animTop = listTop + 16;
        search = new EditBox(font, rightX, listTop, colWidth, 14, Component.literal("Search"));
        search.setMaxLength(128);
        search.setValue(searchText);
        search.setHint(Component.literal("Search animations (Enter adds the first)").withStyle(s -> s.withColor(MUTED)));
        search.setResponder(text -> {
            searchText = text;
            refilter();
        });
        addRenderableWidget(search);
        refilter();

        y = height - 56;
        if (selectedMove >= 0 && selectedMove < draft.moves.size()) {
            SparringMove move = draft.moves.get(selectedMove);
            String name = "Move " + (selectedMove + 1) + ":";
            labels.add(new Label(left, y + 6, name, TEXT));
            x = left + font.width(name) + 6;
            labels.add(new Label(x, y + 6, "Speed x", MUTED));
            x += font.width("Speed x") + 4;
            int index = selectedMove;
            numberBox(x, y, 40, move.speed(), value -> replaceMove(index, draft.moves.get(index).withSpeed(value)));
            x += 50;
            labels.add(new Label(x, y + 6, "Transition", MUTED));
            x += font.width("Transition") + 4;
            numberBox(x, y, 40, move.transition(),
                    value -> replaceMove(index, draft.moves.get(index).withTransition(value)));
            x += 48;
            labels.add(new Label(x, y + 6, "(x Play speed)", MUTED));
        } else {
            labels.add(new Label(left, y + 6, "Click a move to set its own speed and transition", MUTED));
        }
    }

    private void showDraft(int index) {
        draftIndex = Mth.clamp(index, 0, drafts.size() - 1);
        selectedMove = -1;
        movesScroll = 0;
        rebuildWidgets();
    }

    private void replaceMove(int index, SparringMove move) {
        List<SparringMove> moves = draft().moves;
        if (index >= 0 && index < moves.size()) {
            moves.set(index, move);
        }
    }

    private void initBehaviour() {
        int y = 30;
        addRenderableWidget(new Slider(left, y, colWidth, pauseTicks / (double) SparringDummy.MAX_PAUSE,
                value -> pauseTicks = (int) Math.round(value * SparringDummy.MAX_PAUSE),
                () -> "Pause between moves: " + pauseTicks + " ticks ("
                        + String.format(Locale.ROOT, "%.2f", pauseTicks / 20F) + "s)"));
        addRenderableWidget(new Slider(rightX, y, colWidth, toUnit(animationSpeed, 0.25F, 3F),
                value -> animationSpeed = step(fromUnit(value, 0.25F, 3F), 0.05F),
                () -> "Play speed: x" + String.format(Locale.ROOT, "%.2f", animationSpeed)));
        y += 26;
        addRenderableWidget(Button.builder(chaseLabel(), b -> {
            SparringDummy.ChaseMode[] all = SparringDummy.ChaseMode.values();
            chaseMode = all[(chaseMode.ordinal() + 1) % all.length];
            b.setMessage(chaseLabel());
        }).bounds(left, y, colWidth, 20).build());
        addRenderableWidget(new Slider(rightX, y, colWidth,
                toUnit(runSpeed, SparringDummy.MIN_RUN_SPEED, SparringDummy.MAX_RUN_SPEED),
                value -> runSpeed = step(fromUnit(value, SparringDummy.MIN_RUN_SPEED, SparringDummy.MAX_RUN_SPEED),
                        0.05F),
                () -> "Run speed: x" + String.format(Locale.ROOT, "%.2f", runSpeed) + " of walk"));
        y += 26;
        addRenderableWidget(Button.builder(targetLabel(), b -> {
            targetIndex = (targetIndex + 1) % targets.size();
            b.setMessage(targetLabel());
        }).bounds(left, y, colWidth, 20).build());
        addRenderableWidget(new Slider(rightX, y, colWidth, toUnit(stopBelow, 0F, SparringDummy.MAX_STOP_BELOW),
                value -> stopBelow = step(fromUnit(value, 0F, SparringDummy.MAX_STOP_BELOW), 5F),
                () -> stopBelow <= 0F ? "Stop below: Off (fights on)"
                        : "Stop when target HP < " + Math.round(stopBelow) + "%"));

        y += 34;
        String[] help = {
                "Play speed: all moves play faster or slower (x2 = twice as fast).",
                "Each move can have its own speed too (Moves tab, click a move).",
                "Pause 0 + play speed above 1 = rapid attacks.",
                "Chase: Stay waits in place, Walk, Run, or Auto: runs while",
                "the target is more than 6 blocks away.",
                "Target: nearest player (not creative), hit back only, you,",
                "or the one set before (by command or shift + right click).",
                "Stop below: when the target's HP drops under this, the dummy",
                "stops and stands still until the target heals to full.",
        };
        for (String line : help) {
            labels.add(new Label(left, y, line, MUTED));
            y += 11;
        }
    }

    private void initStats() {
        int y = 30;
        int boxX = left + 90;
        labels.add(new Label(left, y + 6, "Name", TEXT));
        EditBox nameBox = new EditBox(font, boxX, y + 2, left + colWidth - boxX, 16, Component.literal("Name"));
        nameBox.setMaxLength(SparringDummy.MAX_NAME);
        nameBox.setValue(name);
        nameBox.setHint(Component.literal("none (&6 = gold)").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(text -> name = text);
        addRenderableWidget(nameBox);
        y += 24;
        statRow(y, "Max HP", maxHealth, value -> maxHealth = value);
        statRow(y += 24, "Attack damage", attackDamage, value -> attackDamage = value);
        statRow(y += 24, "Armor", armor, value -> armor = value);
        statRow(y += 24, "Walk speed", moveSpeed, value -> moveSpeed = value);
        y += 24;
        int half = (colWidth - 4) / 2;
        addRenderableWidget(Button.builder(invulnerableLabel(), b -> {
            invulnerable = !invulnerable;
            b.setMessage(invulnerableLabel());
        }).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(nameShownLabel(), b -> {
            nameAlways = !nameAlways;
            b.setMessage(nameShownLabel());
        }).bounds(left + half + 4, y, colWidth - half - 4, 20).build());
        y += 28;
        labels.add(new Label(left, y, "HP refills on save. Defaults:", MUTED));
        labels.add(new Label(left, y + 11, "HP 40, damage 4, armor 0, walk 0.3", MUTED));
        labels.add(new Label(left, y + 22, "Damage adds to the weapon's.", MUTED));
        labels.add(new Label(left, y + 33, "Name: & codes, e.g. &6&lMaster Wei", MUTED));

        labels.add(new Label(rightX, 30, "Gear: click to pick from your inventory,", MUTED));
        labels.add(new Label(rightX, 41, "right click to empty. Items are copied.", MUTED));
    }

    private void initCommands() {
        int y = 30;
        if (!canEditCommands) {
            labels.add(new Label(left, y, "Event commands need a higher permission level on this server.", WARN));
            labels.add(new Label(left, y + 14, "(sparring.commandPermissionLevel in the server config, default 2)",
                    MUTED));
            return;
        }
        int labelWidth = 0;
        for (SparringCommands.Event event : SparringCommands.Event.values()) {
            labelWidth = Math.max(labelWidth, font.width(event.label));
        }
        labelWidth += 8;
        for (SparringCommands.Event event : SparringCommands.Event.values()) {
            int index = event.ordinal();
            labels.add(new Label(left, y + 6, event.label, TEXT));
            EditBox box = new EditBox(font, left + labelWidth, y + 2, panelWidth - labelWidth, 16,
                    Component.literal(event.label));
            box.setMaxLength(SparringCommands.MAX_LENGTH);
            box.setValue(commandLines[index]);
            box.setHint(Component.literal("when it " + event.help).withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(text -> commandLines[index] = text);
            addRenderableWidget(box);
            y += 22;
        }
        y += 6;
        String[] help = {
                "Runs on the server as the dummy (@s, at its position) with command block permission.",
                "{target} = UUID of whoever it is about: the target, the attacker or the killer.",
                "  e.g. On target low HP:  effect give {target} minecraft:regeneration 5 2",
                "  e.g. On hurt:  scoreboard players add {target} hits 1",
                "One command per event; leave it empty for none, or use /function for several.",
        };
        for (String line : help) {
            labels.add(new Label(left, y, font.plainSubstrByWidth(line, panelWidth), MUTED));
            y += 11;
        }
    }

    private void statRow(int y, String name, float value, Consumer<Float> setter) {
        labels.add(new Label(left, y + 6, name, TEXT));
        numberBox(left + 90, y, 60, value, setter);
    }

    private void numberBox(int x, int y, int width, float value, Consumer<Float> setter) {
        EditBox box = new EditBox(font, x, y + 2, width, 16, Component.literal("number"));
        box.setMaxLength(8);
        box.setValue(SparringPattern.format(value));
        box.setFilter(text -> text.matches("-?[0-9]*\\.?[0-9]*"));
        box.setResponder(text -> {
            try {
                setter.accept(Float.parseFloat(text));
            } catch (NumberFormatException ignored) {
                // half-typed; keep the last good value
            }
        });
        addRenderableWidget(box);
    }

    private static double toUnit(float value, float min, float max) {
        return Mth.clamp((value - min) / (max - min), 0D, 1D);
    }

    private static float fromUnit(double value, float min, float max) {
        return min + (float) value * (max - min);
    }

    private static float step(float value, float step) {
        return Math.round(value / step) * step;
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    private void save() {
        BhGameNetwork.sendToServer(new C2SSparringApply(entityId, settings(), stats(), gearActions.clone(),
                targets.get(targetIndex), canEditCommands ? commands() : null,
                name.equals(originalName) && nameAlways == originalNameAlways ? null : name, nameAlways));
        onClose();
    }

    private void addMove(ResourceLocation animation) {
        List<SparringMove> moves = draft().moves;
        if (moves.size() >= SparringDummy.MAX_MOVES) {
            say("At most " + SparringDummy.MAX_MOVES + " moves per list", true);
            return;
        }
        moves.add(new SparringMove(animation, 0F));
        movesScroll = Math.max(0, moves.size() - visibleRows(listTop));
        notice = null;
    }

    private void say(String text, boolean warning) {
        notice = text;
        noticeIsWarning = warning;
    }

    private void refilter() {
        String[] words = searchText.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : animations) {
            String text = id.toString();
            boolean all = true;
            for (String word : words) {
                if (!word.isEmpty() && !text.contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                result.add(id);
            }
        }
        filtered = result;
        animScroll = 0;
    }

    private int visibleRows(int top) {
        return Math.max(1, (listBottom - top) / ROW);
    }

    // ---- labels ----------------------------------------------------------------------------------------------------

    private static Component orderLabel(SparringDummy.Order order) {
        return Component.literal("Order: " + (order == SparringDummy.Order.SEQUENCE ? "In order" : "Random"));
    }

    private Component chaseLabel() {
        return Component.literal("Chase: " + switch (chaseMode) {
            case STAY -> "Stay in place";
            case WALK -> "Walk";
            case RUN -> "Run";
            case AUTO -> "Auto (run when far)";
        });
    }

    private Component targetLabel() {
        return Component.literal("Target: " + switch (targets.get(targetIndex)) {
            case NEAREST -> "Nearest player";
            case NONE -> "Hit back only";
            case ME -> "Me";
            case KEEP -> keepName;
        });
    }

    /** The name being typed, in its colours, or "Sparring Dummy #id" while there is none. */
    private Component headline() {
        String clean = SparringDummy.cleanName(name);
        if (clean.isEmpty()) {
            return title;
        }
        return Component.literal(clean.replaceAll("(?i)&([0-9a-fk-or])", "\u00a7$1"))
                .append(Component.literal(" #" + entityId).withStyle(ChatFormatting.GRAY));
    }

    private Component nameShownLabel() {
        return Component.literal("Name: " + (nameAlways ? "Always" : "On look"));
    }

    private Component invulnerableLabel() {
        return Component.literal("Invulnerable: " + (invulnerable ? "On" : "Off"));
    }

    /** Epic Fight's own ids are shown without the namespace; long ones keep their end, which tells them apart. */
    private String display(ResourceLocation id, int maxWidth) {
        String text = id.getNamespace().equals("epicfight") ? id.getPath() : id.toString();
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "…";
        int start = 0;
        while (start < text.length() && font.width(dots + text.substring(start)) > maxWidth) {
            start++;
        }
        return dots + text.substring(start);
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(headline(), titleWidth)),
                left, 8, TEXT);
        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), label.color());
        }
        boolean picker = pickerSlot >= 0;
        int mx = picker ? -1 : mouseX;
        int my = picker ? -1 : mouseY;
        Component tooltip = null;
        ItemStack itemTooltip = ItemStack.EMPTY;
        if (tab == Tab.MOVES) {
            graphics.fill(left, listTop, left + colWidth, listBottom, LIST_BG);
            graphics.fill(rightX, animTop, rightX + colWidth, listBottom, LIST_BG);
            tooltip = renderMoves(graphics, mx, my);
            Component animTooltip = renderAnimations(graphics, mx, my);
            if (tooltip == null) {
                tooltip = animTooltip;
            }
        } else if (tab == Tab.STATS) {
            itemTooltip = renderGear(graphics, mx, my);
        }

        String footer = notice != null ? notice : switch (tab) {
            case MOVES -> filtered.size() + " animations | patterns are checked in order before the default moves";
            case BEHAVIOUR -> "Changes apply when you press Save";
            case STATS -> "Gear is copied from your inventory; the dummy never drops it";
            case COMMANDS -> "Commands are saved with Save and run on the server";
        };
        graphics.drawString(font, font.plainSubstrByWidth(footer, panelWidth), left, height - 34,
                notice != null && noticeIsWarning ? WARN : MUTED);

        super.render(graphics, mx, my, partialTick);
        if (picker) {
            renderPicker(graphics, mouseX, mouseY);
        } else if (tooltip != null) {
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        } else if (!itemTooltip.isEmpty()) {
            graphics.renderTooltip(font, itemTooltip, mouseX, mouseY);
        }
    }

    @Nullable
    private Component renderMoves(GuiGraphics graphics, int mouseX, int mouseY) {
        List<SparringMove> moves = draft().moves;
        int rows = visibleRows(listTop);
        if (moves.isEmpty()) {
            graphics.drawString(font, draftIndex == 0 ? "(empty: Epic Fight's 3 fist attacks)"
                    : "(empty: this pattern is skipped)", left + 4, listTop + 3, MUTED);
            graphics.drawString(font, "Click an animation on the right to add it", left + 4, listTop + 3 + ROW,
                    MUTED);
            return null;
        }
        movesScroll = Mth.clamp(movesScroll, 0, Math.max(0, moves.size() - rows));
        Component tooltip = null;
        int textWidth = colWidth - 3 * GLYPH - 8;
        for (int row = 0; row < rows; row++) {
            int index = movesScroll + row;
            if (index >= moves.size()) {
                break;
            }
            SparringMove move = moves.get(index);
            int y = listTop + row * ROW;
            boolean hover = mouseY >= y && mouseY < y + ROW && mouseX >= left && mouseX < left + colWidth;
            if (index == selectedMove) {
                graphics.fill(left, y, left + colWidth, y + ROW, 0x60406080);
            } else if (hover) {
                graphics.fill(left, y, left + colWidth, y + ROW, HOVER);
            }
            String number = (index + 1) + ". ";
            graphics.drawString(font, number, left + 3, y + 2, MUTED);
            int numberWidth = font.width(number);
            String speed = move.speed() != 1F ? " x" + SparringPattern.format(move.speed()) : "";
            int speedWidth = font.width(speed);
            graphics.drawString(font, display(move.animation(), textWidth - numberWidth - speedWidth),
                    left + 3 + numberWidth, y + 2, TEXT);
            if (!speed.isEmpty()) {
                graphics.drawString(font, speed, left + 3 + textWidth - speedWidth, y + 2, ACCENT);
            }
            int glyphX = left + colWidth - 3 * GLYPH - 2;
            String[] glyphs = {"▲", "▼", "✕"};
            for (int i = 0; i < glyphs.length; i++) {
                int x = glyphX + i * GLYPH;
                boolean over = hover && mouseX >= x && mouseX < x + GLYPH;
                graphics.drawCenteredString(font, glyphs[i], x + GLYPH / 2 + 1, y + 2,
                        over ? (i == 2 ? WARN : ACCENT) : MUTED);
            }
            if (hover && mouseX < glyphX) {
                tooltip = Component.literal(move.toString());
            }
        }
        drawScrollbar(graphics, left + colWidth - 1, listTop, rows, movesScroll, moves.size());
        return tooltip;
    }

    @Nullable
    private Component renderAnimations(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = visibleRows(animTop);
        if (filtered.isEmpty()) {
            String empty = animations.isEmpty() ? "No animation list (no Epic Fight?)" : "No match";
            graphics.drawString(font, empty, rightX + 4, animTop + 3, MUTED);
            graphics.drawString(font, "Type a full id and press Enter", rightX + 4, animTop + 3 + ROW, MUTED);
            return null;
        }
        animScroll = Mth.clamp(animScroll, 0, Math.max(0, filtered.size() - rows));
        Component tooltip = null;
        for (int row = 0; row < rows; row++) {
            int index = animScroll + row;
            if (index >= filtered.size()) {
                break;
            }
            ResourceLocation id = filtered.get(index);
            int y = animTop + row * ROW;
            boolean hover = mouseY >= y && mouseY < y + ROW && mouseX >= rightX && mouseX < rightX + colWidth;
            if (hover) {
                graphics.fill(rightX, y, rightX + colWidth, y + ROW, HOVER);
                tooltip = Component.literal(id + "  (click to add)");
            }
            graphics.drawString(font, display(id, colWidth - 8), rightX + 3, y + 2, hover ? ACCENT : TEXT);
        }
        drawScrollbar(graphics, rightX + colWidth - 1, animTop, rows, animScroll, filtered.size());
        return tooltip;
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int top, int rows, int scroll, int total) {
        if (total <= rows) {
            return;
        }
        int height = rows * ROW;
        int barHeight = Math.max(8, height * rows / total);
        int barY = top + (height - barHeight) * scroll / (total - rows);
        graphics.fill(x, barY, x + 1, barY + barHeight, 0xFFC0C0C0);
    }

    private int gearY(int slot) {
        return 56 + slot * (SLOT + 6);
    }

    private ItemStack renderGear(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack tooltip = ItemStack.EMPTY;
        for (int i = 0; i < gear.length; i++) {
            int x = rightX;
            int y = gearY(i);
            boolean hover = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
            graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF8B8B8B);
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hover ? 0xFFC5C5C5 : 0xFF373737);
            graphics.renderItem(gear[i], x + 1, y + 1);
            String state = switch (gearActions[i]) {
                case C2SSparringApply.GEAR_KEEP -> "";
                case C2SSparringApply.GEAR_CLEAR -> " (will empty)";
                default -> " (will copy)";
            };
            graphics.drawString(font, GEAR_NAMES[i] + state, x + SLOT + 6, y + 2, TEXT);
            String name = gear[i].isEmpty() ? "empty" : gear[i].getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(name, colWidth - SLOT - 8), x + SLOT + 6, y + 11,
                    MUTED);
            if (hover && !gear[i].isEmpty()) {
                tooltip = gear[i];
            }
        }
        return tooltip;
    }

    /** The player's inventory as the picker shows it: main rows, hotbar, then armour and offhand. */
    private static final int PICKER_COLUMNS = 9;

    private int[] pickerOrder() {
        int[] order = new int[41];
        int n = 0;
        for (int i = 9; i < 36; i++) {
            order[n++] = i;
        }
        for (int i = 0; i < 9; i++) {
            order[n++] = i;
        }
        for (int i = 39; i >= 36; i--) {
            order[n++] = i; // head first
        }
        order[n] = Inventory.SLOT_OFFHAND;
        return order;
    }

    private int pickerX() {
        return (width - PICKER_COLUMNS * SLOT) / 2;
    }

    private int pickerY() {
        return (height - 5 * SLOT - 6) / 2;
    }

    private int pickerCell(int cell, boolean xAxis) {
        int row = cell / PICKER_COLUMNS;
        int column = cell % PICKER_COLUMNS;
        int gap = row >= 4 ? 6 : row == 3 ? 4 : 0;
        return xAxis ? pickerX() + column * SLOT : pickerY() + row * SLOT + gap;
    }

    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        Inventory inventory = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory()
                : null;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        int x0 = pickerX() - 8;
        int y0 = pickerY() - 20;
        graphics.fill(0, 0, width, height, 0x80000000);
        graphics.fill(x0, y0, x0 + PICKER_COLUMNS * SLOT + 16, pickerY() + 5 * SLOT + 18, 0xF0202020);
        graphics.drawString(font, "Pick an item for " + GEAR_NAMES[pickerSlot] + " (Esc: cancel)", x0 + 4, y0 + 6,
                TEXT);
        ItemStack hovered = ItemStack.EMPTY;
        if (inventory != null) {
            int[] order = pickerOrder();
            for (int cell = 0; cell < order.length; cell++) {
                int x = pickerCell(cell, true);
                int y = pickerCell(cell, false);
                boolean hover = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
                graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF8B8B8B);
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hover ? 0xFFC5C5C5 : 0xFF373737);
                ItemStack stack = inventory.getItem(order[cell]);
                graphics.renderItem(stack, x + 1, y + 1);
                if (hover) {
                    hovered = stack;
                }
            }
        }
        graphics.pose().popPose();
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pickerSlot >= 0) {
            pickFromInventory(mouseX, mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (tab == Tab.STATS) {
            return clickGear(mouseX, mouseY, button);
        }
        if (tab != Tab.MOVES || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (mouseX >= left && mouseX < left + colWidth && mouseY >= listTop && mouseY < listBottom) {
            return clickMove(mouseX, (int) ((mouseY - listTop) / ROW));
        }
        if (mouseX >= rightX && mouseX < rightX + colWidth && mouseY >= animTop && mouseY < listBottom) {
            int index = animScroll + (int) ((mouseY - animTop) / ROW);
            if (index < filtered.size()) {
                addMove(filtered.get(index));
                return true;
            }
        }
        return false;
    }

    private boolean clickMove(double mouseX, int row) {
        List<SparringMove> moves = draft().moves;
        int index = movesScroll + row;
        if (index >= moves.size()) {
            return false;
        }
        int glyph = (int) Math.floor((mouseX - (left + colWidth - 3 * GLYPH - 2)) / GLYPH);
        if (glyph == 0 && index > 0) {
            moves.add(index - 1, moves.remove(index));
            selectedMove = index - 1;
        } else if (glyph == 1 && index < moves.size() - 1) {
            moves.add(index + 1, moves.remove(index));
            selectedMove = index + 1;
        } else if (glyph == 2) {
            moves.remove(index);
            selectedMove = -1;
        } else if (glyph < 0) {
            selectedMove = selectedMove == index ? -1 : index;
        } else {
            return true;
        }
        rebuildWidgets(); // the move editor row follows the selection
        return true;
    }

    private boolean clickGear(double mouseX, double mouseY, int button) {
        for (int i = 0; i < gear.length; i++) {
            int y = gearY(i);
            if (mouseX >= rightX && mouseX < rightX + SLOT && mouseY >= y && mouseY < y + SLOT) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    gear[i] = ItemStack.EMPTY;
                    gearActions[i] = C2SSparringApply.GEAR_CLEAR;
                } else {
                    pickerSlot = i;
                }
                return true;
            }
        }
        return false;
    }

    private void pickFromInventory(double mouseX, double mouseY) {
        Inventory inventory = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory()
                : null;
        int[] order = pickerOrder();
        for (int cell = 0; cell < order.length && inventory != null; cell++) {
            int x = pickerCell(cell, true);
            int y = pickerCell(cell, false);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                ItemStack stack = inventory.getItem(order[cell]);
                if (stack.isEmpty()) {
                    return; // stay open: pick something, or Esc
                }
                gear[pickerSlot] = stack.copyWithCount(1);
                gearActions[pickerSlot] = (byte) order[cell];
                break;
            }
        }
        pickerSlot = -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.MOVES && pickerSlot < 0) {
            int step = delta > 0 ? -1 : 1;
            if (mouseX >= left && mouseX < left + colWidth) {
                movesScroll += step;
                return true;
            }
            if (mouseX >= rightX && mouseX < rightX + colWidth) {
                animScroll += step;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerSlot >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pickerSlot = -1;
            }
            return true;
        }
        if (search != null && search.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (!filtered.isEmpty()) {
                addMove(filtered.get(0));
            } else {
                ResourceLocation typed = ResourceLocation.tryParse(searchText.trim());
                if (typed != null && !searchText.isBlank()) {
                    addMove(typed); // the server drops it if Epic Fight doesn't know it
                } else {
                    say("Not a valid animation id", true);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- slider ----------------------------------------------------------------------------------------------------

    private static final class Slider extends AbstractSliderButton {
        private final Consumer<Double> apply;
        private final java.util.function.Supplier<String> label;

        Slider(int x, int y, int width, double value, Consumer<Double> apply,
               java.util.function.Supplier<String> label) {
            super(x, y, width, 20, Component.empty(), value);
            this.apply = apply;
            this.label = label;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label.get()));
        }

        @Override
        protected void applyValue() {
            apply.accept(value);
        }
    }
}
