package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One command per event that the dummy runs itself (D79), like a command block: as the dummy ({@code @s}), at its
 * position, permission level 2, output hidden. {@code {target}} becomes the UUID of whoever the event is about (the
 * target, the attacker or the killer); a command that needs it is skipped when there is nobody.
 * <p>
 * Setting them needs {@code sparring.commandPermissionLevel} (2 or more), on the baton screen and by command.
 */
public record SparringCommands(List<String> lines) {
    public static final int MAX_LENGTH = 512;
    public static final String TARGET = "{target}";
    public static final SparringCommands NONE = new SparringCommands(List.of());

    public enum Event {
        ENGAGE("On engage", "starts going after a target"),
        TARGET_LOW("On target low HP", "stops because the target's HP fell under \"Stop below\""),
        MOVE("On each move", "starts a move"),
        HURT("On hurt", "gets hit (also while invulnerable)"),
        DEATH("On death", "dies");

        public final String label;
        public final String help;

        Event(String label, String help) {
            this.label = label;
            this.help = help;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public SparringCommands {
        List<String> clean = new ArrayList<>(Collections.nCopies(Event.values().length, ""));
        for (int i = 0; i < clean.size() && i < lines.size(); i++) {
            clean.set(i, clean(lines.get(i)));
        }
        lines = List.copyOf(clean);
    }

    /** One line, no leading slash or spaces, at most {@link #MAX_LENGTH}. */
    public static String clean(String line) {
        if (line == null) {
            return "";
        }
        String text = line.replace('\n', ' ').replace('\r', ' ').strip();
        while (text.startsWith("/")) {
            text = text.substring(1).stripLeading();
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }

    public String get(Event event) {
        return lines.get(event.ordinal());
    }

    public SparringCommands with(Event event, String line) {
        List<String> copy = new ArrayList<>(lines);
        copy.set(event.ordinal(), line);
        return new SparringCommands(copy);
    }

    public boolean isEmpty() {
        return lines.stream().allMatch(String::isEmpty);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Event event : Event.values()) {
            if (!get(event).isEmpty()) {
                tag.putString(event.key(), get(event));
            }
        }
        return tag;
    }

    public static SparringCommands load(CompoundTag tag) {
        List<String> lines = new ArrayList<>();
        for (Event event : Event.values()) {
            lines.add(tag.getString(event.key()));
        }
        return new SparringCommands(lines);
    }

    public void encode(FriendlyByteBuf buf) {
        for (String line : lines) {
            buf.writeUtf(line, MAX_LENGTH);
        }
    }

    public static SparringCommands decode(FriendlyByteBuf buf) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Event.values().length; i++) {
            lines.add(buf.readUtf(MAX_LENGTH));
        }
        return new SparringCommands(lines);
    }
}
