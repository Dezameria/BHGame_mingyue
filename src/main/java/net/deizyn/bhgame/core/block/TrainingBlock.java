package net.deizyn.bhgame.core.block;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.session.Outcome;
import net.deizyn.bhgame.core.session.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Base of every minigame block: right-click starts a session, removal aborts it.
 * Subclasses describe the game (input profile, pattern, penalties) and what a win or a loss does to the block.
 */
public abstract class TrainingBlock extends BaseEntityBlock {
    private String gameId;

    protected TrainingBlock(Properties properties) {
        super(properties);
    }

    public final String gameId() {
        if (gameId == null) {
            gameId = ForgeRegistries.BLOCKS.getKey(this).getPath();
        }
        return gameId;
    }

    public ResourceLocation defaultLootTable(boolean perfect) {
        return BhGame.id("minigames/" + gameId() + (perfect ? "/perfect" : "/normal"));
    }

    public abstract InputProfile inputProfile();

    /** Input rules for one role; games with several players give each role its own keys (D31). */
    public InputProfile inputProfile(int role) {
        return inputProfile();
    }

    /**
     * How many players must join before the game begins (at most {@code Tuning.MAX_PARTICIPANTS}). The first one
     * waits for the rest (WAITING).
     */
    public int requiredPlayers() {
        return 1;
    }

    /** Created once every participant has joined. */
    public abstract Pattern createPattern(PatternContext ctx);

    public abstract int defaultMaxHp();

    public int defaultMaxMistakes() {
        return 3;
    }

    /**
     * How close the player's feet must be (horizontally, to the block centre) to start. Much shorter than vanilla
     * reach: in the story you have to walk up and put your hands on the thing.
     */
    public double startReach() {
        return 3.0;
    }

    /** Feet may be at most this many blocks above or below the block's base. */
    public double startHeightTolerance() {
        return 1.5;
    }

    /**
     * When positive, a starting player is moved to this many blocks from the block's centre, on whichever of the four
     * sides (N/E/S/W) is nearest and free, facing it (D42). 0 = stay where you clicked.
     */
    public double standDistance() {
        return 0.0;
    }

    /**
     * A face-off (PvP, D58): with {@link #standDistance()} set, the second player is snapped to the side opposite the
     * first instead of the nearest one.
     */
    public boolean facesOff() {
        return false;
    }

    /**
     * Whether players are put in Epic Fight's battle mode for the session (and back to vanilla mode afterwards if that
     * is where they were), with the mode key locked meanwhile (D66). Otherwise they are put in vanilla mode and drawn
     * by the vanilla model, and get battle mode back at the end (D78). Nothing happens without Epic Fight.
     */
    public boolean forcesEpicFightMode() {
        return false;
    }

    /** Where this game shows its cues; tells the client which renderer to use. */
    public CueChannel channel() {
        return CueChannel.BLOCK;
    }

    /** Where the camera turns to at the start, in blocks above the block's base (herbs: near the soil). */
    public float lookHeight() {
        return 0.35F;
    }

    public abstract Penalty smallPenalty();

    public abstract Penalty bigPenalty();

    /**
     * The sound of a one-shot action ({@code PatternContext.action}) for everyone in earshot except {@code player},
     * whose client played it on the key press (D66). Throttled with the action. Default: silent.
     */
    public void playActionSound(ServerLevel level, ServerPlayer player, PlayerAction action) {
    }

    /** What bystanders see the player doing during this game. */
    public abstract PlayerPose pose();

    /** Translation key of the in-story line shown when a session starts. */
    public String hintKey() {
        return "bhgame." + gameId() + ".hint";
    }

    /** Shown to the first player of a multi-player game while waiting for the others. */
    public String waitingHintKey() {
        return "bhgame." + gameId() + ".waiting";
    }

    /** Translation key of the in-story result line shown in the action bar. */
    public String outcomeKey(Outcome outcome) {
        return "bhgame.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** {@code players} are the participants still online (may be empty). */
    public abstract void onWin(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players,
                               Outcome outcome, TrainingBlockEntity blockEntity);

    /** {@code penalty} (normally {@link #bigPenalty()}) goes to every participant in {@code players}. */
    public abstract void onFail(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players,
                                Penalty penalty, TrainingBlockEntity blockEntity);

    /** A participant walked out of a multi-player game after it began (D32); they already got the big penalty. */
    public void onAbandoned(ServerLevel level, BlockPos pos, BlockState state, TrainingBlockEntity blockEntity) {
    }

    /** Whether removing the block ends a session on it (not for carried blocks, which leave the world on purpose). */
    protected boolean abortsSessionOnRemove() {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrainingBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()
                && level.getBlockEntity(pos) instanceof TrainingBlockEntity blockEntity) {
            SessionManager.tryStart(serverPlayer, (ServerLevel) level, pos, this, blockEntity);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel && abortsSessionOnRemove()) {
            SessionManager.abortAt(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
