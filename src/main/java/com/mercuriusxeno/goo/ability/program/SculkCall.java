package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Shriek blob's sculk call in a level: the blob shrieks once like a sculk
 * shrieker, its shriek sound and its rising column of shriek particles, and
 * every living hostile mob within reach across the ground and a few layers
 * up or down takes a walk to the blob ahead of every other move it would
 * make, which holds while the blob stands; every other mob is passed over
 * untouched, and no mob takes any effect.
 * decision shriek-blob-cries-once-and-calls-earshot
 * decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches
 * decision shriek-pulls-hostiles-only
 */
public final class SculkCall {

    /** How many layers above or below the blob a mob the shriek calls may stand. */
    static final double VERTICAL_REACH = 8;
    /** A mob this near the blob has arrived and walks no further. */
    static final double ARRIVED_WITHIN = 1.5;
    /** The pace a called mob walks at, its own walking speed. */
    static final double WALK_SPEED = 1.0;
    /** The goal priority a called mob's walk takes, ahead of every goal vanilla gives a mob. */
    private static final int WALK_PRIORITY = -1;
    /** The shriek the blob cries, the sculk shrieker's own. */
    static final SoundEvent SHRIEK = SoundEvents.SCULK_SHRIEKER_SHRIEK;
    /** The shriek particles the column rises from, one after another, as a sculk shrieker's. */
    static final int COLUMN_PARTICLES = 10;
    /** Ticks between one shriek particle of the column and the next. */
    private static final int COLUMN_DELAY_TICKS = 5;
    /** How far above the blob's floor the column rises from, a shrieker's mouth. */
    private static final double MOUTH_LIFT = 0.5;
    /** The shriek's volume and pitch, a sculk shrieker's own. */
    private static final float SHRIEK_VOLUME = 2f;
    private static final float SHRIEK_PITCH = 0.8f;

    private SculkCall() {
    }

    /**
     * Shrieks from the blob: plays the sculk shrieker's shriek, raises its
     * column of shriek particles, and calls every living hostile within reach.
     *
     * @param level    the level
     * @param blobCell the blob's cell, where called mobs walk to
     * @param reach    how far across the ground the shriek is heard, in blocks
     */
    public static void shriek(ServerLevel level, BlockPos blobCell, double reach) {
        Vec3 blob = Vec3.atBottomCenterOf(blobCell);
        level.playSound(null, blob.x, blob.y, blob.z, SHRIEK, SoundSource.BLOCKS,
                SHRIEK_VOLUME, SHRIEK_PITCH);
        for (int particle = 0; particle < COLUMN_PARTICLES; particle++) {
            level.sendParticles(new ShriekParticleOption(particle * COLUMN_DELAY_TICKS), blob.x, blob.y + MOUTH_LIFT,
                    blob.z, 1, 0, 0, 0, 0);
        }
        AABB earshot = new AABB(blob, blob).inflate(reach, VERTICAL_REACH, reach);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, earshot, mob -> mob.isAlive() && calls(mob))) {
            if (inEarshot(mob.position(), blob, reach)) {
                call(mob, blobCell);
            }
        }
    }

    /**
     * Whether a point lies within the shriek's reach across the ground, short
     * of a point already at the blob.
     *
     * @param at    a mob's position
     * @param blob  the blob
     * @param reach the shriek's reach
     * @return true where the point is heard and has not arrived
     */
    private static boolean inEarshot(Vec3 at, Vec3 blob, double reach) {
        return horizontalDistance(at, blob) <= reach && at.distanceTo(blob) > ARRIVED_WITHIN;
    }

    /**
     * Gives a mob its walk to the blob, unless it already walks to it,
     * dropping first every walk it holds to a blob that has gone.
     *
     * @param mob      the mob the shriek called
     * @param blobCell the blob's cell
     */
    static void call(Mob mob, BlockPos blobCell) {
        mob.goalSelector.removeAllGoals(goal -> goal instanceof SculkCallGoal walk && !walk.blobStands());
        if (!isCalledTo(mob, blobCell)) {
            mob.goalSelector.addGoal(WALK_PRIORITY, new SculkCallGoal(mob, blobCell));
        }
    }

    /**
     * Whether a mob holds a walk to a blob, the walk the shriek gives the mobs it calls.
     *
     * @param mob      the mob
     * @param blobCell the blob's cell
     * @return true where the mob is called to that blob
     */
    public static boolean isCalledTo(Mob mob, BlockPos blobCell) {
        return mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                .anyMatch(goal -> goal instanceof SculkCallGoal walk && walk.leadsTo(blobCell));
    }

    /**
     * Whether the shriek calls a mob: a hostile one, and no other.
     *
     * @param mob a mob within earshot
     * @return true for a mob in the monster category or an enemy
     */
    static boolean calls(Mob mob) {
        return calls(mob.getType().getCategory(), mob instanceof Enemy);
    }

    /**
     * Whether the shriek calls a mob of a category: a monster or an enemy is
     * called, and every other mob is passed over.
     * decision shriek-pulls-hostiles-only
     *
     * @param category the mob's category
     * @param enemy    whether the mob counts as an enemy
     * @return true for a hostile mob
     */
    static boolean calls(MobCategory category, boolean enemy) {
        return enemy || category == MobCategory.MONSTER;
    }

    /**
     * @param at   a point
     * @param blob the blob
     * @return how far the point stands from the blob across the ground, height left out
     */
    static double horizontalDistance(Vec3 at, Vec3 blob) {
        return Math.hypot(at.x - blob.x, at.z - blob.z);
    }
}
