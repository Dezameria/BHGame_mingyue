package net.deizyn.bhgame.game.carry.water_yoke;

import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.CarryEvent;
import net.deizyn.bhgame.core.pattern.Motion;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.session.Participant;
import net.minecraft.util.RandomSource;

/**
 * Keeping the yoke level on a balance bar (D55, D56, water_yoke.md Pattern). The needle (gauge 0, 0.5 = level, toward
 * 1 = the right bucket dips) is pulled toward the heavier bucket all the time, even standing still, so uneven buckets
 * must be held level constantly. On top of that, while the carrier walks or runs, random pushes come left or right,
 * gentle or hard, with no calm spells (D70): walking means balancing the whole way. Holding LMB pushes it left and
 * RMB right (D52: right dips → LMB). Reaching either end spills one level from that side and the needle starts again
 * from the middle. Even buckets standing still settle back by themselves.
 *
 * <p>Carrying water slows the carrier; with the needle in the red part of the bar ({@link Gauge#BALANCE_DANGER})
 * on the move, the swing grows, the yoke drags outward, and the carrier slows more (D70). Slowness never weakens the
 * swing: the drift comes from walking at all, not from how fast.
 *
 * <p>The needle moves at a steady rate between changes, so the client animates it from one sync (D13): the rate
 * changes only when the drift changes (every 1-3.5 s), a key goes down or up, or something jolts the yoke. The
 * client moves the needle for its own keys at once (D57); a key that arrives here counts from half a ping ago, so
 * both sides agree on where the needle is.
 */
public final class WaterYokePattern implements Pattern {
    static final int BALANCE_GAUGE = 0;
    private static final float CENTER = 0.5F;
    /** How fast a held key moves the needle (per tick); always stronger than the hardest drift. */
    private static final float CONTROL = 0.04F;
    private static final float MAX_DRIFT = 0.032F;
    /** Pull toward the heavier bucket per level of difference (always on, D56). */
    private static final float WEIGHT_PULL = 0.004F;
    /** Drift and weight together never outrun a held key. */
    private static final float MAX_PUSH = 0.036F;
    /** In the red, on the move: the drift swings this much harder and the yoke drags outward this much per tick. */
    private static final float DANGER_SWING = 1.5F;
    private static final float DANGER_PULL = 0.006F;
    /** Share of the carrier's speed taken away while carrying water, and while the needle is in the red. */
    private static final float CARRY_SLOW = 0.15F;
    private static final float DANGER_SLOW = 0.45F;
    /** Standing still, the needle settles back toward the middle at this rate. */
    private static final float SETTLE = 0.006F;
    /** Drift strength per tick, light and heavy, by difficulty 1 / 2 / 3 (walking; running is 1.6x). */
    private static final float[] LIGHT = {0.005F, 0.007F, 0.009F};
    private static final float[] HEAVY = {0.012F, 0.016F, 0.020F};
    private static final float SPRINT_FACTOR = 1.6F;
    /** Heavy drifts: percent chance walking / running, plus this much per fatigue step. */
    private static final int HEAVY_WALK = 30;
    private static final int HEAVY_SPRINT = 50;
    private static final int HEAVY_PER_FATIGUE = 10;
    /** One drift lasts this long (ticks), walking / running. */
    private static final int WALK_HOLD_MIN = 30;
    private static final int WALK_HOLD_MAX = 70;
    private static final int SPRINT_HOLD_MIN = 20;
    private static final int SPRINT_HOLD_MAX = 45;
    /** After a spill the needle restarts in the middle and stays calm this long. */
    private static final int CALM_TICKS = 20;
    private static final float LAND_JOLT = 0.18F;
    private static final float STEP_JOLT = 0.10F;
    private static final float HARD_LAND_JOLT = 0.25F;
    /** Fatigue: one step per 20 s on the move (running counts double), at most 3; 3 s standing clears it. */
    private static final int FATIGUE_STEP_TICKS = 400;
    private static final int MAX_FATIGUE = 3;
    private static final int REST_TICKS = 60;
    private static final int SAMPLE_TICKS = 5;

    private final Gauge needle;
    private int left;
    private int right;
    private float drift;
    /** The keys' part of the needle's speed, as of the last update. */
    private float control;
    private long nextDriftTick;
    private Motion lastMotion = Motion.STILL;
    private long lastTick;
    private long stillSince = -1;
    private long movingTicks;

