package net.deizyn.bhgame.game.station.qi_station;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.session.FakePatternContext;
import net.deizyn.bhgame.core.session.Participant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PvP tug of war (D58), played by two bots through the same PatternContext a real session uses. */
class QiStationPatternTest {
    private static final float EPS = 1.0E-4F;

    private final FakePatternContext ctx = new FakePatternContext(2, InputKey.LMB, InputKey.RMB, InputKey.SPACE,
            InputKey.SNEAK);
    private final Participant p0 = ctx.participants.get(0);
    private final Participant p1 = ctx.participants.get(1);
    private QiStationPattern pattern;
    private long now;

    private void start() {
        ctx.focusEnd = 40;
        pattern = new QiStationPattern(ctx);
        now = 40;
        tick();
    }

    private void tick() {
        ctx.clearSchedule();
        pattern.tick(ctx, now);
    }

    /** Runs the clock to {@code until}, waking the pattern when it asked to be. */
    private void runTo(long until) {
        while (now < until && !ctx.finished) {
            now++;
            if (now >= ctx.nextEvent) {
                tick();
            }
        }
    }

    private void press(Participant who, InputKey key) {
        pattern.onInput(ctx, who, key, true, now);
    }

    private void release(Participant who, InputKey key) {
        pattern.onInput(ctx, who, key, false, now);
    }

    /** Shift + RMB, letting go of Shift at once so the draw costs nothing. */
    private void feint(Participant who) {
        press(who, InputKey.SNEAK);
        press(who, InputKey.RMB);
        release(who, InputKey.SNEAK);
    }

    @Test
    void rightClickWhileDrawingIsAFeint() {
        start();
        press(p0, InputKey.SNEAK);
        runTo(now + 20);
        press(p0, InputKey.RMB);
        assertEquals(CueType.FEINT, lastCue().type());
    }

    private float tug() {
        return ctx.gauges.get(QiStationPattern.TUG).value(now);
    }

    private CueData lastCue() {
        return ctx.cues.get(ctx.cues.size() - 1);
    }

    @Test
    void pushingAloneDrivesTheOrbHome() {
        start();
        press(p0, InputKey.LMB);
        runTo(now + 200);
        assertTrue(ctx.log.contains("win r0"), ctx.log.toString());
    }

    @Test
    void bothPushingIsAStalemate() {
        start();
        press(p0, InputKey.LMB);
        press(p1, InputKey.LMB);
        runTo(now + 300);
        assertEquals(0.5F, tug(), EPS);
    }

    @Test
    void anUncounteredBurstDrivesTheOrbAtTheTarget() {
        start();
        press(p0, InputKey.RMB);
        CueData cue = lastCue();
        assertEquals(1 << 1, cue.roles());
        runTo(now + cue.opensIn() + cue.window() + 2);
        assertEquals(0.5F + QiStationPattern.STRUCK, tug(), EPS);
        assertTrue(ctx.log.contains("miss r1 " + cue.id()), ctx.log.toString());
    }

    @Test
    void aCounterThrowsTheBurstBack() {
        start();
        press(p0, InputKey.RMB);
        CueData cue = lastCue();
        runTo(now + cue.opensIn() + 1);
        press(p1, InputKey.SPACE);
        runTo(now + cue.window() + 2);
        assertEquals(0.5F - QiStationPattern.REFLECT, tug(), EPS);
        assertTrue(ctx.log.contains("hit " + cue.id() + " x0"), ctx.log.toString());
    }

    @Test
    void counteringTooEarlyIsAMissAndALatePressDoesNotSaveIt() {
        start();
        press(p0, InputKey.RMB);
        CueData cue = lastCue();
        runTo(now + 3);
        press(p1, InputKey.SPACE);
        assertEquals(0.5F + QiStationPattern.STRUCK, tug(), EPS);
        runTo(now + cue.opensIn());
        press(p1, InputKey.SPACE);
        runTo(now + cue.window() + 2);
        assertEquals(0.5F + QiStationPattern.STRUCK, tug(), EPS);
    }

