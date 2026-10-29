package net.deizyn.bhgame.client;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.client.pose.ClientPoses;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.C2SAbort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Locks the player into the session: movement, jumping, attacking and using are blocked, the captured keys are
 * forwarded, the camera turns to the object and stays there, and most of the vanilla HUD is hidden.
 */
@Mod.EventBusSubscriber(modid = BhGame.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final int CAMERA_TURN_TICKS = 10;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientSession.tickClock();
        ClientPoses.tick(Minecraft.getInstance());
        ClientSession session = ClientSession.get();
        LocalPlayer player = Minecraft.getInstance().player;
        if (session == null) {
            return;
        }
        if (player == null) {
            ClientSession.end();
            return;
        }
        if (session.profile.camera() == InputProfile.Camera.LOCKED) {
            // keep the real rotation on the target too, so the server sees where the player faces
            float yaw = cameraYaw(session, player, 0.0F);
            float pitch = cameraPitch(session, player, 0.0F);
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
        }
        ClientSession.LocalCue opening = session.takeOpeningSoundCue();
        if (opening != null) {
            ClientPacketHandler.playCueSound(session.pos, opening.data().visual());
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientSession.blocksWorldInput() || mc.screen != null) {
            return;
        }
        event.setCanceled(true);
        int action = event.getAction();
        if (!ClientSession.isActive() || (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE)) {
            return;
        }
        InputKey key = null;
        if (mc.options.keyAttack.matchesMouse(event.getButton())) {
            key = InputKey.LMB;
        } else if (mc.options.keyUse.matchesMouse(event.getButton())) {
            key = InputKey.RMB;
        }
        if (key != null) {
            InputCapture.handle(key, action == GLFW.GLFW_PRESS);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        int action = event.getAction();
        if (!ClientSession.isActive() || mc.screen != null || action == GLFW.GLFW_REPEAT) {
            return;
        }
        int keyCode = event.getKey();
        int scanCode = event.getScanCode();
        boolean press = action == GLFW.GLFW_PRESS;
        if (press && ModKeyMappings.ABORT.matches(keyCode, scanCode)) {
            BhGameNetwork.sendToServer(new C2SAbort());
            return;
        }
        Options options = mc.options;
        InputKey key = null;
        if (options.keyJump.matches(keyCode, scanCode)) {
            key = InputKey.SPACE;
        } else if (options.keyUp.matches(keyCode, scanCode)) {
            key = InputKey.UP;
        } else if (options.keyLeft.matches(keyCode, scanCode)) {
            key = InputKey.LEFT;
        } else if (options.keyDown.matches(keyCode, scanCode)) {
            key = InputKey.DOWN;
        } else if (options.keyRight.matches(keyCode, scanCode)) {
            key = InputKey.RIGHT;
        }
        if (key != null) {
            InputCapture.handle(key, press);
        }
    }

    /** Backup for attack/use triggered any other way (keyboard binds, held clicks). */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientSession.blocksWorldInput()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientSession.isActive()) {
            return;
        }
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientSession session = ClientSession.get();
        LocalPlayer player = Minecraft.getInstance().player;
        if (session == null || player == null || session.profile.camera() != InputProfile.Camera.LOCKED
                || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        event.setYaw(cameraYaw(session, player, partialTick));
        event.setPitch(cameraPitch(session, player, partialTick));
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        ClientSession session = ClientSession.get();
        if (session == null) {
            return;
        }
        NamedGuiOverlay overlay = event.getOverlay();
        if (is(overlay, VanillaGuiOverlay.HOTBAR) || is(overlay, VanillaGuiOverlay.EXPERIENCE_BAR)
                || is(overlay, VanillaGuiOverlay.ITEM_NAME)
                || (!session.profile.crosshair() && is(overlay, VanillaGuiOverlay.CROSSHAIR))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSession.end();
        ClientPoses.clear();
    }

    /** Respawn or dimension change: entity ids are no longer valid. */
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        ClientPoses.clear();
    }

    // lowest priority and skip when cancelled, so Pre and Post always come in pairs for the pose-stack push/pop
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        ClientPoses.onRenderPre(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        ClientPoses.onRenderPost(event);
    }

    private static boolean is(NamedGuiOverlay overlay, VanillaGuiOverlay vanilla) {
        return overlay.id().equals(vanilla.id());
    }

    private static float turnProgress(ClientSession session, float partialTick) {
        float t = Mth.clamp((ClientSession.clock() + partialTick - session.startTick) / CAMERA_TURN_TICKS, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static Vec3 lookDelta(ClientSession session, LocalPlayer player, float partialTick) {
        Vec3 target = Vec3.atBottomCenterOf(session.pos).add(0.0, session.lookHeight, 0.0);
        return target.subtract(player.getEyePosition(partialTick));
    }

    private static float cameraYaw(ClientSession session, LocalPlayer player, float partialTick) {
        Vec3 d = lookDelta(session, player, partialTick);
        float targetYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.rotLerp(turnProgress(session, partialTick), session.startYaw, targetYaw);
    }

    private static float cameraPitch(ClientSession session, LocalPlayer player, float partialTick) {
        Vec3 d = lookDelta(session, player, partialTick);
        float targetPitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        return Mth.lerp(turnProgress(session, partialTick), session.startPitch, targetPitch);
    }

    private ClientForgeEvents() {
    }
}
