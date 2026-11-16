package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkSpreader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sculk charges Host goo started on one server, each a vanilla level
 * spreader whose origin is the Host cell the charge began at, ticked with
 * its level as a catalyst ticks its own. The cell is a plain block with no
 * block entity and no chunk storage, so the charge lives here, held outside
 * the cell: a charge whose origin's chunk stops ticking waits, its cursors
 * unmoved, until the chunk ticks again, and a server stop drops every charge
 * in flight.
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 */
public final class HostSpreaders {

    private final Map<ResourceKey<Level>, List<Charge>> charges = new HashMap<>();

    /**
     * One charge in flight.
     *
     * @param origin   the Host cell it began at
     * @param spreader the cursors carrying it
     */
    private record Charge(BlockPos origin, SculkSpreader spreader) {
    }

    /**
     * Starts a charge at a Host cell, as a catalyst starts one at a death.
     *
     * @param level  the cell's level
     * @param cell   the Host cell the charge begins at
     * @param charge the experience the death carried
     */
    public void start(Level level, BlockPos cell, int charge) {
        SculkSpreader spreader = SculkSpreader.createLevelSpreader();
        spreader.addCursors(cell.immutable(), charge);
        charges.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(new Charge(cell.immutable(), spreader));
    }

    /**
     * Moves every charge in a level one tick along, dropping each spent.
     *
     * @param level the ticking level
     */
    public void tick(ServerLevel level) {
        List<Charge> inLevel = charges.get(level.dimension());
        if (inLevel == null) {
            return;
        }
        for (Charge charge : inLevel) {
            charge.spreader().updateCursors(level, charge.origin(), level.getRandom(), true);
        }
        inLevel.removeIf(charge -> charge.spreader().getCursors().isEmpty());
        if (inLevel.isEmpty()) {
            charges.remove(level.dimension());
        }
    }

    /**
     * Drops every charge in flight, as a server stop does.
     */
    public void clear() {
        charges.clear();
    }
}
