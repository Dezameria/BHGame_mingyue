package net.deizyn.bhgame.client;

import net.deizyn.bhgame.core.cue.CueResult;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.session.TrainingPins;
import net.deizyn.bhgame.network.S2CCue;
import net.deizyn.bhgame.network.S2CGaugeSync;
import net.deizyn.bhgame.network.S2CSessionBanner;
import net.minecraft.network.chat.Component;
import net.deizyn.bhgame.network.S2CSessionEnd;
import net.deizyn.bhgame.network.S2CSessionStart;
import net.deizyn.bhgame.network.S2CSessionUpdate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Client side of the session packets. Only loaded on the client (through DistExecutor). */
public final class ClientPacketHandler {

    public static void onSessionStart(S2CSessionStart message) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // the right click that started the session is still "down"; clear it so nothing stays held
        KeyMapping.releaseAll();
        ClientSession.start(message.pos(), message.gameId(), message.profile(), message.maxHp(), message.focusTicks(),
                message.role(), message.players(), message.waiting(), message.lookHeight(), message.channel(),
                player.getYRot(), player.getXRot());
        // Epic Fight's punches must not carry us forward while we train (D44)
        TrainingPins.setClientPinned(player.getUUID());
    }

    public static void onCues(S2CCue message) {
        ClientSession session = ClientSession.get();
        if (session != null) {
            session.addCues(message.cues());
        }
    }

    public static void onUpdate(S2CSessionUpdate message) {
        ClientSession session = ClientSession.get();
        if (session == null) {
            return;
        }
        session.setHp(message.hp());
        session.onResult(message.cueId(), message.result());
        if (message.result() == CueResult.HIT) {
            burst(session.pos, ParticleTypes.HAPPY_VILLAGER, 6);
        } else if (message.result() == CueResult.MISS) {
            burst(session.pos, ParticleTypes.WITCH, 8);
        }
    }

    public static void onGauge(S2CGaugeSync message) {
        ClientSession session = ClientSession.get();
        if (session != null) {
            session.setGauge(message.index(), message.flags(), message.value(), message.ratePerTick());
        }
    }

    public static void onBanner(S2CSessionBanner message) {
        ClientSession session = ClientSession.get();
        if (session != null) {
            session.showBanner(Component.translatable(message.translationKey(), message.value()));
            playLocal(session.pos, SoundEvents.BELL_BLOCK, 0.5F, 1.2F);
        }
    }

    public static void onSessionEnd(S2CSessionEnd message) {
        ClientSession.end();
        KeyMapping.releaseAll();
    }

    /** Feedback particles, spawned on this client only. */
    private static void burst(BlockPos pos, ParticleOptions particle, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.2 + mc.level.random.nextDouble() * 0.6;
            double y = pos.getY() + 0.1 + mc.level.random.nextDouble() * 0.6;
            double z = pos.getZ() + 0.2 + mc.level.random.nextDouble() * 0.6;
            mc.level.addParticle(particle, x, y, z, 0.0, 0.02, 0.0);
        }
    }

    static void playLocal(BlockPos pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
                    volume, pitch, false);
        }
    }

    /** Placeholder cue sounds, heard by the player only (P6 replaces them with the real ones). */
    static void playCueSound(BlockPos pos, CueVisual visual) {
        switch (visual) {
            case YIN -> playLocal(pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
            case YANG -> playLocal(pos, SoundEvents.FIRECHARGE_USE, 0.35F, 1.5F);
            // decoys sound the same: only the colour tells them apart
            case SPARK, SPARK_FAKE -> playLocal(pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25F, 2.0F);
            default -> playLocal(pos, SoundEvents.WARDEN_HEARTBEAT, 0.9F, 1.0F);
        }
    }

    private ClientPacketHandler() {
    }
}
