package net.deizyn.bhgame.game.station.wooden_dummy;

import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;

/** The dummy's penalties, apart from the block class so the pattern (and its tests) never load the block. */
final class WoodenDummyPenalties {
    /**
     * The end of a run (wrong arrow, early SPACE, or out of time): knocked back, a real 5-damage blow and a drumstick
     * of hunger, start again (D39, D40, D77, D80).
     */
    static final Penalty SWAT = Penalties.all(Penalties.knockback(0.6), Penalties.blow(5.0F), Penalties.hunger(2));
    /** Hitting the dummy by hand. */
    static final Penalty BRUTE_FORCE = Penalties.all(Penalties.knockback(0.6), Penalties.damage(2.0F));

    private WoodenDummyPenalties() {
    }
}
