package net.deizyn.bhgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.session.Participant;
import net.deizyn.bhgame.core.session.SessionManager;
import net.deizyn.bhgame.core.session.StressTest;
import net.deizyn.bhgame.core.session.TrainingSession;
import net.deizyn.bhgame.game.carry.water_yoke.YokePrototype;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.registry.Minigames;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Admin commands (permission level 2). list/abort/stats/reset come in P5. */
public final class BhGameCommand {
    private static final int MAX_LISTED = 20;
    private static final int MAX_STRESS = 2000;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bhgame")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug").executes(BhGameCommand::debug))
                .then(SparringCommand.build())
                .then(Commands.literal("yoke").executes(BhGameCommand::yokeToggle)
                        .then(Commands.argument("left", IntegerArgumentType.integer(0, 3))
                                .then(Commands.argument("right", IntegerArgumentType.integer(0, 3))
                                        .executes(BhGameCommand::yokeWater))))
                .then(Commands.literal("stress")
                        .then(Commands.literal("stop").executes(BhGameCommand::stressStop))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_STRESS))
                                .executes(context -> stress(context, "pulse_grass"))
                                .then(Commands.argument("game", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Minigames.all().stream().map(block -> block.getId().getPath()), builder))
                                        .executes(context -> stress(context,
                                                StringArgumentType.getString(context, "game")))))));
    }

    /** Active sessions and packet counters (docs/multiplayer-performance.md section 8). */
    private static int debug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<TrainingSession> sessions = SessionManager.sessions();
        long now = source.getServer().getTickCount();
        int simulated = StressTest.running();
        source.sendSuccess(() -> Component.literal("[bhgame] sessions: " + sessions.size()
                + (simulated > 0 ? " (" + simulated + " simulated)" : "") + " | " + BhGameNetwork.statsLine()), false);
        int listed = 0;
        for (TrainingSession session : sessions) {
            if (session.simulated()) {
                continue;
            }
            if (listed++ >= MAX_LISTED) {
                source.sendSuccess(() -> Component.literal("  ..."), false);
                break;
            }
            StringBuilder names = new StringBuilder();
            for (Participant participant : session.participants()) {
                ServerPlayer player = session.player(participant);
                if (!names.isEmpty()) {
                    names.append('+');
                }
                names.append(player != null ? player.getGameProfile().getName() : participant.id().toString());
            }
            long nextIn = session.nextEventTick() == Long.MAX_VALUE ? -1 : session.nextEventTick() - now;
            String line = "  #" + session.serial() + " " + names + " " + session.game().gameId() + " @ "
                    + session.pos().toShortString() + " " + session.state() + " hp " + session.hp() + "/"
                    + session.maxHp() + " mistakes " + session.mistakes() + " next event in " + nextIn + "t";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (simulated > 0) {
            source.sendSuccess(() -> Component.literal("  " + StressTest.statsLine()), false);
        }
        return sessions.size();
    }

    /** water_yoke prototype (D51): yoke on your own shoulders, no session. */
    private static int yokeToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (SessionManager.isInSession(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("[bhgame] leave the session first"));
            return 0;
        }
        boolean carrying = YokePrototype.toggle(player);
        context.getSource().sendSuccess(() -> Component.literal(carrying
                ? "[bhgame] yoke on (prototype); set water with /bhgame yoke <left> <right>, remove with /bhgame yoke"
                : "[bhgame] yoke off"), false);
        return 1;
    }

    private static int yokeWater(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (SessionManager.isInSession(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("[bhgame] leave the session first"));
            return 0;
        }
        int left = IntegerArgumentType.getInteger(context, "left");
        int right = IntegerArgumentType.getInteger(context, "right");
        YokePrototype.setWater(player, left, right);
        context.getSource().sendSuccess(() -> Component.literal("[bhgame] yoke water " + left + " / " + right), false);
        return 1;
    }

    private static int stress(CommandContext<CommandSourceStack> context, String gameId) {
        CommandSourceStack source = context.getSource();
        if (!BhGameConfig.stressAllowed()) {
            source.sendFailure(Component.literal("[bhgame] stress is off (server config dev.enableStressCommand)"));
            return 0;
        }
        TrainingBlock game = null;
        for (RegistryObject<? extends TrainingBlock> block : Minigames.all()) {
            if (block.getId().getPath().equals(gameId)) {
                game = block.get();
            }
        }
        if (game == null) {
            source.sendFailure(Component.literal("[bhgame] unknown game " + gameId));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        StressTest.start(source.getServer(), game, count);
        source.sendSuccess(() -> Component.literal("[bhgame] " + count + " simulated " + gameId
                + " sessions; measure with /spark profiler, stop with /bhgame stress stop"), true);
        return count;
    }

    private static int stressStop(CommandContext<CommandSourceStack> context) {
        StressTest.stop();
        context.getSource().sendSuccess(() -> Component.literal("[bhgame] stress stopped"), true);
        return 1;
    }

    private BhGameCommand() {
    }
}
