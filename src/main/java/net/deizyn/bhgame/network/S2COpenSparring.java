package net.deizyn.bhgame.network;

import net.deizyn.bhgame.game.mob.sparring_dummy.SparringCommands;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringSettings;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Right click on a sparring dummy with the baton: open its settings screen (D74, D75). Sent only to the player who
 * clicked, once per click. {@code gear} follows {@link SparringStats#GEAR_SLOTS}; {@code targetName} is set only for a
 * fixed target. {@code commands} is sent only to players who may edit them (D79); others get none. {@code name} uses
 * {@code &} colour codes ({@link SparringDummy#editableName}).
 */
public record S2COpenSparring(int entityId, SparringSettings settings, SparringStats stats, List<ItemStack> gear,
                              SparringDummy.TargetMode targetMode, String targetName, boolean targetIsYou,
                              SparringCommands commands, boolean canEditCommands, String name,
                              boolean nameAlwaysShown) {
    private static final int MAX_NAME = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        settings.encode(buf);
        stats.encode(buf);
        for (int i = 0; i < SparringStats.GEAR_SLOTS.length; i++) {
            buf.writeItem(i < gear.size() ? gear.get(i) : ItemStack.EMPTY);
        }
        buf.writeByte(targetMode.ordinal());
        buf.writeUtf(targetName.length() > MAX_NAME ? targetName.substring(0, MAX_NAME) : targetName, MAX_NAME * 4);
        buf.writeBoolean(targetIsYou);
        commands.encode(buf);
        buf.writeBoolean(canEditCommands);
        buf.writeUtf(SparringDummy.cleanName(name), SparringDummy.MAX_NAME * 4);
        buf.writeBoolean(nameAlwaysShown);
    }

    public static S2COpenSparring decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        SparringSettings settings = SparringSettings.decode(buf);
        SparringStats stats = SparringStats.decode(buf);
        List<ItemStack> gear = new ArrayList<>();
        for (int i = 0; i < SparringStats.GEAR_SLOTS.length; i++) {
            gear.add(buf.readItem());
        }
        return new S2COpenSparring(entityId, settings, stats, gear,
                SparringSettings.readEnum(buf, SparringDummy.TargetMode.class), buf.readUtf(MAX_NAME * 4),
                buf.readBoolean(), SparringCommands.decode(buf), buf.readBoolean(),
                buf.readUtf(SparringDummy.MAX_NAME * 4), buf.readBoolean());
    }

    public static void handle(S2COpenSparring message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onOpenSparring(message));
    }
}
