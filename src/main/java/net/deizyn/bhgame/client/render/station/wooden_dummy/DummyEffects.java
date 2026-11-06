package net.deizyn.bhgame.client.render.station.wooden_dummy;

import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.game.station.wooden_dummy.DummyFx;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What everyone near a wooden dummy sees when its player reaches a new stage (D66, D67): one ring of light per stage
 * reached, climbing the post one after another from the foot, the newest one brightest, in a colour that warms every
 * {@link DummyFx#MILESTONE} stages. Every milestone ends in a special finale that grows with each one: a shockwave on
 * the ground, a pillar of light from the top and a fountain of sparks. Client particles only, from one event packet.
 */
public final class DummyEffects {
    private static final double MAX_DISTANCE_SQR = 48.0 * 48.0;
    /** Ten rings fill the post (about 1.9 blocks); later stages keep ten and show their tier by colour. */
    private static final int MAX_RINGS = 10;
    private static final double FIRST_RING = 0.15;
    private static final double RING_STEP = 0.19;
    private static final double RADIUS = 0.6;
    private static final int RING_POINTS = 16;
    /** Ticks between two rings appearing. */
    private static final int RING_GAP = 2;
    private static final double TOP = 1.9;
    /** Stage colour tiers (stages 1-5, 6-10, 11-15, 16+): white, jade, gold, crimson. */
    private static final Vector3f[] TIERS = {
            new Vector3f(0.95F, 0.95F, 0.9F),
            new Vector3f(0.35F, 0.9F, 0.55F),
            new Vector3f(1.0F, 0.78F, 0.2F),
            new Vector3f(0.95F, 0.2F, 0.2F)
    };

    private record Burst(BlockPos pos, int stage, long start) {
        int rings() {
            return Math.min(stage, MAX_RINGS);
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();

    public static void onEvent(BlockPos pos, int event, int data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event != DummyFx.STAGE_UP
                || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            return;
        }
        // a newer stage on the same dummy replaces the one still climbing
        BURSTS.removeIf(burst -> burst.pos.equals(pos));
        BURSTS.add(new Burst(pos.immutable(), Math.max(1, data), ClientSession.clock()));
    }

    public static void tick(Minecraft mc) {
        Level level = mc.level;
        if (level == null) {
            BURSTS.clear();
            return;
        }
        if (BURSTS.isEmpty() || mc.isPaused()) {
            return;
        }
        long now = ClientSession.clock();
        Iterator<Burst> iterator = BURSTS.iterator();
        while (iterator.hasNext()) {
            Burst burst = iterator.next();
            long elapsed = now - burst.start;
            if (elapsed % RING_GAP != 0) {
                continue;
            }
            int step = (int) (elapsed / RING_GAP);
            if (step < burst.rings()) {
                ring(level, burst, step);
            } else {
                finale(level, burst);
                iterator.remove();
            }
        }
    }

    /** Ring {@code index} from the foot; the last one (the stage just reached) is the brightest. */
    private static void ring(Level level, Burst burst, int index) {
        boolean newest = index == burst.rings() - 1;
        DustParticleOptions dust = new DustParticleOptions(tier(burst.stage), newest ? 1.6F : 1.0F);
        double cx = burst.pos.getX() + 0.5;
        double cz = burst.pos.getZ() + 0.5;
        double y = burst.pos.getY() + FIRST_RING + index * RING_STEP;
        double turn = index * 0.4;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = turn + i * Math.PI * 2.0 / RING_POINTS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.addParticle(dust, cx + dx * RADIUS, y, cz + dz * RADIUS, dx * 0.02, 0.0, dz * 0.02);
            if (newest && i % 2 == 0) {
                level.addParticle(ParticleTypes.END_ROD, cx + dx * RADIUS, y, cz + dz * RADIUS, dx * 0.05, 0.01,
                        dz * 0.05);
            }
        }
    }

    private static void finale(Level level, Burst burst) {
        double cx = burst.pos.getX() + 0.5;
        double cz = burst.pos.getZ() + 0.5;
        double top = burst.pos.getY() + TOP;
        if (burst.stage % DummyFx.MILESTONE != 0) {
            // an ordinary stage: a small crown of sparks on top
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                level.addParticle(ParticleTypes.ENCHANTED_HIT, cx, top, cz, Math.cos(angle) * 0.2, 0.08,
                        Math.sin(angle) * 0.2);
            }
            return;
        }
        // milestone: 1 at stage 5, 2 at stage 10, ... each one bigger than the last
        int milestone = Math.min(burst.stage / DummyFx.MILESTONE, 4);
        double ground = burst.pos.getY() + 0.1;
        int wave = 24 + 8 * milestone;
        for (int i = 0; i < wave; i++) {
            double angle = i * Math.PI * 2.0 / wave;
            double speed = 0.25 + 0.05 * milestone;
            level.addParticle(ParticleTypes.CLOUD, cx, ground, cz, Math.cos(angle) * speed, 0.01,
                    Math.sin(angle) * speed);
        }
        for (int i = 0; i < 6 + 4 * milestone; i++) {
            level.addParticle(ParticleTypes.END_ROD, cx, top + i * 0.15, cz, 0.0, 0.15 + i * 0.01, 0.0);
        }
        ParticleOptions sparks = milestone >= 3 ? ParticleTypes.FIREWORK : ParticleTypes.TOTEM_OF_UNDYING;
        int count = 20 + 15 * milestone;
        for (int i = 0; i < count; i++) {
            double vx = (level.random.nextDouble() - 0.5) * 0.6;
            double vy = 0.2 + level.random.nextDouble() * 0.4;
            double vz = (level.random.nextDouble() - 0.5) * 0.6;
            level.addParticle(sparks, cx, top, cz, vx, vy, vz);
        }
        if (milestone >= 2) {
            level.addParticle(ParticleTypes.FLASH, cx, top, cz, 0.0, 0.0, 0.0);
        }
    }

    private static Vector3f tier(int stage) {
        return TIERS[Math.min(TIERS.length - 1, (stage - 1) / DummyFx.MILESTONE)];
    }

    private DummyEffects() {
    }
}
