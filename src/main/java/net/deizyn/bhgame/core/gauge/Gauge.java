package net.deizyn.bhgame.core.gauge;

/**
 * A value from 0 to 1 that moves at a steady rate, so the client can animate it from one sync (D13): the server
 * sends {@code value + ratePerTick} only when the rate changes or the value jumps, plus a resync every
 * gaugeResyncTicks while it moves. Never drawn as a bar unless a game really needs it (D17).
 */
public final class Gauge {
    /** The block shows this gauge as its progress (e.g. the herb rising out of the soil). */
    public static final int FLAG_PROGRESS = 1;
    /**
     * Drawn as a balance bar with a needle, 0.5 = level (water_yoke, D55). A held LMB moves it toward 0 and RMB
     * toward 1 at {@link #keyRate()}, which the client applies itself the moment a key changes (D57).
     */
    public static final int FLAG_BALANCE = 2;
    /**
     * The red part at each end of a balance bar, as a share of the whole bar (water_yoke, D70): the HUD draws it and
     * the game makes the needle in there harder to hold.
     */
    public static final float BALANCE_DANGER = 0.3F;
    /**
     * A tug of war between roles 0 and 1 (qi_station, D58): where the orb hangs, 0 = against role 0, 1 = against
     * role 1, 0.5 = the middle.
     */
    public static final int FLAG_TUG = 4;
    /**
     * Also shown to everyone near the block, with the participants' held keys, as {@code S2CNearbyState} (D60).
     * Cosmetic: the qi_station orb that bystanders see.
     */
    public static final int FLAG_PUBLIC = 8;

    private final int index;
    private final int flags;
    private float value;
    private float ratePerTick;
    private long setTick;
    /** Speed one held key adds (FLAG_BALANCE), sent so the client can move the needle itself; 0 = no keys. */
    private float keyRate;
    /** The part of {@link #ratePerTick} that comes from the keys held now; the client adds its own instead. */
    private float heldRate;
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

    /** The rate without the held keys' part: what the client animates before adding its own keys. */
    public float baseRate() {
        return ratePerTick - heldRate;
    }

    public float keyRate() {
        return keyRate;
    }

    public float heldRate() {
        return heldRate;
    }

    /** A new value and rate with no keys' part in it. */
    public void set(long now, float value, float ratePerTick) {
        set(now, value, ratePerTick, 0.0F);
    }

    private void set(long now, float value, float ratePerTick, float heldRate) {
        this.value = clamp(value);
        this.ratePerTick = ratePerTick;
        this.heldRate = heldRate;
        this.setTick = now;
        this.dirty = true;
    }

    /** Keeps the current value and changes only the rate. */
    public void setRate(long now, float ratePerTick) {
        set(now, value(now), ratePerTick, heldRate);
    }

    /** Keeps the current value; {@code heldRate} is the part of the new rate that comes from the held keys. */
    public void setRate(long now, float ratePerTick, float heldRate) {
        set(now, value(now), ratePerTick, heldRate);
    }

    /** How fast one held key moves this gauge; 0 when keys do nothing (the client then stops predicting). */
    public void setKeyRate(float keyRate) {
        if (keyRate != this.keyRate) {
            this.keyRate = keyRate;
            this.dirty = true;
        }
    }

    public void add(long now, float delta) {
        set(now, value(now) + delta, ratePerTick, heldRate);
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
