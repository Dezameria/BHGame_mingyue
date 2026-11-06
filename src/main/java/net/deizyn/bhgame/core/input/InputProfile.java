package net.deizyn.bhgame.core.input;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Per-game input rules the client follows: which keys are captured, which ones also report release,
 * whether the camera is locked, and what gets sent. See docs/design/core-mechanics.md section 3.
 * {@code freeMovement}: walking, sprinting, sneaking and jumping stay vanilla (water_yoke only, D48-D50).
 * {@link Camera#FREE_LOOK}: the body stays turned to the object like LOCKED, but the mouse turns the camera alone (D66).
 */
public record InputProfile(int keys, int holdKeys, Camera camera, boolean crosshair, boolean sendIdlePresses,
                           boolean aimPayload, boolean freeMovement) {

    public enum Camera {
        LOCKED, FREE, FREE_LOOK;

        /** Whether the player's own rotation is held on the object. */
        public boolean locksBody() {
            return this != FREE;
        }
    }

    private static final int FLAG_FREE_CAMERA = 1;
    private static final int FLAG_CROSSHAIR = 2;
    private static final int FLAG_IDLE_PRESSES = 4;
    private static final int FLAG_AIM = 8;
    private static final int FLAG_FREE_MOVEMENT = 16;
    private static final int FLAG_FREE_LOOK = 32;

    public static InputProfile locked(InputKey... keys) {
        return new InputProfile(InputKey.mask(keys), 0, Camera.LOCKED, false, false, false, false);
    }

    public InputProfile hold(InputKey... keys) {
        return new InputProfile(this.keys, InputKey.mask(keys), camera, crosshair, sendIdlePresses, aimPayload,
                freeMovement);
    }

    public InputProfile withIdlePresses() {
        return new InputProfile(keys, holdKeys, camera, crosshair, true, aimPayload, freeMovement);
    }

    /** Body held on the object, camera free to look around (D66). */
    public InputProfile freeLook() {
        return new InputProfile(keys, holdKeys, Camera.FREE_LOOK, crosshair, sendIdlePresses, aimPayload, freeMovement);
    }

    public InputProfile freeAim() {
        return new InputProfile(keys, holdKeys, Camera.FREE, true, sendIdlePresses, true, freeMovement);
    }

    /** Free camera, crosshair on, and the player walks around normally (a carried object, D48). */
    public InputProfile carried() {
        return new InputProfile(keys, holdKeys, Camera.FREE, true, sendIdlePresses, aimPayload, true);
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
                | (aimPayload ? FLAG_AIM : 0)
                | (freeMovement ? FLAG_FREE_MOVEMENT : 0)
                | (camera == Camera.FREE_LOOK ? FLAG_FREE_LOOK : 0);
        buf.writeByte(flags);
    }

    public static InputProfile read(FriendlyByteBuf buf) {
        int keys = buf.readUnsignedByte();
        int holdKeys = buf.readUnsignedByte();
        int flags = buf.readUnsignedByte();
        return new InputProfile(keys, holdKeys,
                (flags & FLAG_FREE_LOOK) != 0 ? Camera.FREE_LOOK
                        : (flags & FLAG_FREE_CAMERA) != 0 ? Camera.FREE : Camera.LOCKED,
                (flags & FLAG_CROSSHAIR) != 0,
                (flags & FLAG_IDLE_PRESSES) != 0,
                (flags & FLAG_AIM) != 0,
                (flags & FLAG_FREE_MOVEMENT) != 0);
    }
}
