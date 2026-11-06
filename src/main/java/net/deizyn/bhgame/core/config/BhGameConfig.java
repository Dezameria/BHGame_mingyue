package net.deizyn.bhgame.core.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Server and client config (docs/multiplayer-performance.md section 9). Values are copied into plain static fields on
 * load/reload so hot paths never touch the spec. The defaults below are what runs before the config is loaded.
 */
public final class BhGameConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.IntValue LEAD_TICKS;
    private static final ForgeConfigSpec.IntValue MAX_PING_GRACE_MS;
    private static final ForgeConfigSpec.IntValue INPUT_RATE_LIMIT;
    private static final ForgeConfigSpec.DoubleValue MAX_DISTANCE;
    private static final ForgeConfigSpec.IntValue MAX_CONCURRENT_SESSIONS;
    private static final ForgeConfigSpec.IntValue PARTNER_WAIT_TICKS;
    private static final ForgeConfigSpec.IntValue GAUGE_RESYNC_TICKS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_STRESS_COMMAND;
    private static final ForgeConfigSpec.IntValue SPARRING_BATON_PERMISSION;
    private static final ForgeConfigSpec.IntValue SPARRING_COMMAND_PERMISSION;

    private static final ForgeConfigSpec.BooleanValue SHOW_KEY_HINTS;
    private static final ForgeConfigSpec.IntValue POSE_ANIMATION_DISTANCE;

    // server
    public static int leadTicks = 6;
    public static int maxPingGraceMs = 250;
    public static int inputRateLimit = 20;
    public static double maxDistance = 6.0;
    public static int maxConcurrentSessions = 0;
    public static int partnerWaitTicks = 600;
    public static int gaugeResyncTicks = 20;
    public static boolean enableStressCommand = false;
    public static int sparringBatonPermission = 2;
    public static int sparringCommandPermission = 2;

    // client
    public static boolean showKeyHints = true;
    public static int poseAnimationDistance = 32;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        server.push("timing");
        LEAD_TICKS = server.comment("Cues reach the client at least this many ticks before their window opens.")
                .defineInRange("leadTicks", 6, 2, 40);
        MAX_PING_GRACE_MS = server.comment("Cue windows stay open this much longer for high-ping players, at most.")
                .defineInRange("maxPingGraceMs", 250, 0, 1000);
        GAUGE_RESYNC_TICKS = server.comment("While a gauge moves, resend it this often so the client can't drift.")
                .defineInRange("gaugeResyncTicks", 20, 5, 200);
        server.pop();
        server.push("limits");
        INPUT_RATE_LIMIT = server.comment("Input events per second per player; extra ones are dropped.")
                .defineInRange("inputRateLimit", 20, 5, 100);
        MAX_DISTANCE = server.comment("A session is cancelled if a player ends up farther than this from the block.")
                .defineInRange("maxDistance", 6.0, 2.0, 16.0);
        MAX_CONCURRENT_SESSIONS = server.comment("0 = no limit.")
                .defineInRange("maxConcurrentSessions", 0, 0, 10000);
        PARTNER_WAIT_TICKS = server.comment("How long a multi-player game (twin_grass) waits for the partner.")
                .defineInRange("partnerWaitTicks", 600, 100, 6000);
        server.pop();
        server.push("sparring");
        SPARRING_BATON_PERMISSION = server.comment("Permission level needed to use the sparring baton (D74). "
                        + "2 = same as /bhgame sparring, 0 = anyone holding the baton.")
                .defineInRange("batonPermissionLevel", 2, 0, 4);
        SPARRING_COMMAND_PERMISSION = server.comment("Permission level needed to set a sparring dummy's event commands "
                        + "(baton screen or /bhgame sparring ... command). The commands run at level 2, like a command "
                        + "block, so this is never below 2.")
                .defineInRange("commandPermissionLevel", 2, 2, 4);
        server.pop();
        server.push("dev");
        ENABLE_STRESS_COMMAND = server.comment("Allow /bhgame stress outside the dev environment. Keep off on a live server.")
                .define("enableStressCommand", false);
        server.pop();
        SERVER_SPEC = server.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        SHOW_KEY_HINTS = client.comment("Small key hints at the bottom of the screen during training.")
                .define("showKeyHints", true);
        POSE_ANIMATION_DISTANCE = client.comment("Other players' training poses are animated only within this range.")
                .defineInRange("poseAnimationDistance", 32, 8, 128);
        CLIENT_SPEC = client.build();
    }

    public static void onConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() == SERVER_SPEC) {
            leadTicks = LEAD_TICKS.get();
            maxPingGraceMs = MAX_PING_GRACE_MS.get();
            inputRateLimit = INPUT_RATE_LIMIT.get();
            maxDistance = MAX_DISTANCE.get();
            maxConcurrentSessions = MAX_CONCURRENT_SESSIONS.get();
            partnerWaitTicks = PARTNER_WAIT_TICKS.get();
            gaugeResyncTicks = GAUGE_RESYNC_TICKS.get();
            enableStressCommand = ENABLE_STRESS_COMMAND.get();
            sparringBatonPermission = SPARRING_BATON_PERMISSION.get();
            sparringCommandPermission = SPARRING_COMMAND_PERMISSION.get();
        } else if (config.getSpec() == CLIENT_SPEC) {
            showKeyHints = SHOW_KEY_HINTS.get();
            poseAnimationDistance = POSE_ANIMATION_DISTANCE.get();
        }
    }

    /** {@code /bhgame stress} works in the dev environment, or anywhere when the config allows it. */
    public static boolean stressAllowed() {
        return enableStressCommand || !FMLEnvironment.production;
    }

    private BhGameConfig() {
    }
}
