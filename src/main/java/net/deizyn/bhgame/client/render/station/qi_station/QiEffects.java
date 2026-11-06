package net.deizyn.bhgame.client.render.station.qi_station;

import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.client.NearbySessions;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.game.station.qi_station.QiFx;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * qi_station particles, the same on every client near the pedestal (D60), duellists included:
 * <ul>
 *   <li>pushing: a thin stream of qi from the palms into the orb; both pushing: sparks where the two meet;</li>
 *   <li>drawing the orb back (Shift, D76): blue qi flowing from the orb into the palms, with a hiss when it starts;
 *       once it takes hold a blue ring turns at the palms (and a chime), and when it turns a push back the orb
 *       sweeps and qi lashes into the pusher's chest;</li>
 *   <li>a burst (or feint, which looks the same): a surge from the sender's palms, then glyphs drawn into the orb
 *       while it gathers; our own burst is gold and our own feint purple from the start, on our screen only, so we
 *       never mix up what we sent with what is coming at us (D65);</li>
 *   <li>the verdict: a flash and qi flung back at the sender (countered), a blast into the target (struck), a purple
 *       break toward whoever bit on a feint, a purple fizzle when nobody did, a puff on a whiff.</li>
 * </ul>
 * Nothing here is spawned farther than 24 blocks away (docs/multiplayer-performance.md). Placeholder vanilla
 * particles until P6.
 */
