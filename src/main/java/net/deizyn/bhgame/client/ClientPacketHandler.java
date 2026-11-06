package net.deizyn.bhgame.client;

import net.deizyn.bhgame.client.render.station.qi_station.QiEffects;
import net.deizyn.bhgame.client.render.station.wooden_dummy.DummyEffects;
import net.deizyn.bhgame.game.station.wooden_dummy.WoodenDummyBlock;
import net.deizyn.bhgame.client.render.carry.water_yoke.YokeVisuals;
import net.deizyn.bhgame.core.cue.CueResult;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.session.TrainingPins;
import net.deizyn.bhgame.network.S2CCue;
import net.deizyn.bhgame.network.S2CGaugeSync;
import net.deizyn.bhgame.network.S2CNearbyState;
import net.deizyn.bhgame.network.S2CSessionBanner;
import net.minecraft.network.chat.Component;
import net.deizyn.bhgame.network.S2CSessionEnd;
import net.deizyn.bhgame.network.S2CSessionStart;
import net.deizyn.bhgame.network.S2CSessionUpdate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Client side of the session packets. Only loaded on the client (through DistExecutor). */
public final class ClientPacketHandler {
    /** Packets older than this (half of it, really) are not moved forward any further. */
    private static final int MAX_LEAD_MS = 500;

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
        // Epic Fight's punches must not carry us forward while we train (D44); a carrier walks freely
        if (!message.profile().freeMovement()) {
            TrainingPins.setClientPinned(player.getUUID());
        }
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
        if (session.gameId.equals("qi_station")) {
            // the orb's own effects show every verdict (QiEffects, D60)
            return;
        }
        if (session.profile.freeMovement()) {
            // a carried object: the feedback is the yoke itself, not particles where it was picked up
            YokeVisuals.onResult(message.cueId(), message.result());
            return;
        }
        if (message.result() == CueResult.HIT) {
            burst(session.pos, ParticleTypes.HAPPY_VILLAGER, 6);
        } else if (message.result() == CueResult.MISS) {
            burst(session.pos, ParticleTypes.WITCH, 8);
        }
    }

    public static void onNearby(S2CNearbyState message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockState(message.pos()).getBlock() instanceof WoodenDummyBlock) {
            // the dummy has no public state to watch, only its stage-up bursts (D66)
            DummyEffects.onEvent(message.pos(), message.event(), message.data());
            return;
        }
        NearbySessions.State state = NearbySessions.onState(message);
        if (state != null && message.event() != S2CNearbyState.SYNC) {
            QiEffects.onEvent(state, message.event(), message.role(), message.data());
        }
    }

    public static void onGauge(S2CGaugeSync message) {
        ClientSession session = ClientSession.get();
        if (session != null) {
            session.setGauge(message.index(), message.flags(), message.value(), message.ratePerTick(),
                    message.keyRate(), oneWayTicks());
        }
    }

    /** Half our ping in ticks: how old a packet from the server is when it arrives. */
    private static float oneWayTicks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return 0.0F;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0.0F : Math.min(Math.max(info.getLatency(), 0), MAX_LEAD_MS) / 100.0F;
    }

    public static void onBanner(S2CSessionBanner message) {
        ClientSession session = ClientSession.get();
        if (session != null) {
            session.showBanner(Component.translatable(message.translationKey(), message.value()));
            playLocal(session.pos, SoundEvents.BELL_BLOCK, 0.5F, 1.2F);
        }
    }

    /** The sparring baton's settings screen (D74). */
    public static void onOpenSparring(net.deizyn.bhgame.network.S2COpenSparring message) {
        Minecraft.getInstance().setScreen(new net.deizyn.bhgame.client.screen.SparringScreen(message));
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
            // the yoke creaks as it starts to dip; a gust only rustles
            case TILT_LEFT, TILT_RIGHT -> playLocal(pos, SoundEvents.WOOD_STEP, 0.7F, 0.6F);
            case RIPPLE -> playLocal(pos, SoundEvents.AZALEA_LEAVES_STEP, 0.6F, 1.2F);
            // a feint hums the same as a real burst: only its colour gives it away
            case QI_BURST, QI_FEINT -> playLocal(pos, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
            default -> playLocal(pos, SoundEvents.WARDEN_HEARTBEAT, 0.9F, 1.0F);
        }
    }

    private ClientPacketHandler() {
    }
}
