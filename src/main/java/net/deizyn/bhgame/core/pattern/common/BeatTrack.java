package net.deizyn.bhgame.core.pattern.common;

import net.deizyn.bhgame.core.Tuning;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.session.Participant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The timing judge for window cues (TAP and FEINT), shared by every rhythm-style game. The owning pattern decides
 * what beats to {@link #add}; this class sends them ahead in batches, judges presses and expires missed windows.
 *
 * <p>Rules (docs/design/core-mechanics.md section 7):
 * <ul>
 *   <li>TAP, {@code roles == 0}: any participant pressing a matching key inside the window is one hit.</li>
 *   <li>TAP with a role mask: every role in the mask must press; the hit counts when the last one does. A role that
 *       never pressed by the end of the window gets a miss.</li>
 *   <li>A key not in the beat's keys, or any press on a FEINT, is one miss for that role per beat.</li>
 *   <li>With {@code punishGaps}, a press before your next beat opens is one miss per gap.</li>
 *   <li>Repeat presses inside a window that already counted are ignored, not punished.</li>
 * </ul>
 * Windows close {@link PatternContext#graceTicks} later for high-ping players.
 */
public final class BeatTrack {

    /** Lets a game react to results, e.g. a penalty that depends on which key was wrong. */
    public interface Listener {
        /**
         * @param beat    the beat that was missed, or null for a press while the player's side was dim
         * @param pressed the wrong key pressed, or null when the window passed without a press
         */
        default void onMiss(PatternContext ctx, Participant who, @Nullable Beat beat, @Nullable InputKey pressed) {
            ctx.miss(who, beat == null ? -1 : beat.id);
        }

        default void onHit(PatternContext ctx, Beat beat) {
            ctx.hit(beat.id);
        }
    }

    private static final Listener DEFAULT_LISTENER = new Listener() {
    };

    public static final class Beat {
        private int id;
        private CueType type;
        private int keys;
        private int roles;
        private CueVisual visual;
        private long open;
        private long close;
        private boolean sent;
        private int pressedRoles;
        private int punishedRoles;
        private boolean hit;

        public int id() {
            return id;
        }

        public int keys() {
            return keys;
        }

        public CueVisual visual() {
            return visual;
        }

        public long open() {
            return open;
        }

        public long close() {
            return close;
        }

        public int roles() {
            return roles;
        }

        public CueType type() {
            return type;
        }
    }

    private final int sendAheadTicks;
    private final boolean punishGaps;
    private final Listener listener;
    private final ArrayDeque<Beat> beats = new ArrayDeque<>();
    private final List<CueData> outgoing = new ArrayList<>();
    /** Per role: id of the beat whose gap was already punished. */
    private final int[] gapPunishedBefore = new int[Tuning.MAX_PARTICIPANTS];
    private long lastClose;

    /**
     * @param sendAheadTicks beats that open within this many ticks are sent (never less than the leadTicks config)
     * @param punishGaps     whether pressing while your side is dim is a miss
     * @param firstOpenAfter beats start after this tick (normally the end of FOCUS)
     */
    public BeatTrack(int sendAheadTicks, boolean punishGaps, long firstOpenAfter) {
        this(sendAheadTicks, punishGaps, firstOpenAfter, DEFAULT_LISTENER);
    }

    public BeatTrack(int sendAheadTicks, boolean punishGaps, long firstOpenAfter, Listener listener) {
        this.sendAheadTicks = sendAheadTicks;
        this.punishGaps = punishGaps;
        this.listener = listener;
        this.lastClose = firstOpenAfter;
        Arrays.fill(gapPunishedBefore, -1);
    }

    /** Beats queued and not yet expired. Patterns keep a few ahead so batches stay full. */
    public int queued() {
        return beats.size();
    }

    /** Close tick of the last queued beat; the next beat usually opens a gap after it. */
    public long lastClose() {
        return lastClose;
    }

    /** Queues a beat that opens {@code gap} ticks after the previous one closes. */
    public Beat addAfter(PatternContext ctx, int gap, CueType type, int keys, int roles, CueVisual visual, int window) {
        return add(ctx, lastClose + gap, type, keys, roles, visual, window);
    }

    /** Queues a beat at an absolute tick. Beats must be added in opening order. */
    public Beat add(PatternContext ctx, long open, CueType type, int keys, int roles, CueVisual visual, int window) {
        Beat beat = new Beat();
        beat.id = ctx.newCueId();
        beat.type = type;
        beat.keys = keys;
        beat.roles = roles;
        beat.visual = visual;
        beat.open = open;
        beat.close = open + window;
        lastClose = Math.max(lastClose, beat.close);
        beats.addLast(beat);
        return beat;
    }

    /** Expires passed windows, sends the next batch and schedules the next event. Call from {@code Pattern.tick}. */
    public void tick(PatternContext ctx, long now) {
        int grace = ctx.maxGraceTicks();
        while (!beats.isEmpty()) {
            Beat head = beats.peekFirst();
            if (now < head.close + grace) {
                break;
            }
            beats.pollFirst();
            expire(ctx, head);
            if (ctx.isFinished()) {
                return;
            }
        }
    }

    /** Sends unsent beats that are close enough and schedules this track's next event. */
    public void flush(PatternContext ctx, long now) {
        int ahead = Math.max(sendAheadTicks, BhGameConfig.leadTicks);
        outgoing.clear();
        for (Beat beat : beats) {
            if (!beat.sent && beat.open - now <= ahead) {
                beat.sent = true;
                outgoing.add(new CueData(beat.id, beat.type, CueChannel.BLOCK, beat.keys, beat.roles,
                        (int) (beat.open - now), (int) (beat.close - beat.open), beat.visual, -1));
            }
        }
        if (!outgoing.isEmpty()) {
            ctx.sendCues(List.copyOf(outgoing));
        }
        Beat head = beats.peekFirst();
        if (head != null) {
            ctx.scheduleAt(head.close + ctx.maxGraceTicks());
        }
        for (Beat beat : beats) {
            if (!beat.sent) {
                ctx.scheduleAt(beat.open - ahead);
                break;
            }
        }
    }

    public void onPress(PatternContext ctx, Participant who, InputKey key, long now) {
        int grace = ctx.graceTicks(who);
        int bit = who.roleBit();
        for (Beat beat : beats) {
            if (!beat.sent || now >= beat.close + grace || !concerns(beat, bit)) {
                continue;
            }
            if (now < beat.open) {
                // your side is dim: one miss per gap however many presses
                if (punishGaps && gapPunishedBefore[who.role()] != beat.id) {
                    gapPunishedBefore[who.role()] = beat.id;
                    listener.onMiss(ctx, who, null, key);
                }
                return;
            }
            if (beat.type == CueType.FEINT || (beat.keys & key.bit()) == 0) {
                if ((beat.punishedRoles & bit) == 0) {
                    beat.punishedRoles |= bit;
                    listener.onMiss(ctx, who, beat, key);
                }
                return;
            }
            if (beat.hit || (beat.pressedRoles & bit) != 0) {
                return;
            }
            beat.pressedRoles |= bit;
            if (beat.roles == 0 || (beat.pressedRoles & beat.roles) == beat.roles) {
                beat.hit = true;
                listener.onHit(ctx, beat);
            }
            return;
        }
    }

    private void expire(PatternContext ctx, Beat beat) {
        if (beat.type != CueType.TAP || beat.hit) {
            return;
        }
        List<Participant> participants = ctx.participants();
        if (beat.roles == 0) {
            if ((beat.punishedRoles & 1) == 0) {
                listener.onMiss(ctx, participants.get(0), beat, null);
            }
            return;
        }
        for (Participant participant : participants) {
            int bit = participant.roleBit();
            // a role that pressed (or was already punished on this beat) is not blamed again
            if ((beat.roles & bit) != 0 && ((beat.pressedRoles | beat.punishedRoles) & bit) == 0) {
                listener.onMiss(ctx, participant, beat, null);
                if (ctx.isFinished()) {
                    return;
                }
            }
        }
    }

    private static boolean concerns(Beat beat, int roleBit) {
        return beat.roles == 0 || (beat.roles & roleBit) != 0;
    }
}
