package net.deizyn.bhgame.core.outcome;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Writes every finished session to the log and the bhg_* scoreboard objectives (core-mechanics section 12). */
public final class Stats {

    public static void record(ServerPlayer player, String gameId, Outcome outcome, int mistakes, long ticks, BlockPos pos,
                              boolean bruteForce) {
        BhGame.LOGGER.info("[bhgame] {} {} {}{} mistakes={} time={}s at {}", player.getGameProfile().getName(), gameId,
                outcome, bruteForce ? " (brute force)" : "", mistakes, ticks / 20.0, pos.toShortString());
        String objectiveName = switch (outcome) {
            case PERFECT -> "bhg_perfect";
            case SUCCESS -> "bhg_success";
            case FAIL -> "bhg_fail";
            case ABORTED -> null;
        };
        if (objectiveName == null) {
            return;
        }
        Scoreboard scoreboard = player.getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            objective = scoreboard.addObjective(objectiveName, ObjectiveCriteria.DUMMY, Component.literal(objectiveName),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).add(1);
    }

    /** Raises {@code player}'s score on {@code objectiveName} to {@code value} if it is higher (e.g. best stage). */
    public static void recordBest(ServerPlayer player, String objectiveName, int value) {
        BhGame.LOGGER.info("[bhgame] {} {} = {}", player.getGameProfile().getName(), objectiveName, value);
        Scoreboard scoreboard = player.getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            objective = scoreboard.addObjective(objectiveName, ObjectiveCriteria.DUMMY, Component.literal(objectiveName),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
        if (value > score.getScore()) {
            score.setScore(value);
        }
    }

    private Stats() {
    }
}
