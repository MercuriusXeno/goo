package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/**
 * A called mob's walk to the landing the shriek rang out from, ahead of
 * every other move the mob would make: it holds for the ticks the shriek
 * lasts, counted from the shriek, while the mob has not arrived, re-pathing
 * as it walks, and lies idle once its time runs out. No block stands at the
 * landing to hold it.
 * decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches
 * decision shriek-blob-cries-once-and-calls-earshot
 */
final class SculkCallGoal extends Goal {

    /** Ticks between the walk's re-paths, so a path a block or a mob broke is found again. */
    private static final int REPATH_TICKS = 10;

    private final Mob mob;
    private final BlockPos blobCell;
    private final Vec3 blob;
    /** The game tick the walk's time runs out on, the shriek's tick plus the ticks it lasts. */
    private final long endsOn;
    private int ticksSincePath;

    /**
     * @param mob        the called mob
     * @param blobCell   the landing cell the shriek rang out from
     * @param shriekTick the game tick the shriek rang out on
     * @param lasts      the ticks the walk holds, counted from the shriek
     */
    SculkCallGoal(Mob mob, BlockPos blobCell, long shriekTick, int lasts) {
        this.mob = mob;
        this.blobCell = blobCell.immutable();
        this.blob = Vec3.atBottomCenterOf(blobCell);
        this.endsOn = shriekTick + lasts;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * @param cell a landing cell
     * @return true where this walk leads to that landing
     */
    boolean leadsTo(BlockPos cell) {
        return blobCell.equals(cell);
    }

    /**
     * @param gameTick a game tick
     * @return true where the walk still holds on that tick, the last tick before its time runs out included
     */
    boolean holdsAt(long gameTick) {
        return gameTick < endsOn;
    }

    /**
     * @return true while the walk still holds on the mob's level's current tick
     */
    boolean holds() {
        return holdsAt(mob.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        return holds() && mob.position().distanceTo(blob) > SculkCall.ARRIVED_WITHIN;
    }

    @Override
    public void start() {
        walk();
    }

    @Override
    public void tick() {
        ticksSincePath++;
        if (ticksSincePath >= REPATH_TICKS || mob.getNavigation().isDone()) {
            walk();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    private void walk() {
        ticksSincePath = 0;
        mob.getNavigation().moveTo(blob.x, blob.y, blob.z, SculkCall.WALK_SPEED);
    }
}
