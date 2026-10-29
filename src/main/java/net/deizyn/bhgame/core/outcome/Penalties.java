package net.deizyn.bhgame.core.outcome;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Building blocks for penalties. Everything here hurts the player only (D25): bystanders may see and hear the
 * outcome, but nothing touches them, the blocks around, or other entities.
 */
public final class Penalties {

    public static Penalty effect(MobEffect effect, int ticks, int amplifier) {
        return (player, source) -> player.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    public static Penalty damage(float amount) {
        return (player, source) -> player.hurt(player.damageSources().magic(), amount);
    }

    public static Penalty fire(int seconds) {
        return (player, source) -> player.setSecondsOnFire(seconds);
    }

    /** Powder-snow frost on the player (blue screen edge, slowed) for about {@code ticks}. */
    public static Penalty freeze(int ticks) {
        return (player, source) -> {
            player.setTicksFrozen(Math.max(player.getTicksFrozen(), player.getTicksRequiredToFreeze() + ticks * 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
            player.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0,
                    player.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        };
    }

    /** Shoves the player away from the block, like being swatted by it. */
    public static Penalty knockback(double strength) {
        return (player, source) -> {
            Vec3 away = player.position().subtract(Vec3.atCenterOf(source));
            away = new Vec3(away.x, 0.0, away.z);
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1.0, 0.0, 0.0);
            }
            away = away.normalize().scale(strength);
            player.push(away.x, 0.2 + 0.2 * strength, away.z);
            player.hurtMarked = true;
        };
    }

    /** A fake explosion at the block: blast particle and sound, damage and a shove for the player only. */
    public static Penalty burst(float damage, double knockback) {
        return (player, source) -> {
            ServerLevel level = player.serverLevel();
            Vec3 at = Vec3.atCenterOf(source);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6F, 1.3F);
            player.hurt(player.damageSources().explosion(null, null), damage);
            Vec3 away = player.position().subtract(at);
            away = new Vec3(away.x, 0.0, away.z);
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1.0, 0.0, 0.0);
            }
            away = away.normalize().scale(knockback);
            player.push(away.x, 0.3 * knockback, away.z);
            player.hurtMarked = true;
        };
    }

    /**
     * A fake lightning strike on the player: sparks and thunder, damage for the player only. A real LightningBolt is
     * tracked from very far, sets fires and hits bystanders.
     */
    public static Penalty strikeVisual(float damage) {
        return (player, source) -> {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 24,
                    0.3, 1.0, 0.3, 0.2);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.WEATHER, 0.8F, 1.1F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 0.5F, 1.4F);
            player.hurt(player.damageSources().lightningBolt(), damage);
        };
    }

    public static Penalty all(Penalty... penalties) {
        return (player, source) -> {
            for (Penalty penalty : penalties) {
                penalty.apply(player, source);
            }
        };
    }

    private Penalties() {
    }
}
