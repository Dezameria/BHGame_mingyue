package net.deizyn.bhgame.core.input;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Per-game input rules the client follows: which keys are captured, which ones also report release,
 * whether the camera is locked, and what gets sent. See docs/design/core-mechanics.md section 3.
 */
public record InputProfile(int keys, int holdKeys, Camera camera, boolean crosshair, boolean sendIdlePresses,
                           boolean aimPayload) {

    public enum Camera { LOCKED, FREE }

    private static final int FLAG_FREE_CAMERA = 1;
    private static final int FLAG_CROSSHAIR = 2;
    private static final int FLAG_IDLE_PRESSES = 4;
    private static final int FLAG_AIM = 8;

    public static InputProfile locked(InputKey... keys) {
        return new InputProfile(InputKey.mask(keys), 0, Camera.LOCKED, false, false, false);
    }

    public InputProfile hold(InputKey... keys) {
        return new InputProfile(this.keys, InputKey.mask(keys), camera, crosshair, sendIdlePresses, aimPayload);
    }

    public InputProfile withIdlePresses() {
        return new InputProfile(keys, holdKeys, camera, crosshair, true, aimPayload);
    }

    public InputProfile freeAim() {
        return new InputProfile(keys, holdKeys, Camera.FREE, true, sendIdlePresses, true);
    }

    public boolean captures(InputKey key) {
        return (keys & key.bit()) != 0;
    }

    public boolean isHold(InputKey key) {
        return (holdKeys & key.bit()) != 0;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(keys);
        buf.writeByte(holdKeys);
        int flags = (camera == Camera.FREE ? FLAG_FREE_CAMERA : 0)
                | (crosshair ? FLAG_CROSSHAIR : 0)
                | (sendIdlePresses ? FLAG_IDLE_PRESSES : 0)
                | (aimPayload ? FLAG_AIM : 0);
        buf.writeByte(flags);
    }

    public static InputProfile read(FriendlyByteBuf buf) {
        int keys = buf.readUnsignedByte();
        int holdKeys = buf.readUnsignedByte();
        int flags = buf.readUnsignedByte();
        return new InputProfile(keys, holdKeys,
                (flags & FLAG_FREE_CAMERA) != 0 ? Camera.FREE : Camera.LOCKED,
                (flags & FLAG_CROSSHAIR) != 0,
                (flags & FLAG_IDLE_PRESSES) != 0,
                (flags & FLAG_AIM) != 0);
    }
}
