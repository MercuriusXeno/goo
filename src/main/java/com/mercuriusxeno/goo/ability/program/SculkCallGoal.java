package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/**
 * A called mob's walk to the shriek blob that called it, ahead of every
 * other move the mob would make: it holds while the blob stands and the mob
 * has not arrived, re-pathing as it walks, and lies idle once the blob goes.
 * decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches
 */
final class SculkCallGoal extends Goal {

    /** Ticks between the walk's re-paths, so a path a block or a mob broke is found again. */
    private static final int REPATH_TICKS = 10;

    private final Mob mob;
    private final BlockPos blobCell;
    private final Vec3 blob;
    private int ticksSincePath;

    /**
     * @param mob      the called mob
     * @param blobCell the blob's cell
     */
    SculkCallGoal(Mob mob, BlockPos blobCell) {
        this.mob = mob;
        this.blobCell = blobCell.immutable();
        this.blob = Vec3.atBottomCenterOf(blobCell);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * @param cell a blob's cell
     * @return true where this walk leads to that blob
     */
    boolean leadsTo(BlockPos cell) {
        return blobCell.equals(cell);
    }

    /**
     * @return true while the blob that called the mob still stands
     */
    boolean blobStands() {
        return mob.level().getBlockState(blobCell).is(GooBlocks.ABILITY_BLOCK.get());
    }

    @Override
    public boolean canUse() {
        return blobStands() && mob.position().distanceTo(blob) > SculkCall.ARRIVED_WITHIN;
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
