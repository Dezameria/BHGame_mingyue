package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.C2SSparringApply;
import net.deizyn.bhgame.network.S2COpenSparring;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sparring baton (D74): right click a sparring dummy to open its settings screen; shift + right click two dummies to
 * make them spar each other. Same permission as {@code /bhgame sparring} unless the server config lowers it. The
 * screen and its messages are in English (D75).
 */
public class SparringBatonItem extends Item {
    /** The first dummy picked with shift + right click, waiting for its partner. */
    private static final String TAG_PAIR = "PairWith";
    /** The screen's "Save" is refused when the player has walked this far from the dummy. */
    private static final double MAX_APPLY_DISTANCE = 48D;
    /** Inventory#getItem covers the 36 main slots, 4 armour slots and the offhand. */
    private static final int INVENTORY_SLOTS = 41;

    public SparringBatonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof SparringDummy dummy)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS; // swing on the client; the server decides
        }
        if (!allowed(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("Only staff can use the sparring baton")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        // in creative, Player.interactOn hands us a copy of the stack: write the pair to the real one
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            pair(serverPlayer, held, dummy);
        } else {
            open(serverPlayer, dummy);
        }
        return InteractionResult.CONSUME;
    }

    private static void open(ServerPlayer player, SparringDummy dummy) {
        SparringDummy.TargetMode mode = dummy.targetMode();
        String name = "";
        boolean you = false;
        if (mode == SparringDummy.TargetMode.FIXED && dummy.fixedTarget() != null) {
            you = dummy.fixedTarget().equals(player.getUUID());
            Entity entity = ((ServerLevel) dummy.level()).getEntity(dummy.fixedTarget());
            name = entity != null ? entity.getDisplayName().getString() + describeDummy(entity) : "not in this world";
        }
        List<ItemStack> gear = new ArrayList<>();
        for (EquipmentSlot slot : SparringStats.GEAR_SLOTS) {
            gear.add(dummy.getItemBySlot(slot));
        }
        boolean commands = mayEditCommands(player);
        BhGameNetwork.sendTo(player, new S2COpenSparring(dummy.getId(), SparringSettings.of(dummy),
                SparringStats.of(dummy), gear, mode, name, you,
                commands ? dummy.commands() : SparringCommands.NONE, commands, dummy.editableName(),
                dummy.getCustomName() == null || dummy.isCustomNameVisible()));
    }

    private static String describeDummy(Entity entity) {
        return entity instanceof SparringDummy ? " #" + entity.getId() : "";
    }

    private static void pair(ServerPlayer player, ItemStack baton, SparringDummy dummy) {
        CompoundTag tag = baton.getTag();
        UUID first = tag != null && tag.hasUUID(TAG_PAIR) ? tag.getUUID(TAG_PAIR) : null;
        if (first == null || first.equals(dummy.getUUID())) {
            if (first != null) {
                baton.removeTagKey(TAG_PAIR);
                player.displayClientMessage(Component.literal("Pairing cancelled"), true);
                return;
            }
            baton.getOrCreateTag().putUUID(TAG_PAIR, dummy.getUUID());
            player.displayClientMessage(Component.literal("Picked sparring dummy #" + dummy.getId()
                    + ": shift + right click another one to make them fight"), true);
            return;
        }
        Entity other = ((ServerLevel) dummy.level()).getEntity(first);
        if (!(other instanceof SparringDummy partner) || !partner.isAlive()) {
            baton.getOrCreateTag().putUUID(TAG_PAIR, dummy.getUUID());
            player.displayClientMessage(Component.literal("The first dummy is gone; picked #" + dummy.getId()
                    + " instead"), true);
            return;
        }
        baton.removeTagKey(TAG_PAIR);
        partner.targetFixed(dummy);
        dummy.targetFixed(partner);
        player.displayClientMessage(Component.literal("Sparring dummies #" + partner.getId() + " and #"
                + dummy.getId() + " now fight each other (change it with Target in the settings)"), true);
    }

    /** Server side of the screen's "Save". */
    public static void apply(ServerPlayer player, C2SSparringApply message) {
        if (!allowed(player)) {
            return;
        }
        Entity entity = player.level().getEntity(message.entityId());
        if (!(entity instanceof SparringDummy dummy) || !dummy.isAlive()
                || dummy.distanceToSqr(player) > MAX_APPLY_DISTANCE * MAX_APPLY_DISTANCE) {
            player.displayClientMessage(Component.literal("That dummy is too far away or gone; nothing was saved")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        SparringSettings sent = message.settings();
        SparringSettings settings = sent.filterMoves(move -> EpicFightCompat.animationExists(move.animation()));
        int dropped = sent.moveCount() - settings.moveCount();
        settings.applyTo(dummy);
        message.stats().applyTo(dummy);
        applyGear(player, dummy, message.gear());
        if (message.name() != null) {
            dummy.setEditableName(message.name(), message.nameAlwaysShown());
        }
        boolean commandsRefused = false;
        if (message.commands() != null) {
            if (mayEditCommands(player)) {
                dummy.setCommands(message.commands());
            } else {
                commandsRefused = true;
            }
        }
        switch (message.target()) {
            case NEAREST -> dummy.targetNearestPlayer();
            case NONE -> dummy.targetNobody();
            case ME -> dummy.targetFixed(player);
            case KEEP -> {
            }
        }
        player.displayClientMessage(Component.literal("Saved sparring dummy #" + dummy.getId()
                + (dummy.getCustomName() != null ? " (" + dummy.getCustomName().getString() + "\u00a7r)" : "")
                + (dropped > 0 ? " (skipped " + dropped + " move(s) Epic Fight doesn't know)" : "")
                + (commandsRefused ? " (commands need permission level " + BhGameConfig.sparringCommandPermission
                + ")" : "")), true);
    }

    /** Gear is copied from the player's own inventory, read here on the server; the dummy never drops it. */
    private static void applyGear(ServerPlayer player, SparringDummy dummy, byte[] gear) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < SparringStats.GEAR_SLOTS.length && i < gear.length; i++) {
            byte action = gear[i];
            if (action == C2SSparringApply.GEAR_CLEAR) {
                dummy.setItemSlot(SparringStats.GEAR_SLOTS[i], ItemStack.EMPTY);
            } else if (action >= 0 && action < INVENTORY_SLOTS) {
                ItemStack copy = inventory.getItem(action).copy();
                copy.setCount(Math.min(copy.getCount(), 1));
                dummy.setItemSlot(SparringStats.GEAR_SLOTS[i], copy);
            }
        }
    }

    private static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(BhGameConfig.sparringBatonPermission);
    }

    /** Event commands run at level 2, so setting them needs at least that (D79). */
    public static boolean mayEditCommands(ServerPlayer player) {
        return player.hasPermissions(Math.max(SparringDummy.COMMAND_PERMISSION,
                BhGameConfig.sparringCommandPermission));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Right click a sparring dummy: settings").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Shift + right click two dummies: make them fight")
                .withStyle(ChatFormatting.GRAY));
        if (stack.getTag() != null && stack.getTag().hasUUID(TAG_PAIR)) {
            lines.add(Component.literal("First dummy picked").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().hasUUID(TAG_PAIR);
    }
}
