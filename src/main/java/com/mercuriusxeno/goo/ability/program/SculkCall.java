package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One tick of a convoke blob's sculk call in a level: the ring stands as a
 * circle of sculk charge on the ground at its radius, clicking as it leaves
 * the blob, and every living hostile mob in the band it swept this tick,
 * within a few layers of the blob, takes a walk to the blob ahead of every
 * other move it would make, which holds while the blob stands; every other
 * mob the ring crosses is passed over untouched.
 * decision convoke-is-a-sculk-call-that-draws-every-mob-the-ring-touches
 * decision convoke-ring-pulls-hostiles-only
 */
public final class SculkCall {

    /** How many layers above or below the blob a mob the ring crosses may stand. */
    static final double VERTICAL_REACH = 8;
    /** A mob this near the blob has arrived and walks no further. */
    static final double ARRIVED_WITHIN = 1.5;
    /** The pace a called mob walks at, its own walking speed. */
    static final double WALK_SPEED = 1.0;
    /** The goal priority a called mob's walk takes, ahead of every goal vanilla gives a mob. */
    private static final int WALK_PRIORITY = -1;
    /** Sculk charge drawn per block of the ring's circumference. */
    private static final double PARTICLES_PER_BLOCK = 1.5;
    private static final int MIN_PARTICLES = 8;
    private static final int MAX_PARTICLES = 128;
    /** How far above the blob's floor the ring draws, so the ground does not hide it. */
    private static final double RING_LIFT = 0.1;
    private static final double FULL_TURN = 2 * Math.PI;

    private SculkCall() {
    }

    /**
     * Draws the ring at its outer radius and calls every mob between the two radii.
     *
     * @param level    the level
     * @param blobCell the blob's cell, where called mobs walk to
     * @param inner    the ring's radius at the start of this tick
     * @param outer    the ring's radius at the end of this tick
     */
    public static void pulse(ServerLevel level, BlockPos blobCell, double inner, double outer) {
        Vec3 blob = Vec3.atBottomCenterOf(blobCell);
        if (inner == 0) {
            level.playSound(null, blob.x, blob.y, blob.z, SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 1f, 1f);
        }
        drawRing(level, blob, outer);
        AABB band = new AABB(blob, blob).inflate(outer, VERTICAL_REACH, outer);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, band, mob -> mob.isAlive() && calls(mob))) {
            if (crossedThisTick(mob.position(), blob, inner, outer)) {
                call(mob, blobCell);
            }
        }
    }

    /**
     * Whether the ring's front crossed a point this tick, short of a point already at the blob.
     *
     * @param at    a mob's position
     * @param blob  the blob
     * @param inner the ring's radius at the start of this tick
     * @param outer the ring's radius at the end of this tick
     * @return true where the point lies in the band swept and has not arrived
     */
    private static boolean crossedThisTick(Vec3 at, Vec3 blob, double inner, double outer) {
        double across = horizontalDistance(at, blob);
        return across >= inner && across < outer && at.distanceTo(blob) > ARRIVED_WITHIN;
    }

    /**
     * Gives a mob its walk to the blob, unless it already walks to it,
     * dropping first every walk it holds to a blob that has gone.
     *
     * @param mob      the mob the ring crossed
     * @param blobCell the blob's cell
     */
    static void call(Mob mob, BlockPos blobCell) {
        mob.goalSelector.removeAllGoals(goal -> goal instanceof SculkCallGoal walk && !walk.blobStands());
        boolean walking = mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                .anyMatch(goal -> goal instanceof SculkCallGoal walk && walk.leadsTo(blobCell));
        if (!walking) {
            mob.goalSelector.addGoal(WALK_PRIORITY, new SculkCallGoal(mob, blobCell));
        }
    }

    /**
     * Whether the ring calls a mob: a hostile one, and no other.
     *
     * @param mob a mob the ring crosses
     * @return true for a mob in the monster category or an enemy
     */
    static boolean calls(Mob mob) {
        return calls(mob.getType().getCategory(), mob instanceof Enemy);
    }

    /**
     * Whether the ring calls a mob of a category: a monster or an enemy is
     * called, and every other mob is passed over.
     * decision convoke-ring-pulls-hostiles-only
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

    private static void drawRing(ServerLevel level, Vec3 blob, double radius) {
        int points = (int) Math.clamp(Math.ceil(FULL_TURN * radius * PARTICLES_PER_BLOCK),
                MIN_PARTICLES, MAX_PARTICLES);
        for (int point = 0; point < points; point++) {
            double angle = FULL_TURN * point / points;
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, blob.x + radius * Math.cos(angle),
                    blob.y + RING_LIFT, blob.z + radius * Math.sin(angle), 1, 0, 0, 0, 0);
        }
    }
}
