package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link PatternContext} for unit tests: zero ping, nothing sent over a network. Records hits, misses, cues and
 * banners, and ends on {@link #win()} / {@link #fail()} like a real session would.
 */
public final class FakePatternContext implements PatternContext {
    public final List<Participant> participants = new ArrayList<>();
    public final List<String> log = new ArrayList<>();
    public final List<CueData> cues = new ArrayList<>();
    public final List<String> banners = new ArrayList<>();
    public final List<PlayerAction> actions = new ArrayList<>();
    public final Map<Integer, Gauge> gauges = new HashMap<>();
    public final RandomSource random = RandomSource.create(1);
    public int hp = 10;
    public int difficulty = 1;
    public long focusEnd;
    public long nextEvent = Long.MAX_VALUE;
    public boolean finished;
    public String best;
    public int nextId;

    public FakePatternContext(int players) {
        this(players, InputKey.LMB, InputKey.RMB);
    }

    public FakePatternContext(int players, InputKey... keys) {
        for (int role = 0; role < players; role++) {
            participants.add(new Participant(UUID.randomUUID(), role, InputProfile.locked(keys), true));
        }
    }

    @Override public int difficulty() { return difficulty; }
    @Override public int hp() { return hp; }
    @Override public int maxHp() { return 10; }
    @Override public int mistakes() { return 0; }
    @Override public RandomSource random() { return random; }
    @Override public long focusEndTick() { return focusEnd; }
    @Override public List<Participant> participants() { return participants; }
    @Override public int newCueId() { return nextId++; }
    @Override public void scheduleAt(long tick) { nextEvent = Math.min(nextEvent, tick); }
    @Override public int graceTicks(Participant participant) { return 0; }
    @Override public int maxGraceTicks() { return 0; }
    @Override public void sendCues(List<CueData> sent) { cues.addAll(sent); }

    @Override
    public void hit(int cueId, int damage) {
        hp -= damage;
        log.add(damage == 1 ? "hit " + cueId : "hit " + cueId + " x" + damage);
    }

    @Override public void miss(Participant who, int cueId) { log.add("miss r" + who.role() + " " + cueId); }
    @Override public void miss(Participant who, int cueId, Penalty penalty) { miss(who, cueId); }
    @Override public void win() { log.add("win"); finished = true; }
    @Override public void fail() { log.add("fail"); finished = true; }
    @Override public void action(Participant who, PlayerAction action, InputKey key) { actions.add(action); }
    @Override public void banner(String translationKey, int value) { banners.add(translationKey + " " + value); }
    @Override public void setOutcomeMessage(String translationKey, int value) { }
    @Override public void recordBest(String objective, int value) { best = objective + "=" + value; }
    @Override public void setFailPenalty(Penalty penalty) { }

    @Override
    public Gauge gauge(int index, int flags) {
        return gauges.computeIfAbsent(index, i -> new Gauge(i, flags));
    }

    @Override public boolean isFinished() { return finished; }

    /** Clears the schedule before a pattern call, like the session manager does. */
    public void clearSchedule() {
        nextEvent = Long.MAX_VALUE;
    }
}
