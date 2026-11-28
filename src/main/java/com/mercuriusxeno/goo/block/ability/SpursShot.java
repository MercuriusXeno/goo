package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.ability.program.SpursStep;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * The shot Spurs goo makes at a living thing walking on it: a bone spike
 * leaves the goo a short way ahead of the thing, low over the cell it
 * walks on, and flies at the thing's middle, once the spurs step's cooldown
 * has passed since the goo last shot at it; each shot rolls the step's spend
 * chance of taking one layer from the cell, the cell gone with its last
 * layer. A shot credits the player who placed the cell, and nobody where no
 * player did, and is never shot at that player.
 * spurs-shoots-bone-spikes-at-walkers
 * standing-goo-thing-credits-whoever-placed-it
 */
final class SpursShot {

    /** How far ahead of the walker the spike leaves the goo, in blocks: past a mob's half width. */
    static final double LEAD = 0.6;
    /** How high over the walker's feet the spike leaves, in blocks. */
    static final double RISE = 0.1;
    /** How far inside the cell's edges the spike leaves, in blocks. */
    static final double EDGE_MARGIN = 0.05;
    /** The horizontal speed under which a walker reads as standing, blocks per tick. */
    private static final double STANDING_SPEED = 1e-3;

    private SpursShot() {
    }

    /**
     * Shoots a bone spike at a thing walking on a Spurs cell, where the step
     * shoots at it, it is not the player who sprayed the cell and its
     * cooldown has passed, and rolls the cell's spend.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param state  the cell's state
     * @param entity the thing on the cell
     * @param step   the spurs step the cell's laying ability runs
     * @param placer the player the shot credits, null for nobody
     */
    static void shoot(ServerLevel level, BlockPos pos, BlockState state, Entity entity, SpursStep step,
                      @Nullable ServerPlayer placer) {
        if (!(entity instanceof LivingEntity living) || !dueToShoot(level, pos, living, step)) {
            return;
        }
        living.setData(GooAttachments.SPURS_SHOT_AT, level.getGameTime());
        Vec3 heading = headingOf(living.getDeltaMovement(), level.getRandom().nextDouble() * Math.TAU);
        Vec3 start = launchPoint(living.position(), heading, pos);
        Vec3 aim = living.getBoundingBox().getCenter().add(living.getDeltaMovement());
        GooServerState.of(level.getServer()).boneSpikes().launch(level, placer, step.strike(), start,
                ProjectileFlight.launch(aim.subtract(start), step.shotSpeed()));
        int layers = state.getValue(VolumeCellBlock.LAYERS);
        int left = SpikesImpale.layersAfterStrike(layers, level.getRandom().nextDouble(), step.spendChance());
        if (left < layers) {
            level.setBlock(pos, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Where a bone spike leaves the goo: the lead ahead of the walker's feet
     * along its heading, kept over the cell's own top so it never leaves from
     * inside the ground beside the cell, the rise over the walker's feet.
     *
     * @param feet    the walker's feet, standing on the cell
     * @param heading the walker's horizontal heading, unit length
     * @param cell    the cell the spike leaves
     * @return the launch point
     */
    static Vec3 launchPoint(Vec3 feet, Vec3 heading, BlockPos cell) {
        return new Vec3(overTheCell(feet.x + heading.x * LEAD, cell.getX()), feet.y + RISE,
                overTheCell(feet.z + heading.z * LEAD, cell.getZ()));
    }

    // A coordinate held over the cell's column, the margin inside its edges.
    private static double overTheCell(double coordinate, int cellCorner) {
        return Mth.clamp(coordinate, cellCorner + EDGE_MARGIN, cellCorner + 1 - EDGE_MARGIN);
    }

    /**
     * The horizontal heading a walker moves along, or the fallback angle's
     * heading for a walker standing still.
     *
     * @param motion        the walker's motion, blocks per tick
     * @param fallbackAngle the angle in radians a standing walker's spike leaves at
     * @return the heading, unit length and flat
     */
    static Vec3 headingOf(Vec3 motion, double fallbackAngle) {
        Vec3 flat = new Vec3(motion.x, 0, motion.z);
        if (flat.length() < STANDING_SPEED) {
            return new Vec3(Math.cos(fallbackAngle), 0, Math.sin(fallbackAngle));
        }
        return flat.normalize();
    }

    /**
     * Whether a cell shoots at a living thing walking on it now: the step
     * shoots at it, it is not the player who sprayed the cell, and the
     * cooldown since the goo last shot at it has passed.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param living the thing on the cell
     * @param step   the spurs step
     * @return true where the cell shoots at the thing this tick
     */
    private static boolean dueToShoot(ServerLevel level, BlockPos pos, LivingEntity living, SpursStep step) {
        return EntityScan.passes(living, Set.copyOf(step.where()), null)
                && !SprayerSpared.spares(living, CellPlacers.of(level).placerAt(pos))
                && SpikesImpale.due(level.getGameTime(), living.getData(GooAttachments.SPURS_SHOT_AT),
                step.cooldown());
    }
}