    @Test
    void counteringThinAirCostsALittle() {
        start();
        press(p1, InputKey.SPACE);
        assertEquals(0.5F + QiStationPattern.WHIFF_LOSS, tug(), EPS);
    }

    @Test
    void aStrikeOnCooldownIsIgnored() {
        start();
        press(p0, InputKey.RMB);
        int sent = ctx.cues.size();
        runTo(now + 5);
        press(p0, InputKey.RMB);
        assertEquals(sent, ctx.cues.size());
    }

    @Test
    void shiftRightClickSendsAFeintThatOnlyHurtsWhoCountersIt() {
        start();
        feint(p0);
        CueData cue = lastCue();
        assertEquals(CueType.FEINT, cue.type());
        runTo(now + cue.opensIn() + cue.window() + 2);
        assertEquals(0.5F, tug(), EPS);

        runTo(now + QiStationPattern.STRIKE_COOLDOWN);
        feint(p0);
        cue = lastCue();
        assertEquals(CueType.FEINT, cue.type());
        runTo(now + cue.opensIn() + 1);
        press(p1, InputKey.SPACE);
        assertEquals(0.5F + QiStationPattern.FEINT_LOSS, tug(), EPS);
    }

    @Test
    void aFeintSharesTheCooldown() {
        start();
        feint(p0);
        int sent = ctx.cues.size();
        runTo(now + 5);
        press(p0, InputKey.RMB);
        assertEquals(sent, ctx.cues.size());
    }

    @Test
    void spaceWhilePushingDoesNotCounter() {
        start();
        press(p0, InputKey.LMB);
        press(p1, InputKey.LMB);
        press(p0, InputKey.RMB);
        CueData cue = lastCue();
        runTo(now + cue.opensIn() + 1);
        press(p1, InputKey.SPACE);
        runTo(now + cue.window() + 2);
        assertEquals(0.5F + QiStationPattern.STRUCK, tug(), EPS);
        assertTrue(ctx.log.contains("miss r1 " + cue.id()), ctx.log.toString());
    }

    @Test
    void letGoTooLateAndTheCounterIsLost() {
        start();
        press(p0, InputKey.RMB);
        press(p1, InputKey.LMB);
        CueData cue = lastCue();
        runTo(now + cue.opensIn());
        release(p1, InputKey.LMB);
        runTo(now + 1);
        press(p1, InputKey.SPACE);
        runTo(now + cue.window() + 2);
        assertTrue(ctx.log.contains("miss r1 " + cue.id()), ctx.log.toString());
    }

    @Test
    void letGoInTimeAndTheCounterHolds() {
        start();
        press(p0, InputKey.RMB);
        press(p1, InputKey.LMB);
        CueData cue = lastCue();
        runTo(now + cue.opensIn() - QiStationPattern.STANCE_TICKS - 1);
        release(p1, InputKey.LMB);
        runTo(now + QiStationPattern.STANCE_TICKS + 2);
        press(p1, InputKey.SPACE);
        runTo(now + cue.window() + 2);
        assertTrue(ctx.log.contains("hit " + cue.id() + " x0"), ctx.log.toString());
    }

    @Test
    void bystandersSeeTheSameSendForAFeintAndABurst() {
        start();
        press(p0, InputKey.RMB);
        String real = ctx.nearby.get(ctx.nearby.size() - 1);
        runTo(now + 80);
        feint(p1);
        String feint = ctx.nearby.get(ctx.nearby.size() - 1);
        assertEquals(real.replace(" r0 ", " r1 "), feint);
        assertTrue(real.startsWith(QiFx.SEND + " r0 "), real);
    }

