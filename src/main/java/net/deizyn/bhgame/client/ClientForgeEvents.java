package net.deizyn.bhgame.client;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.client.pose.ClientPoses;
import net.deizyn.bhgame.client.render.station.qi_station.QiEffects;
import net.deizyn.bhgame.client.render.station.wooden_dummy.DummyEffects;
import net.deizyn.bhgame.client.render.carry.water_yoke.YokeVisuals;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.C2SAbort;
import net.deizyn.bhgame.network.C2SCarryPlace;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ScreenEvent;
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
        if (event.phase == TickEvent.Phase.START) {
            keepHandsEmpty(Minecraft.getInstance());
            return;
        }
        ClientSession.tickClock();
        ClientPoses.tick(Minecraft.getInstance());
        NearbySessions.tick(Minecraft.getInstance());
        QiEffects.tick(Minecraft.getInstance());
        DummyEffects.tick(Minecraft.getInstance());
        ClientSession session = ClientSession.get();
        LocalPlayer player = Minecraft.getInstance().player;
        if (session == null) {
            return;
        }
        if (player == null) {
            ClientSession.end();
            return;
        }
        if (session.profile.camera().locksBody()) {
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
            // a carried object is on the player's shoulders, not where it was picked up
            ClientPacketHandler.playCueSound(session.profile.freeMovement() ? player.blockPosition() : session.pos,
                    opening.data().visual());
        }
    }

    /**
     * Carrying (D51): both hands stay on the yoke, so the hotbar, swap-hands and inventory keys do nothing, and the
     * drop key puts the yoke down where you look, or in front of you (D56: Shift + RMB was too easy to hit while
     * sneaking and balancing). Runs before vanilla reads the keys this tick; the server also checks the hands.
     */
    private static void keepHandsEmpty(Minecraft mc) {
        ClientSession session = ClientSession.get();
        if (session == null || mc.player == null) {
            return;
        }
        Options options = mc.options;
        if (!session.profile.freeMovement()) {
            // any other session: the drop key is another way to stop training (D64), and drops nothing
            boolean stop = false;
            while (options.keyDrop.consumeClick()) {
                stop = true;
            }
            if (stop) {
                BhGameNetwork.sendToServer(new C2SAbort());
            }
            return;
        }
        boolean putDown = false;
        while (options.keyDrop.consumeClick()) {
            putDown = true;
        }
        if (putDown) {
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BhGameNetwork.sendToServer(new C2SCarryPlace(hit.getBlockPos(), hit.getDirection()));
            } else {
                BhGameNetwork.sendToServer(new C2SAbort());
            }
        }
        while (options.keySwapOffhand.consumeClick()) {
            // swallowed
        }
        while (options.keyInventory.consumeClick()) {
            // swallowed
        }
        for (KeyMapping hotbar : options.keyHotbarSlots) {
            while (hotbar.consumeClick()) {
                // swallowed
            }
        }
        // F5 while carrying: vanilla's own toggle never got through, so we take the presses ourselves (counted in
        // onKey) and drop vanilla's clicks so it cannot toggle a second time
        while (options.keyTogglePerspective.consumeClick()) {
            // counted from the key itself instead
        }
        for (; perspectivePresses > 0; perspectivePresses--) {
            CameraType before = options.getCameraType();
            options.setCameraType(before.cycle());
            if (before.isFirstPerson() != options.getCameraType().isFirstPerson()) {
                mc.gameRenderer.checkEntityPostEffect(options.getCameraType().isFirstPerson()
                        ? mc.getCameraEntity() : null);
            }
            mc.levelRenderer.needsUpdate();
        }
    }

    /** F5 presses seen while carrying, applied at the start of the next tick. */
    private static int perspectivePresses;

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        ClientSession session = ClientSession.get();
        if (session != null && session.profile.freeMovement() && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        ClientSession session = ClientSession.get();
        if (session == null) {
            return;
        }
        if (session.profile.freeMovement() && event.getNewScreen() instanceof InventoryScreen) {
            event.setCanceled(true);
        } else if (event.getNewScreen() instanceof PauseScreen && Minecraft.getInstance().isWindowActive()
                && Minecraft.getInstance().screen == null) {
            // Esc during a session stops training instead of opening the menu (D64); a pause from losing focus
            // (alt-tab) still opens the menu and keeps the session
            event.setCanceled(true);
            BhGameNetwork.sendToServer(new C2SAbort());
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
        int keyCode = event.getKey();
        int scanCode = event.getScanCode();
        boolean press = action == GLFW.GLFW_PRESS;
        ClientSession carried = ClientSession.get();
        if (press && mc.screen == null && carried != null && carried.profile.freeMovement()
                && mc.options.keyTogglePerspective.matches(keyCode, scanCode)) {
            perspectivePresses = Math.min(perspectivePresses + 1, 3);
        }
        if (!ClientSession.isActive() || mc.screen != null || action == GLFW.GLFW_REPEAT) {
            return;
        }
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
        } else if (options.keyShift.matches(keyCode, scanCode)) {
            key = InputKey.SNEAK;
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
        ClientSession session = ClientSession.get();
        // carried games walk, sprint, sneak and jump as usual once the object is up (D48-D50)
        if (session == null || (session.profile.freeMovement() && !session.isFocusing())) {
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
        NearbySessions.clear();
    }

    /** Respawn or dimension change: entity ids are no longer valid. */
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        ClientPoses.clear();
        NearbySessions.clear();
    }

    // lowest priority and skip when cancelled, so Pre and Post always come in pairs for the pose-stack push/pop
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        ClientPoses.onRenderPre(event);
    }

    /** water_yoke: the yoke and buckets across the bottom of the view instead of bare hands (BODY channel, D51). */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ClientPoses.isCarrying(player)) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            YokeVisuals.renderFirstPerson(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    player, event.getPartialTick());
        }
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

    static float cameraYaw(ClientSession session, LocalPlayer player, float partialTick) {
        Vec3 d = lookDelta(session, player, partialTick);
        float targetYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.rotLerp(turnProgress(session, partialTick), session.startYaw, targetYaw);
    }

    static float cameraPitch(ClientSession session, LocalPlayer player, float partialTick) {
        Vec3 d = lookDelta(session, player, partialTick);
        float targetPitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        return Mth.lerp(turnProgress(session, partialTick), session.startPitch, targetPitch);
    }

    private ClientForgeEvents() {
    }
}
