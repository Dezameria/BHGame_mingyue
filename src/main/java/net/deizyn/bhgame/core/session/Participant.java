package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;

import java.util.UUID;

/**
 * One player inside a session. {@code role} is the join order (0 = the player who clicked first) and indexes the
 * session's participant list; games give each role its own {@link InputProfile} (D31). Holds the UUID only.
 */
public final class Participant {
    private final UUID id;
    private final int role;
    private final InputProfile profile;
    private final boolean simulated;
    private int mistakes;
    private int heldKeys;
    private long rateWindowStart;
    private int rateCount;
    private long lastActionTick = Long.MIN_VALUE;
    /** Was in Epic Fight's vanilla mode before a game that forces battle mode (D66); put back at the end. */
    boolean restoreVanillaMode;
    /** Was in Epic Fight's battle mode before a game with vanilla poses forced vanilla mode (D78); put back at the end. */
    boolean restoreEpicFightMode;

    Participant(UUID id, int role, InputProfile profile, boolean simulated) {
        this.id = id;
        this.role = role;
        this.profile = profile;
        this.simulated = simulated;
    }

    public UUID id() {
        return id;
    }

    public int role() {
        return role;
    }

    /** Bit of this role in a cue's role mask. */
    public int roleBit() {
        return 1 << role;
    }

    public InputProfile profile() {
        return profile;
    }

    /** A /bhgame stress bot with no real player behind it. */
    public boolean simulated() {
        return simulated;
    }

    public int mistakes() {
        return mistakes;
    }

    void addMistake() {
        mistakes++;
    }

    /** Whether a hold key of this participant is down right now (tracked from PRESS/RELEASE edges). */
    public boolean holds(InputKey key) {
        return (heldKeys & key.bit()) != 0;
    }

    /** Every hold key down right now, as {@link InputKey#bit()}s. */
    int heldKeys() {
        return heldKeys;
    }

    void setHeld(InputKey key, boolean down) {
        heldKeys = down ? heldKeys | key.bit() : heldKeys & ~key.bit();
    }

    /** True at most once every {@code minGap} ticks; throttles the movements bystanders are sent (D41). */
    boolean allowAction(long now, int minGap) {
        if (now - lastActionTick < minGap) {
            return false;
        }
        lastActionTick = now;
        return true;
    }

    /** Sliding one-second window rate limit (docs/multiplayer-performance.md section 3). */
    boolean allowInput(long now) {
        if (now - rateWindowStart >= 20) {
            rateWindowStart = now;
            rateCount = 0;
        }
        return ++rateCount <= BhGameConfig.inputRateLimit;
    }
}