    @Test
    void verdictsAreShownNearby() {
        start();
        press(p0, InputKey.RMB);
        CueData cue = lastCue();
        runTo(now + cue.opensIn() + cue.window() + 2);
        assertTrue(ctx.nearby.contains(QiFx.STRUCK + " r1 0"), ctx.nearby.toString());

        runTo(now + 80);
        press(p0, InputKey.RMB);
        cue = lastCue();
        runTo(now + cue.opensIn() + 1);
        press(p1, InputKey.SPACE);
        assertTrue(ctx.nearby.contains(QiFx.REFLECT + " r1 0"), ctx.nearby.toString());

        runTo(now + 80);
        feint(p0);
        cue = lastCue();
        runTo(now + cue.opensIn() + cue.window() + 10);
        assertTrue(ctx.nearby.contains(QiFx.FIZZLE + " r1 0"), ctx.nearby.toString());
    }

    @Test
    void drawingAloneLosesGround() {
        start();
        press(p0, InputKey.SNEAK);
        runTo(now + 100);
        assertEquals(0.5F - 100 * QiStationPattern.PULL_RATE, tug(), EPS);
        assertTrue(ctx.nearby.contains(QiFx.GRIP + " r0 0"), ctx.nearby.toString());
    }

    @Test
    void aDrawThatTookHoldTurnsThePushBack() {
        start();
        press(p1, InputKey.LMB);
        press(p0, InputKey.SNEAK);
        runTo(now + QiStationPattern.GRIP_TICKS);
        float gripped = 0.5F - QiStationPattern.GRIP_TICKS * (QiStationPattern.PUSH_RATE + QiStationPattern.PULL_RATE);
        assertEquals(gripped, tug(), EPS);
        assertTrue(ctx.nearby.contains(QiFx.YIELD + " r0 1"), ctx.nearby.toString());
        runTo(now + 40);
        assertEquals(gripped + 40 * QiStationPattern.YIELD_RATE, tug(), EPS);

        // the pusher lets go: only the draw's own cost is left
        release(p1, InputKey.LMB);
        runTo(now + 20);
        assertEquals(gripped + 40 * QiStationPattern.YIELD_RATE - 20 * QiStationPattern.PULL_RATE, tug(), EPS);
    }

    @Test
    void aPushOnlyMeetsTheDrawOnceItTakesHold() {
        start();
        press(p0, InputKey.SNEAK);
        runTo(now + QiStationPattern.GRIP_TICKS + 5);
        float before = tug();
        press(p1, InputKey.LMB);
        runTo(now + 10);
        assertEquals(before + 10 * QiStationPattern.YIELD_RATE, tug(), EPS);
    }

    @Test
    void bothDrawingIsAStalemate() {
        start();
        press(p0, InputKey.SNEAK);
        press(p1, InputKey.SNEAK);
        runTo(now + 300);
        assertEquals(0.5F, tug(), EPS);
    }

    @Test
    void drawingWinsOverYourOwnPush() {
        start();
        press(p0, InputKey.LMB);
        press(p0, InputKey.SNEAK);
        runTo(now + 50);
        assertEquals(0.5F - 50 * QiStationPattern.PULL_RATE, tug(), EPS);
    }

    @Test
    void spaceWhileDrawingDoesNotCounter() {
        start();
        press(p0, InputKey.RMB);
        press(p1, InputKey.SNEAK);
        CueData cue = lastCue();
        runTo(now + cue.opensIn() + 1);
        press(p1, InputKey.SPACE);
        runTo(now + cue.window() + 2);
        assertTrue(ctx.log.contains("miss r1 " + cue.id()), ctx.log.toString());
    }

    @Test
    void theFrenziedOrbEndsAPassiveDuel() {
        start();
        press(p0, InputKey.LMB);
        press(p1, InputKey.LMB);
        runTo(40 + QiStationPattern.FRENZY_AFTER - 1);
        assertTrue(ctx.banners.isEmpty());
        runTo(100_000);
        assertTrue(ctx.finished);
        assertTrue(ctx.banners.contains("bhgame.qi_station.frenzy 0"));
        assertTrue(ctx.cues.stream().anyMatch(c -> c.type() == CueType.FEINT));
    }
}
