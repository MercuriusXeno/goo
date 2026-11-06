package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.List;

/**
 * The vein a churn lifts, carried across its ticks: the cells it stands in
 * now, and whether the churn has looked for it yet. A step is an immutable
 * definition, so the marker keeps this on its block entity, which saves it,
 * and a vein that drifts apart as it rises still rises whole
 * (decision churn-pulls-a-vein-up-as-a-fluid-chain).
 */
public final class ChurnVeinState {

    private static final String TAG_FOUND = "ChurnVeinFound";
    private static final String TAG_CELLS = "ChurnVein";

    private boolean found;
    private List<BlockPos> cells = List.of();

    /**
     * @return true once the churn has looked for its vein
     */
    public boolean found() {
        return found;
    }

    /**
     * @return the vein's cells now
     */
    public List<BlockPos> cells() {
        return cells;
    }

    /**
     * Holds the vein's cells from now, the first call marking it found.
     *
     * @param standing the vein's cells
     */
    public void standAt(List<BlockPos> standing) {
        found = true;
        cells = List.copyOf(standing);
    }

    /**
     * Restores the vein from persistent data.
     *
     * @param input the value input to read from
     */
    public void load(ValueInput input) {
        found = input.getBooleanOr(TAG_FOUND, false);
        cells = input.read(TAG_CELLS, BlockPos.CODEC.listOf()).orElse(List.of());
    }

    /**
     * Writes the vein to persistent data.
     *
     * @param output the value output to write to
     */
    public void save(ValueOutput output) {
        if (found) {
            output.putBoolean(TAG_FOUND, true);
            output.store(TAG_CELLS, BlockPos.CODEC.listOf(), cells);
        }
    }
}
