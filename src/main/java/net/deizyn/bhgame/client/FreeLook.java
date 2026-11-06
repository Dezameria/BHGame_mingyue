package net.deizyn.bhgame.client;

import net.deizyn.bhgame.core.input.InputProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * {@link InputProfile.Camera#FREE_LOOK} (D66): the player's body stays turned to the object, as with a locked camera,
 * while the mouse turns only the camera, in first and third person. Mouse turns are kept as an offset from the
 * locked direction, so the camera still follows the turn-in at the start. Called from the client mixins.
 */
public final class FreeLook {

    /**
     * The session that looks freely, or null. A {@link InputProfile.Camera#LOCKED} session looks freely in third
     * person only (D77): F5 lets the mouse orbit the camera while the body stays on the object, and back in first
     * person the view snaps onto the object again (the offset is dropped, so the next F5 starts from behind).
     */
    @Nullable
    private static ClientSession active() {
        ClientSession session = ClientSession.get();
        if (session == null) {
            return null;
        }
        InputProfile.Camera camera = session.profile.camera();
        if (camera == InputProfile.Camera.LOCKED) {
            if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                session.lookYawOffset = 0.0F;
                session.lookPitchOffset = 0.0F;
                return null;
            }
            return session;
        }
        return camera == InputProfile.Camera.FREE_LOOK ? session : null;
    }

    /**
     * A mouse turn of {@code entity} (raw deltas as {@code Entity.turn} gets them). Returns true when it was taken
     * for the camera, so the body must not turn.
     */
    public static boolean turn(Entity entity, double yRot, double xRot) {
        ClientSession session = active();
        LocalPlayer player = Minecraft.getInstance().player;
        if (session == null || entity != player) {
            return false;
        }
        // the same scale vanilla uses in Entity.turn
        session.lookYawOffset = Mth.wrapDegrees(session.lookYawOffset + (float) yRot * 0.15F);
        float base = ClientForgeEvents.cameraPitch(session, player, 0.0F);
        session.lookPitchOffset = Mth.clamp(session.lookPitchOffset + (float) xRot * 0.15F, -90.0F - base,
                90.0F - base);
        return true;
    }

    /** The camera's yaw for {@code entity} this frame, or {@code vanilla} when it is not looking freely. */
    public static float yaw(Entity entity, float partialTick, float vanilla) {
        ClientSession session = active();
        LocalPlayer player = Minecraft.getInstance().player;
        if (session == null || entity != player) {
            return vanilla;
        }
        return ClientForgeEvents.cameraYaw(session, player, partialTick) + session.lookYawOffset;
    }

    public static float pitch(Entity entity, float partialTick, float vanilla) {
        ClientSession session = active();
        LocalPlayer player = Minecraft.getInstance().player;
        if (session == null || entity != player) {
            return vanilla;
        }
        return Mth.clamp(ClientForgeEvents.cameraPitch(session, player, partialTick) + session.lookPitchOffset, -90.0F,
                90.0F);
    }

    private FreeLook() {
    }
}
