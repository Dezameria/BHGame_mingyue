package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.pattern.Motion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * What a carried session holds while its block is out of the world (D48): the block as it was, its admin NBT, the
 * pose byte (water levels) at the start and now, and the carrier's sampled movement. Server only.
 */
public final class CarryState {
    final BlockState state;
    final CompoundTag blockEntityTag;
    final int startData;
    int data;
    Vec3 lastPos;
    Motion motion = Motion.STILL;
    long lastJumpTick = Long.MIN_VALUE / 2;
    boolean placed;
    boolean restoreEpicFight;
    /** Share of the carrier's speed taken away now (D70). */
    float slow;

    /** The carry slowdown is our own speed modifier, so it never touches slowness from potions or other games. */
    private static final UUID SLOW_ID = UUID.fromString("6f2b9c1e-4d7a-4b8e-9a35-2c1f0e7d5b40");

    CarryState(BlockState state, CompoundTag blockEntityTag, int data, Vec3 pos) {
        this.state = state;
        this.blockEntityTag = blockEntityTag;
        this.startData = data;
        this.data = data;
        this.lastPos = pos;
    }

    /**
     * Sets the carry slowdown on {@code player} (0 removes it). Transient, so it is never saved with the player; the
     * attribute reaches their client only when it changes.
     */
    static void applySlow(ServerPlayer player, float share) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOW_ID);
        if (share > 0.0F) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, "bhgame carry", -share,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public int startData() {
        return startData;
    }

    public int data() {
        return data;
    }
}
