package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * One move of the sparring dummy: an Epic Fight animation id, the same id {@code /epicfight animator play} takes, its
 * transition time modifier (D73) and a play speed multiplier on top of the dummy's own (D75).
 */
public record SparringMove(ResourceLocation animation, float transition, float speed) {
    public static final float MIN_SPEED = 0.1F;
    public static final float MAX_SPEED = 5F;
    public static final float MAX_TRANSITION = 10F;

    private static final String TAG_ANIMATION = "Anim";
    private static final String TAG_TRANSITION = "Transition";
    private static final String TAG_SPEED = "Speed";

    public SparringMove {
        transition = Float.isFinite(transition) ? Math.max(-MAX_TRANSITION, Math.min(MAX_TRANSITION, transition)) : 0F;
        speed = clampSpeed(speed);
    }

    public SparringMove(ResourceLocation animation, float transition) {
        this(animation, transition, 1F);
    }

    public static float clampSpeed(float speed) {
        return Float.isFinite(speed) ? Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed)) : 1F;
    }

    public SparringMove withTransition(float newTransition) {
        return new SparringMove(animation, newTransition, speed);
    }

    public SparringMove withSpeed(float newSpeed) {
        return new SparringMove(animation, transition, newSpeed);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ANIMATION, animation.toString());
        if (transition != 0F) {
            tag.putFloat(TAG_TRANSITION, transition);
        }
        if (speed != 1F) {
            tag.putFloat(TAG_SPEED, speed);
        }
        return tag;
    }

    @Nullable
    public static SparringMove load(CompoundTag tag) {
        ResourceLocation animation = ResourceLocation.tryParse(tag.getString(TAG_ANIMATION));
        if (animation == null) {
            return null;
        }
        return new SparringMove(animation, tag.getFloat(TAG_TRANSITION),
                tag.contains(TAG_SPEED) ? tag.getFloat(TAG_SPEED) : 1F);
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(animation);
        buf.writeFloat(transition);
        buf.writeFloat(speed);
    }

    static SparringMove decode(FriendlyByteBuf buf) {
        return new SparringMove(buf.readResourceLocation(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(animation.toString());
        if (speed != 1F) {
            text.append(" x").append(String.format(Locale.ROOT, "%.2f", speed));
        }
        if (transition != 0F) {
            text.append(" (transition ").append(transition).append(')');
        }
        return text.toString();
    }
}
