package net.deizyn.bhgame.game.wooden_dummy;

import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;

/** The dummy's penalties, apart from the block class so the pattern (and its tests) never load the block. */
final class WoodenDummyPenalties {
    /** Ran out of time on a set: the dummy's arm swats you. */
    static final Penalty TIMEOUT = Penalties.all(Penalties.knockback(0.4), Penalties.damage(1.0F));
    /** The end of a run (wrong arrow, early SPACE, or too many timeouts): knocked back, start again (D39, D40). */
    static final Penalty SWAT = Penalties.all(Penalties.knockback(0.6), Penalties.damage(1.0F));
    /** Hitting the dummy by hand. */
    static final Penalty BRUTE_FORCE = Penalties.all(Penalties.knockback(0.6), Penalties.damage(2.0F));

    private WoodenDummyPenalties() {
    }
}
