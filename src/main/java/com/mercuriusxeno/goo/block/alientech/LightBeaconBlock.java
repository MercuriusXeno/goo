package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;

/**
 * The light beacon: a block that connects to a solid face, gives the max light where it stands and sends its
 * far-seen column straight out the side opposite the face it attaches by, which its block entity's renderer draws.
 * decision bulb-one-model-max-light-beacon-combo
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public class LightBeaconBlock extends FaceMountedBlock {

    /** The max light, which the beacon gives. */
    public static final int LIGHT = 15;
    /** The shard's pillar, 6 pixels square and 13 tall with its foot and collar, and its lit tip, 4 square and 2 tall. */
    private static final float PILLAR_WIDTH = 6f;
    private static final float TIP_WIDTH = 4f;
    private static final float PILLAR_TOP = 3f;
    private static final float TIP_TOP = 1f;
    private static final float CELL = 16f;

    /**
     * Creates the block, pointing up off a floor.
     *
     * @param properties the block properties
     */
    public LightBeaconBlock(Properties properties) {
        super(properties, Shapes.or(boxZ(PILLAR_WIDTH, PILLAR_TOP, CELL),
                boxZ(TIP_WIDTH, TIP_TOP, PILLAR_TOP)));
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new LightBeaconBlockEntity(pos, state);
    }
}
