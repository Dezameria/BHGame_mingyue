package net.deizyn.bhgame.core.input;

/** The only inputs a minigame may use (docs/design/core-mechanics.md section 2). */
public enum InputKey {
    LMB, RMB, SPACE, UP, LEFT, DOWN, RIGHT;

    private static final InputKey[] VALUES = values();

    public int bit() {
        return 1 << ordinal();
    }

    /** The arrow pointing the other way (a reversed arrow in a SEQUENCE); other keys map to themselves. */
    public InputKey opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            default -> this;
        };
    }

    public boolean isArrow() {
        return this == UP || this == LEFT || this == DOWN || this == RIGHT;
    }

    public static int mask(InputKey... keys) {
        int mask = 0;
        for (InputKey key : keys) {
            mask |= key.bit();
        }
        return mask;
    }

    public static InputKey byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
