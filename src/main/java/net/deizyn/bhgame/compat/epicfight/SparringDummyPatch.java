package net.deizyn.bhgame.compat.epicfight;

import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;

/**
 * Epic Fight patch of the sparring dummy (D73): biped armature, the usual humanoid idle/walk/hit/death animations, and
 * no Epic Fight combat AI. Its moves come from {@code SparringAttackGoal} instead.
 */
public class SparringDummyPatch extends HumanoidMobPatch<SparringDummy> {

    public SparringDummyPatch() {
        super(Factions.NEUTRAL);
    }

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);
        commonAggresiveMobAnimatorInit(animator);
        animator.addLivingAnimation(LivingMotions.RUN, Animations.BIPED_RUN);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        commonAggressiveMobUpdateMotion(considerInaction);
        // the chase modes run by sprinting (D75); Epic Fight would show the walk for an aggressive mob
        if (original.isSprinting() && (currentLivingMotion == LivingMotions.CHASE
                || currentLivingMotion == LivingMotions.WALK)) {
            currentLivingMotion = LivingMotions.RUN;
            currentCompositeMotion = LivingMotions.RUN;
        }
    }

    /** HumanoidMobPatch would add its weapon-based attack goals here; the configured move list replaces them. */
    @Override
    public void setAIAsInfantry(boolean holdingRangedWeapon) {
    }

    @Override
    public void setAIAsMounted(Entity ridingEntity) {
    }
}
