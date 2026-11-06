package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A conditional move set of the sparring dummy (D75). Before each move the dummy goes down its patterns in order and
 * uses the first one whose condition holds and whose range reaches the target; if none does, it uses its default
 * moves ({@code SparringDummy#moves}).
 */
public record SparringPattern(Condition condition, float value, SparringDummy.Order order, float range,
                              List<SparringMove> moves) {
    public static final int MAX_PATTERNS = 8;

    public enum Condition {
        TARGET_FARTHER("Target farther than", "blocks", 5F, 0F, 32F),
        TARGET_CLOSER("Target closer than", "blocks", 2F, 0F, 32F),
        HEALTH_BELOW("My HP below", "%", 50F, 0F, 100F),
        TARGET_HEALTH_BELOW("Target HP below", "%", 30F, 0F, 100F),
        CHANCE("Chance", "%", 30F, 0F, 100F),
        WAS_HIT("Got hit in the last", "s", 2F, 0F, 30F);

        public final String label;
        public final String unit;
        public final float defaultValue;
        public final float min;
        public final float max;

        Condition(String label, String unit, float defaultValue, float min, float max) {
            this.label = label;
            this.unit = unit;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
        }

        public float clamp(float value) {
            return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : defaultValue;
        }
    }

    public SparringPattern {
        value = condition.clamp(value);
        range = SparringDummy.clampRange(range);
        moves = List.copyOf(moves.subList(0, Math.min(moves.size(), SparringDummy.MAX_MOVES)));
    }

    public static SparringPattern create(Condition condition) {
        return new SparringPattern(condition, condition.defaultValue, SparringDummy.Order.SEQUENCE, 2.5F, List.of());
    }

    /**
     * Whether the condition holds now. {@code roll} is in [0, 1), drawn again after every move, so a chance pattern is
     * decided once per move and not every tick.
     */
    boolean test(SparringDummy dummy, LivingEntity target, double distance, float roll) {
        return switch (condition) {
            case TARGET_FARTHER -> distance > value;
            case TARGET_CLOSER -> distance < value;
            case HEALTH_BELOW -> dummy.getHealth() < dummy.getMaxHealth() * value / 100F;
            case TARGET_HEALTH_BELOW -> target.getHealth() < target.getMaxHealth() * value / 100F;
            case CHANCE -> roll * 100F < value;
            case WAS_HIT -> dummy.getLastHurtByMob() != null
                    && dummy.tickCount - dummy.getLastHurtByMobTimestamp() <= value * 20F;
        };
    }

    public String describe() {
        return condition.label + " " + format(value) + " " + condition.unit;
    }

    public static String format(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    // ---- NBT and network -------------------------------------------------------------------------------------------

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("If", condition.name().toLowerCase(Locale.ROOT));
        tag.putFloat("Value", value);
        tag.putString("Order", order.name().toLowerCase(Locale.ROOT));
        tag.putFloat("Range", range);
        ListTag list = new ListTag();
        moves.forEach(move -> list.add(move.save()));
        tag.put("Moves", list);
        return tag;
    }

    static SparringPattern load(CompoundTag tag) {
        Condition condition = SparringDummy.parse(Condition.class, tag.getString("If"), Condition.CHANCE);
        List<SparringMove> moves = new ArrayList<>();
        ListTag list = tag.getList("Moves", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            SparringMove move = SparringMove.load(list.getCompound(i));
            if (move != null) {
                moves.add(move);
            }
        }
        return new SparringPattern(condition, tag.contains("Value") ? tag.getFloat("Value") : condition.defaultValue,
                SparringDummy.parse(SparringDummy.Order.class, tag.getString("Order"), SparringDummy.Order.SEQUENCE),
                tag.contains("Range") ? tag.getFloat("Range") : 2.5F, moves);
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeByte(condition.ordinal());
        buf.writeFloat(value);
        buf.writeByte(order.ordinal());
        buf.writeFloat(range);
        SparringSettings.encodeMoves(buf, moves);
    }

    static SparringPattern decode(FriendlyByteBuf buf) {
        Condition condition = SparringSettings.readEnum(buf, Condition.class);
        float value = buf.readFloat();
        SparringDummy.Order order = SparringSettings.readEnum(buf, SparringDummy.Order.class);
        float range = buf.readFloat();
        return new SparringPattern(condition, value, order, range, SparringSettings.decodeMoves(buf));
    }
}