    WaterYokePattern(PatternContext ctx) {
        needle = ctx.gauge(BALANCE_GAUGE, Gauge.FLAG_BALANCE);
        needle.set(ctx.focusEndTick(), CENTER, 0.0F);
        left = WaterYokeBlock.left(ctx.poseData());
        right = WaterYokeBlock.right(ctx.poseData());
        needle.setKeyRate(hasWater() ? CONTROL : 0.0F);
        lastTick = ctx.focusEndTick();
    }

    @Override
    public void tick(PatternContext ctx, long now) {
        if (now < ctx.focusEndTick()) {
            ctx.scheduleAt(ctx.focusEndTick());
            return;
        }
        Motion motion = ctx.motion();
        trackFatigue(motion, now);
        if (!hasWater()) {
            // already parked by spill(); setting it again would resend it every sample
            ctx.setCarrySlow(0.0F);
            ctx.scheduleAt(now + SAMPLE_TICKS);
            return;
        }
        if (checkEdge(ctx, now)) {
            return;
        }
        ctx.setCarrySlow(swaying() && inDanger(needle.value(now)) ? DANGER_SLOW : CARRY_SLOW);
        boolean moving = motion == Motion.WALK || motion == Motion.SPRINT;
        boolean wasMoving = lastMotion == Motion.WALK || lastMotion == Motion.SPRINT;
        // in the air the yoke keeps doing what it was doing
        if (motion != Motion.AIRBORNE && (now >= nextDriftTick || moving != wasMoving
                || (moving && motion != lastMotion))) {
            newDrift(ctx, motion, now);
        }
        if (motion != Motion.AIRBORNE) {
            lastMotion = motion;
        }
        updateRate(ctx, now);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        // the session already recorded the hold; only the needle's speed changes
        if (now < ctx.focusEndTick() || !hasWater()) {
            return;
        }
        // the key went down on the player's screen about half a ping ago, and the needle moved there right away
        float change = control(who) - control;
        needle.add(now, change * ctx.inputLagTicks(who));
        if (!checkEdge(ctx, now)) {
            updateRate(ctx, now);
        }
    }

    @Override
    public void onCarryEvent(PatternContext ctx, CarryEvent event, long now) {
        if (!hasWater() || now < ctx.focusEndTick()) {
            return;
        }
        RandomSource random = ctx.random();
        switch (event) {
            case LAND -> jolt(now, random, LAND_JOLT);
            case STEP_DOWN -> jolt(now, random, STEP_JOLT);
            case HARD_LAND -> {
                spill(ctx, 0, now);
                jolt(now, random, HARD_LAND_JOLT);
            }
            case HURT -> spill(ctx, 0, now);
        }
        if (!ctx.isFinished() && hasWater() && !checkEdge(ctx, now)) {
            updateRate(ctx, now);
        }
    }

    /** A sudden lurch toward the side the needle already leans to (a random side when level). */
    private void jolt(long now, RandomSource random, float amount) {
        float value = needle.value(now);
        float dir = value > CENTER ? 1.0F : value < CENTER ? -1.0F : random.nextBoolean() ? 1.0F : -1.0F;
        needle.add(now, dir * amount);
    }

    /** A new push, light or heavy, to a random side; standing still the needle settles instead. */
    private void newDrift(PatternContext ctx, Motion motion, long now) {
        RandomSource random = ctx.random();
        if (motion != Motion.WALK && motion != Motion.SPRINT) {
            drift = 0.0F;
            nextDriftTick = Long.MAX_VALUE;
            return;
        }
        boolean sprint = motion == Motion.SPRINT;
        int fatigue = fatigue();
        int heavyChance = (sprint ? HEAVY_SPRINT : HEAVY_WALK) + fatigue * HEAVY_PER_FATIGUE;
        int d = Math.max(0, Math.min(2, ctx.difficulty() - 1));
        float strength = random.nextInt(100) < heavyChance ? HEAVY[d] : LIGHT[d];
        // a little variety inside each kind
        strength *= 0.8F + random.nextFloat() * 0.4F;
        strength *= (sprint ? SPRINT_FACTOR : 1.0F) * (1.0F + 0.15F * fatigue);
        drift = Math.min(MAX_DRIFT, strength) * (random.nextBoolean() ? 1.0F : -1.0F);
        int min = sprint ? SPRINT_HOLD_MIN : WALK_HOLD_MIN;
        int max = sprint ? SPRINT_HOLD_MAX : WALK_HOLD_MAX;
        nextDriftTick = now + min + random.nextInt(max - min + 1);
    }

