package net.deizyn.bhgame.client;

import net.deizyn.bhgame.client.pose.ClientPoses;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.network.S2CNearbyState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Sessions near us that we may watch (D60): the public gauge and who holds which key, per block, from
 * {@link S2CNearbyState}. Ours included, so the particles look the same for everyone. A session is forgotten once none
 * of its players shows a session pose any more (the end sends the pose NONE), or they are all gone.
 */
public final class NearbySessions {

    /**
     * A state without any posed participant is kept this long. Poses are sent before the state on the same channel
     * (at the start and to a new tracker), so this is only a small margin; the orb should vanish with the duel.
     */
    private static final int ORPHAN_TICKS = 5;

    public static final class State {
        public final BlockPos pos;
        private int[] entityIds = new int[0];
        private int[] heldKeys = new int[0];
        private float value;
        private float rate;
        private long syncedAt;
        private long orphanSince = -1;

        State(BlockPos pos) {
            this.pos = pos;
        }

        /** The public gauge now, animated from the last sync like the players' own gauges (D13). */
        public float value(double time) {
            float v = value + rate * (float) (time - syncedAt);
            return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
        }

        @Nullable
        public Player player(int role) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || role < 0 || role >= entityIds.length
                    || entityIds[role] == S2CNearbyState.NO_ENTITY) {
                return null;
            }
            Entity entity = mc.level.getEntity(entityIds[role]);
            return entity instanceof Player player ? player : null;
        }

        public boolean holds(int role, InputKey key) {
            return role >= 0 && role < heldKeys.length && (heldKeys[role] & key.bit()) != 0;
        }
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    static State onState(S2CNearbyState message) {
        State state = STATES.computeIfAbsent(message.pos().immutable(), State::new);
        state.entityIds = message.entityIds();
        state.heldKeys = message.heldKeys();
        state.value = message.value();
        state.rate = message.ratePerTick();
        state.syncedAt = ClientSession.clock();
        state.orphanSince = -1;
        return state;
    }

    @Nullable
    public static State get(BlockPos pos) {
        return STATES.get(pos);
    }

    public static Collection<State> all() {
        return STATES.values();
    }

    static void tick(Minecraft mc) {
        if (STATES.isEmpty()) {
            return;
        }
        long now = ClientSession.clock();
        Iterator<State> iterator = STATES.values().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            boolean posed = false;
            for (int id : state.entityIds) {
                if (id != S2CNearbyState.NO_ENTITY && ClientPoses.hasPose(id)) {
                    posed = true;
                    break;
                }
            }
            if (posed || mc.level == null) {
                state.orphanSince = -1;
                if (mc.level == null) {
                    iterator.remove();
                }
            } else if (state.orphanSince < 0) {
                state.orphanSince = now;
            } else if (now - state.orphanSince > ORPHAN_TICKS) {
                iterator.remove();
            }
        }
    }

    static void clear() {
        STATES.clear();
    }

    private NearbySessions() {
    }
}
