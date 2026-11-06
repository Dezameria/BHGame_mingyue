package net.deizyn.bhgame.game.station.qi_station;

import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pattern.Phases;
import net.deizyn.bhgame.core.pattern.common.BeatTrack;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.session.Participant;
import org.jetbrains.annotations.Nullable;

/**
 * Two players push one orb at each other (D58). The {@code tug} gauge is where the orb hangs: 0 = against role 0,
 * 1 = against role 1; whoever it reaches loses. Holding LMB pushes (both holding = stalemate). RMB sends a burst at
 * the opponent, who sees the orb gather on their side and must counter with SPACE inside the window: a counter
 * throws it back a little, a miss (or SPACE too early) drives the orb hard at them. SPACE with nothing coming costs a
 * little. Shift + RMB sends a feint instead, on the same cooldown, that turns purple only near the end of the gather;
 * countering it costs ground. Holding Shift draws the orb back toward yourself (D76): alone it only loses ground, but
 * once it takes hold ({@link #GRIP_TICKS}) a push from the other side is turned back on the pusher; both drawing is a
 * stalemate, and drawing beats a push from the same player. A counter needs open palms: SPACE does nothing while LMB
 * or Shift is held or for {@link #STANCE_TICKS} after letting go (D59). The orb (a public gauge) and each burst's {@link QiFx} events are
 * shown to everyone nearby (D60). After a minute the orb turns frenzied and bursts at random sides by itself, sometimes as a feint, with
 * windows that keep shrinking, so a duel always ends.
 * Numbers and rules: docs/design/minigames/qi_station.md
 */
final class QiStationPattern implements Pattern, BeatTrack.Listener {
    static final int TUG = 0;
    static final float PUSH_RATE = 1.0F / 200.0F;
    static final float REFLECT = 0.1F;
    static final float STRUCK = 0.25F;
    static final float ORB_STRUCK = 0.15F;
    static final float FEINT_LOSS = 0.15F;
    static final float WHIFF_LOSS = 0.05F;
    static final int[] CHARGE = {18, 16, 13};
    static final int[] WINDOW = {10, 8, 6};
    static final int MIN_WINDOW = 4;
    static final int STRIKE_COOLDOWN = 30;
    /** After letting go of LMB, the palms need this long before SPACE counts (D59). */
    static final int STANCE_TICKS = 4;
    /** Drawing the orb back alone: it drifts toward whoever draws (D76). */
    static final float PULL_RATE = 0.7F / 200.0F;
    /** A drawing that has taken hold meets a push: the orb goes at the pusher this fast. */
    static final float YIELD_RATE = 2.0F / 200.0F;
    /** Shift held this long before the draw takes hold; time enough for the pusher to see it and let go. */
    static final int GRIP_TICKS = 10;
    static final int FRENZY_AFTER = 1200;
    /** In the frenzy the window loses a tick this often. */
    static final int FRENZY_STEP = 400;
    static final float ORB_FEINT_CHANCE = 0.25F;
    private static final int SEND_AHEAD_TICKS = 60;
    /** A whiff puff for bystanders at most this often per player; the gauge itself is never held back. */
    private static final int WHIFF_FX_GAP = 10;
    /** The same for the yield flourish, which a pusher could otherwise set off by tapping LMB. */
    private static final int YIELD_FX_GAP = 10;
    private static final int ORB = -1;

    private final BeatTrack track;
    private final int charge;
    private final int baseWindow;
    private final long frenzyStart;
    private final boolean[] pushing = new boolean[2];
    private final boolean[] drawing = new boolean[2];
    /** Per role: when Shift went down, and whether that draw has taken hold yet. */
    private final long[] drawSince = new long[2];
    private final boolean[] gripped = new boolean[2];
    /** The role whose draw is turning the other's push back right now, or -1. */
    private int yielding = -1;
    private final long[] yieldFxAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    /** Per role: the tick LMB or Shift was last let go. */
    private final long[] releasedAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    /** Per attacker: first tick they may strike again. */
    private final long[] strikeReady = new long[2];
    /** Per target: first tick a new burst may be aimed at them (the last one is judged and gone). */
    private final long[] freeAt = new long[2];
    /** Per target: the burst or feint still waiting for its verdict, and who sent it (role, or ORB). */
    private final BeatTrack.Beat[] incoming = new BeatTrack.Beat[2];
    private final int[] sender = new int[2];
    private final long[] whiffFxAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private boolean frenzy;
    private long nextOrbStrike;
    /** The tick of the call in progress, for listener callbacks that move the gauge. */
    private long now;