    /** Sets the needle's speed from the drift and the held keys, and wakes up when it would reach an end. */
    private void updateRate(PatternContext ctx, long now) {
        control = control(ctx.participants().get(0));
        // the heavier bucket pulls its side down: left heavier → toward 0
        float weight = (right - left) * WEIGHT_PULL;
        float value = needle.value(now);
        boolean danger = swaying() && inDanger(value);
        float push = drift + weight;
        if (danger) {
            // in the red the load swings wider and drags toward the end it hangs over
            push = drift * DANGER_SWING + weight + (value > CENTER ? DANGER_PULL : -DANGER_PULL);
        }
        push = Math.max(-MAX_PUSH, Math.min(MAX_PUSH, push));
        boolean settling = false;
        if (drift == 0.0F && weight == 0.0F && control == 0.0F && nextDriftTick == Long.MAX_VALUE
                && Math.abs(value - CENTER) > SETTLE) {
            // even buckets, standing still: the yoke settles back level
            push = value > CENTER ? -SETTLE : SETTLE;
            settling = true;
        }
        float rate = push + control;
        if (rate != needle.ratePerTick() || control != needle.heldRate()) {
            needle.setRate(now, rate, control);
        }
        long wake = now + SAMPLE_TICKS;
        if (rate > 0.0F) {
            wake = Math.min(wake, needle.tickReaching(now, 1.0F));
        } else if (rate < 0.0F) {
            wake = Math.min(wake, needle.tickReaching(now, 0.0F));
        }
        if (rate != 0.0F && swaying()) {
            // into or out of the red changes the swing and the slowdown; one tick past so the value is inside
            float edge = value < CENTER ? Gauge.BALANCE_DANGER : 1.0F - Gauge.BALANCE_DANGER;
            long cross = needle.tickReaching(now, edge);
            if (cross != Long.MAX_VALUE) {
                wake = Math.min(wake, cross + 1);
            }
        }
        if (settling) {
            // stop at the middle
            wake = Math.min(wake, needle.tickReaching(now, CENTER));
        }
        ctx.scheduleAt(Math.min(wake, nextDriftTick));
    }

    /** RMB pushes the needle right, LMB left. */
    private static float control(Participant carrier) {
        return (carrier.holds(InputKey.RMB) ? CONTROL : 0.0F) - (carrier.holds(InputKey.LMB) ? CONTROL : 0.0F);
    }

    /** At an end: spill that side and start again from the middle. Returns true if the session ended. */
    private boolean checkEdge(PatternContext ctx, long now) {
        float value = needle.value(now);
        if (value > 0.0F && value < 1.0F) {
            return false;
        }
        spill(ctx, value >= 1.0F ? -1 : 1, now);
        if (ctx.isFinished()) {
            return true;
        }
        needle.set(now, CENTER, 0.0F);
        drift = 0.0F;
        nextDriftTick = now + CALM_TICKS;
        ctx.scheduleAt(nextDriftTick);
        return false;
    }

    /** One level out of {@code side} (1 left, -1 right, 0 any side with water), shown to everyone around. */
    private void spill(PatternContext ctx, int side, long now) {
        if (side == 0 || (side == 1 ? left : right) == 0) {
            side = left == 0 ? -1 : right == 0 ? 1 : ctx.random().nextBoolean() ? 1 : -1;
        }
        if (side == 1 && left > 0) {
            left--;
        } else if (side == -1 && right > 0) {
            right--;
        }
        ctx.setPoseData(WaterYokeBlock.data(left, right));
        Penalty penalty = hasWater() ? Penalty.NONE : WaterYokeBlock.EMPTY;
        ctx.miss(ctx.participants().get(0), -1, penalty);
        if (!hasWater()) {
            needle.set(now, CENTER, 0.0F);
            needle.setKeyRate(0.0F);
        }
    }

    private void trackFatigue(Motion motion, long now) {
        long dt = Math.max(0, now - lastTick);
        lastTick = now;
        if (motion == Motion.STILL) {
            if (stillSince < 0) {
                stillSince = now;
            } else if (now - stillSince >= REST_TICKS) {
                movingTicks = 0;
            }
        } else {
            stillSince = -1;
            movingTicks += motion == Motion.SPRINT ? dt * 2 : dt;
        }
    }

    /** On the move (drift spells running), as opposed to standing still where the yoke rests. */
    private boolean swaying() {
        return nextDriftTick != Long.MAX_VALUE;
    }

    private static boolean inDanger(float value) {
        return value < Gauge.BALANCE_DANGER || value > 1.0F - Gauge.BALANCE_DANGER;
    }

    private boolean hasWater() {
        return left + right > 0;
    }

    private int fatigue() {
        return (int) Math.min(MAX_FATIGUE, movingTicks / FATIGUE_STEP_TICKS);
    }
}
