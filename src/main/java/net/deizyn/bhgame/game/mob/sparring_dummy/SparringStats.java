package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Body stats the sparring baton's screen edits (D75): base values of vanilla attributes, so {@code /attribute} sees the
 * same numbers, plus invulnerability. Saving refills HP.
 */
public record SparringStats(float maxHealth, float attackDamage, float armor, float moveSpeed, boolean invulnerable) {
    public static final float MAX_HEALTH = 1024F;
    public static final float MAX_DAMAGE = 512F;
    public static final float MAX_ARMOR = 30F;
    public static final float MAX_MOVE_SPEED = 1F;

    /** Order of the screen's gear slots. */
    public static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public SparringStats {
        maxHealth = clamp(maxHealth, 1F, MAX_HEALTH, 40F);
        attackDamage = clamp(attackDamage, 0F, MAX_DAMAGE, 4F);
        armor = clamp(armor, 0F, MAX_ARMOR, 0F);
        moveSpeed = clamp(moveSpeed, 0F, MAX_MOVE_SPEED, 0.3F);
    }

    private static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public static SparringStats of(SparringDummy dummy) {
        return new SparringStats(base(dummy, Attributes.MAX_HEALTH), base(dummy, Attributes.ATTACK_DAMAGE),
                base(dummy, Attributes.ARMOR), base(dummy, Attributes.MOVEMENT_SPEED), dummy.isInvulnerable());
    }

    private static float base(SparringDummy dummy, Attribute attribute) {
        AttributeInstance instance = dummy.getAttribute(attribute);
        return instance != null ? (float) instance.getBaseValue() : 0F;
    }

    public void applyTo(SparringDummy dummy) {
        setBase(dummy, Attributes.MAX_HEALTH, maxHealth);
        setBase(dummy, Attributes.ATTACK_DAMAGE, attackDamage);
        setBase(dummy, Attributes.ARMOR, armor);
        setBase(dummy, Attributes.MOVEMENT_SPEED, moveSpeed);
        dummy.setInvulnerable(invulnerable);
        dummy.setHealth(dummy.getMaxHealth());
    }

    private static void setBase(SparringDummy dummy, Attribute attribute, float value) {
        AttributeInstance instance = dummy.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(maxHealth);
        buf.writeFloat(attackDamage);
        buf.writeFloat(armor);
        buf.writeFloat(moveSpeed);
        buf.writeBoolean(invulnerable);
    }

    public static SparringStats decode(FriendlyByteBuf buf) {
        return new SparringStats(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }
}
