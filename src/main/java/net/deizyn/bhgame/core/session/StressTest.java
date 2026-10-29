package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /bhgame stress <n>}: keeps n simulated sessions running so their server CPU cost can be measured with spark
 * (docs/multiplayer-performance.md section 8). Bots press a random captured key every 0.4-1.4 s, which gives a mix of
 * hits and misses, so sessions end and get replaced like real ones. Nothing goes over the network; would-be packets
 * are only counted.
 */
public final class StressTest {
    /** Far below the world, so a real block can never share a position with a simulated session. */
    private static final int VIRTUAL_Y = -10000;

    private static int target;
    @Nullable
    private static TrainingBlock game;
    private static int nextPos;
    private static long started;
    private static long finished;

    public static void start(MinecraftServer server, TrainingBlock stressGame, int count) {
        game = stressGame;
        target = count;
        fill(server);
    }

    public static void stop() {
        target = 0;
        for (TrainingSession session : SessionManager.sessions().toArray(TrainingSession[]::new)) {
            if (session.simulated()) {
                SessionManager.finish(session, Outcome.ABORTED, null, null);
            }
        }
    }

    public static int running() {
        int count = 0;
        for (TrainingSession session : SessionManager.sessions()) {
            if (session.simulated()) {
                count++;
            }
        }
        return count;
    }

    public static String statsLine() {
        return "stress: target " + target + ", running " + running() + ", started " + started + ", finished " + finished;
    }

    static void reset() {
        target = 0;
        game = null;
    }

    static void botTick(TrainingSession session, long now) {
        if (now >= session.botNextPress()) {
            Participant bot = session.participants().get(session.random().nextInt(session.participants().size()));
            InputKey key = randomKey(session, bot);
            if (key != null) {
                SessionManager.botInput(session, bot, key, now);
            }
            session.setBotNextPress(now + 8 + session.random().nextInt(20));
        }
        if (!session.isFinished()) {
            session.scheduleAt(session.botNextPress());
        }
    }

    static void onFinished(TrainingSession session) {
        finished++;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && target > 0) {
            fill(server);
        }
    }

    private static void fill(MinecraftServer server) {
        if (game == null) {
            return;
        }
        for (int running = running(); running < target; running++) {
            SessionManager.startSimulated(server, game, new BlockPos(nextPos++, VIRTUAL_Y, 0));
            started++;
        }
    }

    @Nullable
    private static InputKey randomKey(TrainingSession session, Participant bot) {
        int keys = bot.profile().keys();
        int count = Integer.bitCount(keys);
        if (count == 0) {
            return null;
        }
        int pick = session.random().nextInt(count);
        for (InputKey key : InputKey.values()) {
            if ((keys & key.bit()) != 0 && pick-- == 0) {
                return key;
            }
        }
        return null;
    }

    private StressTest() {
    }
}
