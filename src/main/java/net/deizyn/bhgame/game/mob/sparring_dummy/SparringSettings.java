package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the sparring baton's screen edits about how the dummy fights (D74, D75): the default moves, the
 * conditional patterns, pause, chase and play speed, and when to stop (D79). Body stats are in {@link SparringStats}; the target is picked
 * separately.
 */
public record SparringSettings(List<SparringMove> moves, SparringDummy.Order order, float attackRange,
                               List<SparringPattern> patterns, int pauseTicks, SparringDummy.ChaseMode chaseMode,
                               float runSpeed, float animationSpeed, float stopBelow) {

    public SparringSettings {
        moves = List.copyOf(moves.subList(0, Math.min(moves.size(), SparringDummy.MAX_MOVES)));
        attackRange = SparringDummy.clampRange(attackRange);
        patterns = List.copyOf(patterns.subList(0, Math.min(patterns.size(), SparringPattern.MAX_PATTERNS)));
        pauseTicks = Math.max(0, Math.min(SparringDummy.MAX_PAUSE, pauseTicks));
        runSpeed = Float.isFinite(runSpeed)
                ? Math.max(SparringDummy.MIN_RUN_SPEED, Math.min(SparringDummy.MAX_RUN_SPEED, runSpeed)) : 1.5F;
        animationSpeed = SparringMove.clampSpeed(animationSpeed);
        stopBelow = SparringDummy.clampStopBelow(stopBelow);
    }

    public static SparringSettings of(SparringDummy dummy) {
        return new SparringSettings(dummy.moves(), dummy.order(), dummy.attackRange(), dummy.patterns(),
                dummy.pauseTicks(), dummy.chaseMode(), dummy.runSpeed(), dummy.animationSpeed(),
                dummy.stopBelow());
    }

    public void applyTo(SparringDummy dummy) {
        dummy.setMoves(moves);
        dummy.setOrder(order);
        dummy.setAttackRange(attackRange);
        dummy.setPatterns(patterns);
        dummy.setPauseTicks(pauseTicks);
        dummy.setChaseMode(chaseMode);
        dummy.setRunSpeed(runSpeed);
        dummy.setAnimationSpeed(animationSpeed);
        dummy.setStopBelow(stopBelow);
    }

    public void encode(FriendlyByteBuf buf) {
        encodeMoves(buf, moves);
        buf.writeByte(order.ordinal());
        buf.writeFloat(attackRange);
        buf.writeVarInt(patterns.size());
        patterns.forEach(pattern -> pattern.encode(buf));
        buf.writeVarInt(pauseTicks);
        buf.writeByte(chaseMode.ordinal());
        buf.writeFloat(runSpeed);
        buf.writeFloat(animationSpeed);
        buf.writeFloat(stopBelow);
    }

    /** Throws on lists longer than the limits, so a bad client can't make us read forever. */
    public static SparringSettings decode(FriendlyByteBuf buf) {
        List<SparringMove> moves = decodeMoves(buf);
        SparringDummy.Order order = readEnum(buf, SparringDummy.Order.class);
        float range = buf.readFloat();
        int count = buf.readVarInt();
        if (count < 0 || count > SparringPattern.MAX_PATTERNS) {
            throw new IllegalArgumentException("too many sparring patterns: " + count);
        }
        List<SparringPattern> patterns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            patterns.add(SparringPattern.decode(buf));
        }
        int pause = buf.readVarInt();
        SparringDummy.ChaseMode chase = readEnum(buf, SparringDummy.ChaseMode.class);
        return new SparringSettings(moves, order, range, patterns, pause, chase, buf.readFloat(), buf.readFloat(),
                buf.readFloat());
    }

    static void encodeMoves(FriendlyByteBuf buf, List<SparringMove> moves) {
        buf.writeVarInt(moves.size());
        moves.forEach(move -> move.encode(buf));
    }

    static List<SparringMove> decodeMoves(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > SparringDummy.MAX_MOVES) {
            throw new IllegalArgumentException("too many sparring moves: " + count);
        }
        List<SparringMove> moves = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            moves.add(SparringMove.decode(buf));
        }
        return moves;
    }

    public static <E extends Enum<E>> E readEnum(FriendlyByteBuf buf, Class<E> type) {
        E[] values = type.getEnumConstants();
        return values[Math.floorMod(buf.readByte(), values.length)];
    }

    /** The same settings with every move list filtered (the server drops unknown animation ids). */
    public SparringSettings filterMoves(java.util.function.Predicate<SparringMove> keep) {
        List<SparringPattern> filtered = new ArrayList<>();
        for (SparringPattern pattern : patterns) {
            filtered.add(new SparringPattern(pattern.condition(), pattern.value(), pattern.order(), pattern.range(),
                    pattern.moves().stream().filter(keep).toList()));
        }
        return new SparringSettings(moves.stream().filter(keep).toList(), order, attackRange, filtered, pauseTicks,
                chaseMode, runSpeed, animationSpeed, stopBelow);
    }

    public int moveCount() {
        int count = moves.size();
        for (SparringPattern pattern : patterns) {
            count += pattern.moves().size();
        }
        return count;
    }
}
