package net.deizyn.bhgame.client;

import net.deizyn.bhgame.client.pose.ClientPoses;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.C2SInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Filters captured key edges by the session's input profile before anything is sent
 * (docs/multiplayer-performance.md section 2): releases only for hold keys, idle presses only if the game wants them.
 * Hold keys are always sent, since the server tracks whether they are down.
 */
final class InputCapture {

    static void handle(InputKey key, boolean press) {
        ClientSession session = ClientSession.get();
        if (session == null || session.isFocusing() || !session.profile.captures(key)) {
            return;
        }
        boolean hold = session.profile.isHold(key);
        if (!press && !hold) {
            return;
        }
        if (press) {
            // instant local feedback; the HIT/MISS result arrives from the server about one ping later
            if (session.channel == CueChannel.WORLD_ARROW) {
                localPunch(session, key);
            } else if (!hold) {
                ClientPacketHandler.playLocal(session.pos, SoundEvents.GRASS_HIT, 0.5F, 1.2F);
            }
            if (!hold && !session.profile.sendIdlePresses() && session.openCue(ClientSession.clock()) == null) {
                return;
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (session.profile.aimPayload() && player != null) {
            BhGameNetwork.sendToServer(C2SInput.aimed(key, press, player.getYRot(), player.getXRot()));
        } else {
            BhGameNetwork.sendToServer(C2SInput.of(key, press));
        }
    }

    /**
     * The wooden dummy: our own punch, hit effect and sound play the moment the key goes down (D42); the server
     * sends the same punch only to the players around us.
     */
    private static void localPunch(ClientSession session, InputKey key) {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientSession.LocalPress result = session.onLocalPress(key);
        PlayerAction action = switch (result) {
            // the row index of the arrow just pressed picks the hand, like the server's onStep
            case STEP -> (session.sequenceProgress() - 1) % 2 == 0 ? PlayerAction.PUNCH_RIGHT : PlayerAction.PUNCH_LEFT;
            case FINISHER -> PlayerAction.PUNCH_FINISH;
            case WRONG -> session.sequenceBroken() ? PlayerAction.KNOCKED_BACK : null;
            case NONE -> null;
        };
        if (action != null && player != null) {
            ClientPoses.action(player.getId(), action, action == PlayerAction.KNOCKED_BACK ? null : key);
        }
        if (result == ClientSession.LocalPress.WRONG || result == ClientSession.LocalPress.NONE) {
            ClientPacketHandler.playLocal(session.pos, SoundEvents.WOOD_STEP, 0.6F, 0.7F);
        }
    }

    private InputCapture() {
    }
}