    QiStationPattern(PatternContext ctx) {
        this.track = new BeatTrack(SEND_AHEAD_TICKS, true, ctx.focusEndTick(), this);
        this.charge = Phases.byDifficulty(ctx, CHARGE);
        this.baseWindow = Phases.byDifficulty(ctx, WINDOW);
        this.frenzyStart = ctx.focusEndTick() + FRENZY_AFTER;
        tug(ctx).set(ctx.focusEndTick(), 0.5F, 0.0F);
    }

    @Override
    public void tick(PatternContext ctx, long now) {
        this.now = now;
        Gauge tug = tug(ctx);
        if (checkEnd(ctx, tug)) {
            return;
        }
        updateForces(ctx);
        if (ctx.isFinished()) {
            return;
        }
        track.tick(ctx, now);
        if (ctx.isFinished()) {
            return;
        }
        fizzleFeints(ctx);
        if (!frenzy && now >= frenzyStart) {
            frenzy = true;
            nextOrbStrike = now;
            ctx.banner("bhgame.qi_station.frenzy", 0);
        }
        if (frenzy && now >= nextOrbStrike) {
            orbStrike(ctx);
        }
        track.flush(ctx, now);
        ctx.scheduleAt(frenzy ? nextOrbStrike : frenzyStart);
        scheduleEnd(ctx, tug);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        this.now = now;
        int role = who.role();
        if (role > 1) {
            return;
        }
        if (key == InputKey.LMB || key == InputKey.SNEAK) {
            if (key == InputKey.LMB) {
                pushing[role] = press;
            } else {
                drawing[role] = press;
                gripped[role] = false;
                drawSince[role] = now;
            }
            if (!press) {
                releasedAt[role] = now;
            }
            updateForces(ctx);
        } else if (!press) {
            return;
        } else if (key == InputKey.RMB) {
            int target = role ^ 1;
            if (now >= strikeReady[role] && now >= freeAt[target]) {
                launch(ctx, role, target, drawing[role]);
                strikeReady[role] = freeAt[target] + STRIKE_COOLDOWN;
                ctx.action(who, PlayerAction.PUNCH_FINISH, null);
                track.flush(ctx, now);
            }
        } else if (key == InputKey.SPACE) {
            if (pushing[role] || drawing[role] || now < releasedAt[role] + STANCE_TICKS) {
                // palms still on the orb: no counter, no whiff either
                return;
            }
            if (now < freeAt[role]) {
                track.onPress(ctx, who, key, now);
            } else {
                // countering thin air: the stance opens for a moment
                shift(ctx, role, WHIFF_LOSS);
                if (!ctx.isFinished() && now >= whiffFxAt[role] + WHIFF_FX_GAP) {
                    whiffFxAt[role] = now;
                    ctx.showNearby(QiFx.WHIFF, role, 0);
                }
            }
        }
    }

    /**
     * Sets the orb's drift from both players' palms (D76): a push sends it at the other side, a draw (Shift, which wins
     * over that player's own push) brings it back, and a draw that has taken hold turns a push back on the pusher.
     * Fires GRIP when a draw takes hold and YIELD when one starts turning a push, and wakes the session for the next grip.
     */
    private void updateForces(PatternContext ctx) {
        for (int role = 0; role < 2; role++) {
            if (!drawing[role] || gripped[role]) {
                continue;
            }
            if (now >= drawSince[role] + GRIP_TICKS) {
                gripped[role] = true;
                ctx.showNearby(QiFx.GRIP, role, 0);
            } else {
                ctx.scheduleAt(drawSince[role] + GRIP_TICKS);
            }
        }
        boolean push0 = pushing[0] && !drawing[0];
        boolean push1 = pushing[1] && !drawing[1];
        int turning = gripped[0] && push1 ? 0 : gripped[1] && push0 ? 1 : -1;
        if (turning != yielding) {
            yielding = turning;
            if (turning >= 0 && now >= yieldFxAt[turning] + YIELD_FX_GAP) {
                yieldFxAt[turning] = now;
                ctx.showNearby(QiFx.YIELD, turning, turning ^ 1);
            }
        }
        float rate;
        if (turning >= 0) {
            // the push goes back the way it came, harder
            rate = turning == 0 ? YIELD_RATE : -YIELD_RATE;
        } else {
            rate = PUSH_RATE * ((push0 ? 1 : 0) - (push1 ? 1 : 0))
                    + PULL_RATE * ((drawing[1] ? 1 : 0) - (drawing[0] ? 1 : 0));
        }
        Gauge tug = tug(ctx);
        if (rate != tug.ratePerTick()) {
            tug.setRate(now, rate);
        }
        if (!checkEnd(ctx, tug)) {
            scheduleEnd(ctx, tug);
        }
    }