public final class QiEffects {
    private static final double MAX_DISTANCE_SQR = 24.0 * 24.0;
    /** From the eyes to the palms of arms held out at the orb (ClientPoses.posePalmPush). */
    private static final double PALM_REACH = 0.7;
    private static final double PALM_DROP = 0.3;
    /** Our own burst and our own feint (D65); the other side's stay white with enchant glyphs. */
    private static final DustParticleOptions OWN_BURST = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.2F), 1.2F);
    private static final DustParticleOptions OWN_FEINT = new DustParticleOptions(new Vector3f(0.72F, 0.38F, 0.95F), 1.2F);
    /** The draw's ring at the palms once it has taken hold. */
    private static final DustParticleOptions DRAW_RING = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 0.95F), 0.9F);

    private static final class Gather {
        final BlockPos pos;
        final long start;
        final long end;
        /** Our own send: its colour, else null (glyphs). */
        @Nullable
        final ParticleOptions own;

        Gather(BlockPos pos, long start, long end, @Nullable ParticleOptions own) {
            this.pos = pos;
            this.start = start;
            this.end = end;
            this.own = own;
        }
    }

    private static final List<Gather> GATHERS = new ArrayList<>();
    /** Per pedestal and role: drawing last tick (to catch the start), and whether the draw has taken hold. */
    private static final Map<BlockPos, boolean[]> DRAWN = new HashMap<>();
    private static final Map<BlockPos, boolean[]> GRIPPED = new HashMap<>();
    private static boolean lastSendFeint;

    /** Our RMB in a qi duel: a feint if Shift was down; only we know which one we sent. */
    public static void onLocalSend(boolean feint) {
        lastSendFeint = feint;
    }

    /** Whether {@code player} is drawing a qi orb back (for the arms' pose). */
    public static boolean isDrawing(Player player) {
        for (NearbySessions.State state : NearbySessions.all()) {
            for (int role = 0; role < 2; role++) {
                if (state.player(role) == player) {
                    return drawing(state, role);
                }
            }
        }
        return false;
    }

    public static void onEvent(NearbySessions.State state, int event, int role, int data) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !inRange(mc, state.pos)) {
            return;
        }
        Vec3 orb = orbWorld(state, ClientSession.clock());
        switch (event) {
            case QiFx.SEND -> {
                Player sender = state.player(role);
                ParticleOptions own = sender != null && sender == mc.player ? ownSend() : null;
                if (sender != null) {
                    Vec3 palms = palms(sender);
                    stream(level, own != null ? own : ParticleTypes.END_ROD, palms, orb, own != null ? 16 : 10, 0.2,
                            0.04);
                    puff(level, ParticleTypes.CLOUD, palms, 3, 0.03);
                }
                long now = ClientSession.clock();
                GATHERS.add(new Gather(state.pos, now, now + Math.max(1, data), own));
            }
            case QiFx.REFLECT -> {
                level.addParticle(ParticleTypes.FLASH, orb.x, orb.y, orb.z, 0.0, 0.0, 0.0);
                Player sender = data == QiFx.ORB ? null : state.player(data);
                if (sender != null) {
                    stream(level, ParticleTypes.END_ROD, orb, chest(sender), 14, 0.22, 0.05);
                } else {
                    puff(level, ParticleTypes.END_ROD, orb, 14, 0.12);
                }
                endGather(state.pos);
            }
            case QiFx.STRUCK -> {
                Player victim = state.player(role);
                if (victim != null) {
                    Vec3 chest = chest(victim);
                    stream(level, ParticleTypes.END_ROD, orb, chest, 18, 0.3, 0.06);
                    level.addParticle(ParticleTypes.EXPLOSION, chest.x, chest.y, chest.z, 0.0, 0.0, 0.0);
                    puff(level, ParticleTypes.CRIT, chest, 10, 0.25);
                }
                endGather(state.pos);
            }
            case QiFx.FEINT_CAUGHT -> {
                Player presser = state.player(role);
                if (presser != null) {
                    stream(level, ParticleTypes.WITCH, orb, chest(presser), 16, 0.25, 0.08);
                }
                puff(level, ParticleTypes.WITCH, orb, 8, 0.1);
                endGather(state.pos);
            }
            case QiFx.FIZZLE -> {
                puff(level, ParticleTypes.WITCH, orb, 10, 0.06);
                puff(level, ParticleTypes.SMOKE, orb, 4, 0.03);
                endGather(state.pos);
            }
            case QiFx.WHIFF -> {
                Player presser = state.player(role);
                if (presser != null) {
                    puff(level, ParticleTypes.POOF, palms(presser), 4, 0.04);
                }
            }
            case QiFx.GRIP -> {
                if (role < 2) {
                    GRIPPED.computeIfAbsent(state.pos, pos -> new boolean[2])[role] = true;
                }
                Player drawer = state.player(role);
                if (drawer != null) {
                    Vec3 palms = palms(drawer);
                    ring(level, DRAW_RING, palms, 0.35, 10);
                    stream(level, ParticleTypes.SOUL_FIRE_FLAME, orb, palms, 6, 0.12, 0.03);
                    sound(level, palms, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.45F, 1.8F);
                }
            }
            case QiFx.YIELD -> {
                level.addParticle(ParticleTypes.SWEEP_ATTACK, orb.x, orb.y, orb.z, 0.0, 0.0, 0.0);
                puff(level, ParticleTypes.SOUL_FIRE_FLAME, orb, 8, 0.08);
                Player pusher = state.player(data);
                if (pusher != null) {
                    Vec3 chest = chest(pusher);
                    stream(level, ParticleTypes.SOUL_FIRE_FLAME, orb, chest, 16, 0.25, 0.05);
                    puff(level, ParticleTypes.CRIT, chest, 8, 0.2);
                }
                sound(level, orb, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.8F);
                sound(level, orb, SoundEvents.TRIDENT_RIPTIDE_1, 0.5F, 1.3F);
            }
            default -> {
            }
        }
    }

    /** Pushing streams, the clash, and gathering glyphs; runs every client tick. */
    public static void tick(Minecraft mc) {
        Level level = mc.level;
        if (level == null || mc.player == null) {
            GATHERS.clear();
            return;
        }
        long now = ClientSession.clock();
        GATHERS.removeIf(gather -> now >= gather.end || NearbySessions.get(gather.pos) == null);
        DRAWN.keySet().removeIf(pos -> NearbySessions.get(pos) == null);
        GRIPPED.keySet().removeIf(pos -> NearbySessions.get(pos) == null);
        if (NearbySessions.all().isEmpty() || mc.isPaused()) {
            return;
        }
        for (NearbySessions.State state : NearbySessions.all()) {
            if (!inRange(mc, state.pos)) {
                continue;
            }
            Vec3 orb = orbWorld(state, now);
            boolean push0 = pushing(state, 0);
            boolean push1 = pushing(state, 1);
            if (now % 2 == 0) {
                if (push0) {
                    pushStream(level, state.player(0), orb);
                }
                if (push1) {
                    pushStream(level, state.player(1), orb);
                }
            }
            if (push0 && push1 && now % 3 == 0) {
                // two pushes meeting at the orb
                puff(level, ParticleTypes.ELECTRIC_SPARK, orb, 2, 0.15);
            }
            drawEffects(level, state, orb, now);
        }
        for (Gather gather : GATHERS) {
            NearbySessions.State state = NearbySessions.get(gather.pos);
            if (state == null || !inRange(mc, gather.pos)) {
                continue;
            }
            Vec3 orb = orbWorld(state, now);
            if (gather.own != null) {
                // our own send: a ring of our colour closing in on the orb
                for (int i = 0; i < 2; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2.0;
                    double r = 0.45 + level.random.nextDouble() * 0.2;
                    Vec3 from = orb.add(Math.cos(angle) * r, (level.random.nextDouble() - 0.5) * 0.4, Math.sin(angle) * r);
                    Vec3 velocity = orb.subtract(from).scale(0.15);
                    level.addParticle(gather.own, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
                }
                continue;
            }
            for (int i = 0; i < 3; i++) {
                // the enchant glyph flies from orb + offset to the orb itself
                double dx = (level.random.nextDouble() - 0.5) * 1.6;
                double dy = (level.random.nextDouble() - 0.3) * 1.2;
                double dz = (level.random.nextDouble() - 0.5) * 1.6;
                level.addParticle(ParticleTypes.ENCHANT, orb.x, orb.y, orb.z, dx, dy, dz);
            }
        }
    }

    /** 0 to 1 while a burst gathers at {@code pos}, for the orb that bystanders see. */
    public static float gather(BlockPos pos, double time) {
        float best = 0.0F;
        for (Gather gather : GATHERS) {
            if (gather.pos.equals(pos) && time >= gather.start) {
                best = Math.max(best, (float) Math.min(1.0, (time - gather.start) / (gather.end - gather.start)));
            }
        }
        return best;
    }

    /** The orb in world space: our own gauge when it is our duel (no ping behind), else the shared state. */
    static Vec3 orbWorld(NearbySessions.State state, double time) {
        float tug = state.value(time);
        ClientSession session = ClientSession.get();
        if (session != null && session.pos.equals(state.pos)) {
            int index = session.gaugeWithFlag(Gauge.FLAG_TUG);
            if (index >= 0) {
                tug = session.gauge(index, time);
            }
        }
        float[] local = QiOrbRenderer.orbLocal(state.pos, tug, state.player(0), state.player(1), time);
        return new Vec3(state.pos.getX() + local[0], state.pos.getY() + local[1], state.pos.getZ() + local[2]);
    }

    /** Drawing streams and the start of a draw, for both sides of one pedestal. */
    private static void drawEffects(Level level, NearbySessions.State state, Vec3 orb, long now) {
        boolean[] drawn = DRAWN.computeIfAbsent(state.pos, pos -> new boolean[2]);
        boolean[] gripped = GRIPPED.computeIfAbsent(state.pos, pos -> new boolean[2]);
        for (int role = 0; role < 2; role++) {
            boolean drawing = drawing(state, role);
            Player drawer = state.player(role);
            if (drawing != drawn[role]) {
                drawn[role] = drawing;
                gripped[role] = false;
                if (drawing && drawer != null) {
                    // the palms snap back: a hiss and qi torn loose from the orb
                    Vec3 palms = palms(drawer);
                    stream(level, ParticleTypes.SOUL_FIRE_FLAME, orb, palms, 6, 0.1, 0.04);
                    sound(level, palms, SoundEvents.TRIDENT_RETURN, 0.6F, 1.4F);
                }
            }
            if (!drawing || drawer == null) {
                continue;
            }
            Vec3 palms = palms(drawer);
            if (gripped[role] || now % 2 == 0) {
                drawStream(level, orb, palms, gripped[role] ? 2 : 1);
            }
            if (gripped[role] && now % 3 == 0) {
                ring(level, DRAW_RING, palms, 0.3, 4);
            }
            Player other = state.player(role ^ 1);
            if (gripped[role] && other != null && pushing(state, role ^ 1) && !drawing(state, role ^ 1)
                    && now % 2 == 0) {
                // the push turned back: qi flung from the orb at the pusher
                stream(level, ParticleTypes.END_ROD, orb, chest(other), 2, 0.25, 0.04);
            }
        }
    }

    /** {@code count} wisps of blue qi drawn from around the orb into the palms. */
    private static void drawStream(Level level, Vec3 orb, Vec3 palms, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 from = orb.add((level.random.nextDouble() - 0.5) * 0.4, (level.random.nextDouble() - 0.5) * 0.3,
                    (level.random.nextDouble() - 0.5) * 0.4);
            Vec3 velocity = palms.subtract(from).scale(0.12);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, velocity.x, velocity.y,
                    velocity.z);
        }
    }

    private static boolean pushing(NearbySessions.State state, int role) {
        return holds(state, role, InputKey.LMB);
    }

    private static boolean drawing(NearbySessions.State state, int role) {
        return holds(state, role, InputKey.SNEAK);
    }

    /** Our own keys straight from the keyboard (no ping behind), everyone else's from the shared state. */
    private static boolean holds(NearbySessions.State state, int role, InputKey key) {
        ClientSession session = ClientSession.get();
        if (session != null && session.pos.equals(state.pos) && session.role == role) {
            return session.holdsLocally(key);
        }
        return state.holds(role, key);
    }

    private static void pushStream(Level level, @Nullable Player player, Vec3 orb) {
        if (player == null) {
            return;
        }
        Vec3 from = palms(player).add((level.random.nextDouble() - 0.5) * 0.2, (level.random.nextDouble() - 0.5) * 0.15,
                (level.random.nextDouble() - 0.5) * 0.2);
        Vec3 velocity = orb.subtract(from).scale(0.08);
        level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
    }

    /** The colour of a send of ours: the key we pressed for it (a press on cooldown sends nothing). */
    private static ParticleOptions ownSend() {
        return lastSendFeint ? OWN_FEINT : OWN_BURST;
    }

    private static void endGather(BlockPos pos) {
        Iterator<Gather> iterator = GATHERS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().pos.equals(pos)) {
                iterator.remove();
            }
        }
    }

    /** {@code count} particles from {@code from} toward {@code to}, at {@code speed} with some {@code spread}. */
    private static void stream(Level level, ParticleOptions type, Vec3 from, Vec3 to, int count, double speed,
                               double spread) {
        Vec3 dir = to.subtract(from).normalize();
        for (int i = 0; i < count; i++) {
            double s = speed * (0.6 + level.random.nextDouble() * 0.6);
            level.addParticle(type, from.x, from.y, from.z,
                    dir.x * s + (level.random.nextDouble() - 0.5) * spread,
                    dir.y * s + (level.random.nextDouble() - 0.5) * spread,
                    dir.z * s + (level.random.nextDouble() - 0.5) * spread);
        }
    }

    private static void puff(Level level, ParticleOptions type, Vec3 at, int count, double speed) {
        for (int i = 0; i < count; i++) {
            level.addParticle(type, at.x, at.y, at.z, (level.random.nextDouble() - 0.5) * speed * 2,
                    (level.random.nextDouble() - 0.5) * speed * 2, (level.random.nextDouble() - 0.5) * speed * 2);
        }
    }

    /** {@code count} particles on a flat ring of {@code radius} around {@code at}. */
    private static void ring(Level level, ParticleOptions type, Vec3 at, double radius, int count) {
        double start = level.random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            double angle = start + Math.PI * 2.0 * i / count;
            level.addParticle(type, at.x + Math.cos(angle) * radius, at.y, at.z + Math.sin(angle) * radius,
                    0.0, 0.0, 0.0);
        }
    }

    private static void sound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static Vec3 palms(Player player) {
        return player.getEyePosition().add(player.getViewVector(1.0F).scale(PALM_REACH)).add(0.0, -PALM_DROP, 0.0);
    }

    private static Vec3 chest(Player player) {
        return player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
    }

    private static boolean inRange(Minecraft mc, BlockPos pos) {
        return mc.player != null && mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_DISTANCE_SQR;
    }

    private QiEffects() {
    }
}
