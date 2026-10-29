package net.deizyn.bhgame.client.pose;

import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The hit a punch leaves in the air in front of the player (D42): sparks and a thud where the fist lands, on the side
 * the arrow points to (W high, S low, A to the player's left, D to the right); the finisher is a bigger burst.
 * Client-side particles and sounds only, so they cost no packets beyond the action itself.
 */
final class PunchEffects {
    /** From the eyes to the dummy's surface when standing at the snapped distance. */
    private static final double REACH = 0.95;
    private static final double SIDE = 0.35;

    static void spawn(Player player, PlayerAction action, @Nullable InputKey key) {
        if (action == PlayerAction.KNOCKED_BACK) {
            return;
        }
        Level level = player.level();
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 at = player.getEyePosition().add(forward.scale(REACH)).add(0.0, -0.3, 0.0);
        if (key != null) {
            at = switch (key) {
                case UP -> at.add(0.0, 0.4, 0.0);
                case DOWN -> at.add(0.0, -0.5, 0.0);
                case LEFT -> at.add(right.scale(-SIDE));
                case RIGHT -> at.add(right.scale(SIDE));
                default -> at;
            };
        }
        boolean finisher = action == PlayerAction.PUNCH_FINISH;
        int sparks = finisher ? 14 : 6;
        for (int i = 0; i < sparks; i++) {
            double vx = (level.random.nextDouble() - 0.5) * 0.4 - forward.x * 0.15;
            double vy = (level.random.nextDouble() - 0.3) * 0.3;
            double vz = (level.random.nextDouble() - 0.5) * 0.4 - forward.z * 0.15;
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, vx, vy, vz);
        }
        if (finisher) {
            level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0.0, 0.0, 0.0);
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 0.9F,
                    false);
        } else {
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.45F,
                    1.1F + level.random.nextFloat() * 0.2F, false);
        }
    }

    private PunchEffects() {
    }
}
