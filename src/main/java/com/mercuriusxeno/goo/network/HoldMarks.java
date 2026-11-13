package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.GradualBreak;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one stream hold has marked on the blocks it reached: the blocks it
 * painted, each with the state that stood there when it was painted, which
 * keep transitioning while the hold lasts, and the positions it has stepped,
 * which it steps no further (decision decay-gnats-degrade-each-block-once).
 * A new hold starts with fresh marks.
 */
public final class HoldMarks {

    private final Map<BlockPos, BlockState> painted = new HashMap<>();
    private final Set<BlockPos> stepped = new HashSet<>();
    private @Nullable Vec3 anchor;
    /** The mob the swarm chases, or none yet. */
    private int chasedId = GnatSeekPayload.NO_PREY;
    /** The hold tick the swarm's gnats reach the mob they chase. */
    private int reachedOn;
    private final GradualBreak gradualBreak = new GradualBreak();

    /**
     * The blocks the hold is breaking over several ticks, each with its progress
     * (decision gradual-breaks-share-one-break-texture-piece).
     *
     * @return the hold's gradual break
     */
    public GradualBreak gradualBreak() {
        return gradualBreak;
    }

    /**
     * Paints a block, unless the hold already painted or stepped it.
     *
     * @param pos    the block
     * @param origin the state standing there now
     */
    public void paint(BlockPos pos, BlockState origin) {
        if (!stepped.contains(pos)) {
            painted.putIfAbsent(pos.immutable(), origin);
        }
    }


    /**
     * @return the painted blocks, each with the state that stood there when painted, as a copy
     */
    public Map<BlockPos, BlockState> painted() {
        return Map.copyOf(painted);
    }

    /**
     * Notes that the hold stepped a block, which leaves it painted no longer.
     *
     * @param pos the block
     */
    public void noteStepped(BlockPos pos) {
        painted.remove(pos);
        stepped.add(pos.immutable());
    }

    /**
     * Drops a painted block the hold can step no further, such as one cleared to air.
     *
     * @param pos the block
     */
    public void unpaint(BlockPos pos) {
        painted.remove(pos);
    }

    /**
     * Where the hold is anchored: the point given on the first ask, kept
     * for the rest of the hold, so a sense growing from where the hold began
     * stays centered there as the player moves
     * (decision glitter-sphere-icons-gem-ore-groups).
     *
     * @param here the point to anchor at, where none is anchored yet
     * @return the hold's anchor
     */
    public Vec3 anchorAt(Vec3 here) {
        if (anchor == null) {
            anchor = here;
        }
        return anchor;
    }

    /**
     * @param pos the block
     * @return true once the hold stepped it
     */
    public boolean stepped(BlockPos pos) {
        return stepped.contains(pos);
    }

    /**
     * Whether the swarm's gnats have reached the mob it chases this tick, so
     * its bite may land.
     *
     * @param preyId   the mob the swarm chases
     * @param held     the hold's tick count
     * @param distance how far the mob stands from the glove, in blocks
     * @return true once the gnats have had time to fly there
     */
    public boolean swarmReached(int preyId, int held, double distance) {
        if (preyId != chasedId) {
            chasedId = preyId;
            reachedOn = held + (int) Math.ceil(distance / SwarmMotes.GNAT_SPEED);
        }
        return held >= reachedOn;
    }
}
