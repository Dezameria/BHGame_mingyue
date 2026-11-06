package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Gets close to the target (walking or running, by chase mode), picks the move set whose condition holds and whose
 * range reaches (D75), plays its next move, waits for it to end, pauses, and goes again (D73). Not a
 * {@code MeleeAttackGoal}, so Epic Fight's mob AI setup leaves it alone.
 * <p>
 * A move is chosen one tick before it plays: its play speed goes out as entity data that tick, so clients have it when
 * Epic Fight's animation packet arrives. Without Epic Fight it falls back to a vanilla punch, so the mob still works.
 * <p>
 * It lets go of a target whose HP fell under "stop below" until that target heals to full (D79), and runs the On
 * engage / On each move commands.
 */
final class SparringAttackGoal extends Goal {
    /** Ticks after starting a move before checking Epic Fight's state, which updates on the next animator tick. */
    private static final int START_GRACE = 3;
    /** A move that would not play (unknown id) is skipped after this long. */
    private static final int FAILED_MOVE_DELAY = 20;
    private static final int VANILLA_COOLDOWN = 20;
    /** How often a chasing dummy may look for a new path; farther targets less often (like MeleeAttackGoal). */
    private static final int REPATH_INTERVAL = 10;
    private static final int FAR_REPATH_EXTRA = 10;
    private static final double FAR_DISTANCE_SQR = 16D * 16D;
    /** A new path only once the target has moved this far (squared) from where the last one led, or it ended. */
    private static final double TARGET_MOVED_SQR = 1D;

    private final SparringDummy dummy;
    private int cooldown;
    private int grace;
    private int repath;
    private SparringMove pending;
    /** Epic Fight patch, looked up once instead of every tick. */
    private Object patch;
    private boolean pathed;
    private double pathX;
    private double pathY;
    private double pathZ;

    SparringAttackGoal(SparringDummy dummy) {
        this.dummy = dummy;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = dummy.getTarget();
        return target != null && target.isAlive() && !target.isSpectator() && !dummy.holdsBack(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        dummy.setAggressive(true);
        repath = 0;
        dummy.runCommand(SparringCommands.Event.ENGAGE, dummy.getTarget());
    }

    @Override
    public void stop() {
        dummy.setAggressive(false);
        halt();
        dummy.resetCombo();
        pending = null;
    }

    @Override
    public void tick() {
        LivingEntity target = dummy.getTarget();
        if (target == null) {
            return;
        }
        dummy.getLookControl().setLookAt(target, 30F, 30F);
        if (pending != null) {
            play(target);
            return;
        }
        if (grace > 0) {
            grace--;
            return;
        }
        if (patch == null) {
            patch = EpicFightCompat.patchOf(dummy);
        }
        if (EpicFightCompat.isBusy(patch)) {
            halt();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            // between moves: close in on the default range, but don't pick a move yet
            double range = dummy.attackRange();
            if (dummy.distanceToSqr(target) > range * range) {
                chase(target);
            } else {
                halt();
            }
            return;
        }
        int pattern = dummy.choosePattern(target);
        if (pattern < 0) {
            chase(target);
            return;
        }
        halt();
        SparringMove move = dummy.nextMove(pattern);
        if (EpicFightCompat.isAvailable()) {
            dummy.setMoveSpeed(dummy.animationSpeed() * move.speed());
            pending = move;
        } else {
            dummy.swing(InteractionHand.MAIN_HAND);
            dummy.doHurtTarget(target);
            dummy.runCommand(SparringCommands.Event.MOVE, target);
            float speed = dummy.animationSpeed() * move.speed();
            cooldown = Math.max(Math.round(VANILLA_COOLDOWN / speed), dummy.pauseTicks());
        }
    }

    private void play(LivingEntity target) {
        SparringMove move = pending;
        pending = null;
        if (EpicFightCompat.playMove(dummy, move.animation(), move.transition(), target)) {
            grace = START_GRACE;
            cooldown = dummy.pauseTicks();
            dummy.runCommand(SparringCommands.Event.MOVE, target);
        } else {
            cooldown = FAILED_MOVE_DELAY;
        }
    }

    private void chase(LivingEntity target) {
        SparringDummy.ChaseMode mode = dummy.chaseMode();
        if (mode == SparringDummy.ChaseMode.STAY) {
            halt();
            return;
        }
        double distanceSqr = dummy.distanceToSqr(target);
        boolean run = switch (mode) {
            case RUN -> true;
            // two thresholds, so a target near the line doesn't flip run/walk (and resend the flag) every tick
            case AUTO -> distanceSqr > square(dummy.isSprinting()
                    ? SparringDummy.AUTO_WALK_DISTANCE : SparringDummy.AUTO_RUN_DISTANCE);
            default -> false;
        };
        if (run != dummy.isSprinting()) {
            dummy.setSprinting(run);
            pathed = false; // new speed now
            repath = 0;
        }
        if (--repath > 0) {
            return;
        }
        repath = REPATH_INTERVAL + (distanceSqr > FAR_DISTANCE_SQR ? FAR_REPATH_EXTRA : 0);
        // pathfinding is the costly part of chasing: keep the path while the target stays where it led
        if (pathed && !dummy.getNavigation().isDone() && target.distanceToSqr(pathX, pathY, pathZ) < TARGET_MOVED_SQR) {
            return;
        }
        pathed = dummy.getNavigation().moveTo(target, run ? dummy.runSpeed() : 1D);
        pathX = target.getX();
        pathY = target.getY();
        pathZ = target.getZ();
    }

    private static double square(double value) {
        return value * value;
    }

    private void halt() {
        if (pathed || !dummy.getNavigation().isDone()) {
            dummy.getNavigation().stop();
        }
        pathed = false;
        repath = 0;
        if (dummy.isSprinting()) {
            dummy.setSprinting(false);
        }
    }
}
