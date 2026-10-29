package net.deizyn.bhgame.core.gauge;

/**
 * A value from 0 to 1 that moves at a steady rate, so the client can animate it from one sync (D13): the server
 * sends {@code value + ratePerTick} only when the rate changes or the value jumps, plus a resync every
 * gaugeResyncTicks while it moves. Never drawn as a bar unless a game really needs it (D17).
 */
public final class Gauge {
    /** The block shows this gauge as its progress (e.g. the herb rising out of the soil). */
    public static final int FLAG_PROGRESS = 1;

    private final int index;
    private final int flags;
    private float value;
    private float ratePerTick;
    private long setTick;
    long lastSyncTick;
    boolean dirty;

    public Gauge(int index, int flags) {
        this.index = index;
        this.flags = flags;
    }

    public int index() {
        return index;
    }

    public int flags() {
        return flags;
    }

    /** The value at {@code now}, clamped to 0..1. */
    public float value(long now) {
        return clamp(value + ratePerTick * (now - setTick));
    }

    public float ratePerTick() {
        return ratePerTick;
    }

    public void set(long now, float value, float ratePerTick) {
        this.value = clamp(value);
        this.ratePerTick = ratePerTick;
        this.setTick = now;
        this.dirty = true;
    }

    /** Keeps the current value and changes only the rate. */
    public void setRate(long now, float ratePerTick) {
        set(now, value(now), ratePerTick);
    }

    public void add(long now, float delta) {
        set(now, value(now) + delta, ratePerTick);
    }

    /** First tick at which the value reaches {@code target} at the current rate, or Long.MAX_VALUE if it never does. */
    public long tickReaching(long now, float target) {
        float current = value(now);
        if (ratePerTick == 0.0F || (target - current) / ratePerTick < 0.0F) {
            return current == target ? now : Long.MAX_VALUE;
        }
        return now + (long) Math.ceil((target - current) / ratePerTick);
    }

    /** Whether the client needs another sync: changed, or still moving and not synced for {@code resyncTicks}. */
    public boolean needsSync(long now, int resyncTicks) {
        if (dirty) {
            return true;
        }
        float current = value(now);
        boolean moving = ratePerTick != 0.0F && !(current <= 0.0F && ratePerTick < 0.0F)
                && !(current >= 1.0F && ratePerTick > 0.0F);
        return moving && now - lastSyncTick >= resyncTicks;
    }

    public void markSynced(long now) {
        dirty = false;
        lastSyncTick = now;
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }
}
