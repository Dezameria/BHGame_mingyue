package net.deizyn.bhgame.network;

import net.deizyn.bhgame.game.mob.sparring_dummy.SparringBatonItem;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringCommands;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringSettings;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;

import java.util.function.Supplier;

/**
 * "Save" in the sparring baton's screen (D74, D75). The server checks permission, distance, every animation id and the
 * inventory slots again ({@link SparringBatonItem#apply}); nothing the client sends is trusted.
 * <p>
 * {@code gear} has one byte per {@link SparringStats#GEAR_SLOTS}: {@link #GEAR_KEEP}, {@link #GEAR_CLEAR}, or the
 * index of a slot in the player's own inventory to copy the item from (0-40, {@code Inventory#getItem}).
 * {@code commands} is null to leave them alone; the server ignores it without the command permission (D79).
 * {@code name} is null when the screen didn't change the name or its visibility, so a name set another way (JSON
 * colours by {@code /data}) survives a save.
 */
public record C2SSparringApply(int entityId, SparringSettings settings, SparringStats stats, byte[] gear,
                               Target target, @Nullable SparringCommands commands, @Nullable String name,
                               boolean nameAlwaysShown) {
    public static final byte GEAR_KEEP = -1;
    public static final byte GEAR_CLEAR = -2;

    /** What the screen's target button ended on. KEEP leaves the dummy's target alone. */
    public enum Target {KEEP, NEAREST, NONE, ME}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        settings.encode(buf);
        stats.encode(buf);
        for (int i = 0; i < SparringStats.GEAR_SLOTS.length; i++) {
            buf.writeByte(i < gear.length ? gear[i] : GEAR_KEEP);
        }
        buf.writeByte(target.ordinal());
        buf.writeBoolean(commands != null);
        if (commands != null) {
            commands.encode(buf);
        }
        buf.writeBoolean(name != null);
        if (name != null) {
            buf.writeUtf(SparringDummy.cleanName(name), SparringDummy.MAX_NAME * 4);
        }
        buf.writeBoolean(nameAlwaysShown);
    }

    public static C2SSparringApply decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        SparringSettings settings = SparringSettings.decode(buf);
        SparringStats stats = SparringStats.decode(buf);
        byte[] gear = new byte[SparringStats.GEAR_SLOTS.length];
        for (int i = 0; i < gear.length; i++) {
            gear[i] = buf.readByte();
        }
        Target target = SparringSettings.readEnum(buf, Target.class);
        SparringCommands commands = buf.readBoolean() ? SparringCommands.decode(buf) : null;
        String name = buf.readBoolean() ? buf.readUtf(SparringDummy.MAX_NAME * 4) : null;
        return new C2SSparringApply(entityId, settings, stats, gear, target, commands, name, buf.readBoolean());
    }

    public static void handle(C2SSparringApply message, Supplier<NetworkEvent.Context> context) {
        BhGameNetwork.countReceived();
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            SparringBatonItem.apply(player, message);
        }
    }
}