    /** A counter in the window: the burst goes back where it came from (the frenzied orb just fizzles). */
    @Override
    public void onHit(PatternContext ctx, BeatTrack.Beat beat) {
        int target = Integer.numberOfTrailingZeros(beat.roles());
        if (target > 1 || incoming[target] != beat) {
            return;
        }
        incoming[target] = null;
        ctx.hit(beat.id(), 0);
        if (sender[target] != ORB) {
            shift(ctx, sender[target], REFLECT);
        }
        if (!ctx.isFinished()) {
            ctx.showNearby(QiFx.REFLECT, target, sender[target] == ORB ? QiFx.ORB : sender[target]);
        }
    }

    /**
     * {@code beat} null = SPACE while the orb was still gathering: the stance broke too early, same as no counter.
     * A press on a feint only costs ground.
     */
    @Override
    public void onMiss(PatternContext ctx, Participant who, @Nullable BeatTrack.Beat beat, @Nullable InputKey pressed) {
        int role = who.role();
        if (role > 1) {
            return;
        }
        BeatTrack.Beat pending = incoming[role];
        if (pending == null || (beat != null && beat != pending)) {
            return;
        }
        incoming[role] = null;
        if (pending.type() == CueType.FEINT) {
            // shown before the shift, which may end the duel and then nobody would see why
            ctx.showNearby(QiFx.FEINT_CAUGHT, role, 0);
            shift(ctx, role, FEINT_LOSS);
            return;
        }
        ctx.showNearby(QiFx.STRUCK, role, 0);
        ctx.miss(who, pending.id());
        ctx.action(who, PlayerAction.KNOCKED_BACK, null);
        shift(ctx, role, sender[role] == ORB ? ORB_STRUCK : STRUCK);
    }

    /** A feint whose window passed with nobody biting just breaks up; bystanders see it go. */
    private void fizzleFeints(PatternContext ctx) {
        for (int role = 0; role < 2; role++) {
            BeatTrack.Beat pending = incoming[role];
            if (pending == null || pending.type() != CueType.FEINT) {
                continue;
            }
            if (now >= freeAt[role]) {
                incoming[role] = null;
                ctx.showNearby(QiFx.FIZZLE, role, 0);
            } else {
                ctx.scheduleAt(freeAt[role]);
            }
        }
    }

    private void orbStrike(PatternContext ctx) {
        int target = ctx.random().nextInt(2);
        if (now < freeAt[target]) {
            target ^= 1;
        }
        if (now < freeAt[target]) {
            nextOrbStrike = Math.min(freeAt[0], freeAt[1]);
            return;
        }
        launch(ctx, ORB, target, ctx.random().nextFloat() < ORB_FEINT_CHANCE);
        nextOrbStrike = now + 40 + ctx.random().nextInt(41);
    }

    private void launch(PatternContext ctx, int from, int target, boolean feint) {
        BeatTrack.Beat beat = track.add(ctx, now + charge, feint ? CueType.FEINT : CueType.TAP, InputKey.SPACE.bit(),
                1 << target, feint ? CueVisual.QI_FEINT : CueVisual.QI_BURST, window());
        incoming[target] = beat;
        sender[target] = from;
        freeAt[target] = beat.close() + ctx.maxGraceTicks() + 1;
        if (feint) {
            ctx.scheduleAt(freeAt[target]);
        }
        // the same event for a feint: nobody may learn which it is before the target does
        ctx.showNearby(QiFx.SEND, from == ORB ? QiFx.ORB : from, charge);
    }

    private int window() {
        if (!frenzy) {
            return baseWindow;
        }
        return Math.max(MIN_WINDOW, baseWindow - (int) ((now - frenzyStart) / FRENZY_STEP));
    }

    /** Moves the orb {@code amount} toward {@code role} (that role is losing ground). */
    private void shift(PatternContext ctx, int role, float amount) {
        Gauge tug = tug(ctx);
        tug.add(now, role == 0 ? -amount : amount);
        if (!checkEnd(ctx, tug)) {
            scheduleEnd(ctx, tug);
        }
    }

    /** The orb reached a player: the other one wins. */
    private boolean checkEnd(PatternContext ctx, Gauge tug) {
        if (ctx.isFinished()) {
            return true;
        }
        float value = tug.value(now);
        if (value >= 1.0F) {
            ctx.winBy(ctx.participants().get(0));
        } else if (value <= 0.0F) {
            ctx.winBy(ctx.participants().get(1));
        }
        return ctx.isFinished();
    }

    /** Wakes the session when a steady push would carry the orb all the way. */
    private void scheduleEnd(PatternContext ctx, Gauge tug) {
        if (tug.ratePerTick() > 0.0F) {
            ctx.scheduleAt(tug.tickReaching(now, 1.0F));
        } else if (tug.ratePerTick() < 0.0F) {
            ctx.scheduleAt(tug.tickReaching(now, 0.0F));
        }
    }

    private static Gauge tug(PatternContext ctx) {
        return ctx.gauge(TUG, Gauge.FLAG_TUG | Gauge.FLAG_PUBLIC);
    }
}
