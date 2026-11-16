package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The wisp's block entity, which saves nothing: it stands so the wisp's
 * renderer can draw the floating mote, and keeps on the client the tick the
 * wisp appeared there, which the mote fades in from, save a wisp a
 * drifting mote settled into, shown whole at once.
 * decision radiant-wisps-where-light-is-low
 */
public class WispBlockEntity extends BlockEntity {

    /** The appearance tick before the wisp has loaded. */
    public static final long NOT_YET = -1L;
    /** The tick the wisp appeared on this client, or {@link #NOT_YET} before it loads. */
    private long appearedAt = NOT_YET;

    /**
     * Creates the wisp's block entity.
     *
     * @param pos   the wisp's position
     * @param state the wisp's block state
     */
    public WispBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.WISP.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            boolean driftedHere = !level.getEntitiesOfClass(DriftingWisp.class, new AABB(worldPosition)).isEmpty();
            appearedAt = appearanceTick(driftedHere, level.getGameTime());
        }
    }

    /**
     * The tick a wisp appearing on a client fades in from: none for a wisp
     * whose drifting mote stands on its spot as it loads, which takes over
     * from that mote whole, otherwise the tick it loaded.
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param driftedHere whether a drifting wisp stands on the spot
     * @param now         the game tick the wisp loads
     * @return the appearance tick, or {@link #NOT_YET} for a wisp shown whole at once
     */
    static long appearanceTick(boolean driftedHere, long now) {
        return driftedHere ? NOT_YET : now;
    }

    /**
     * The tick the wisp appeared on this client.
     *
     * @return the tick, or {@link #NOT_YET} before it loads
     */
    public long appearedAt() {
        return appearedAt;
    }
}
