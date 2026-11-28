package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.EnumSet;
import java.util.Set;

/**
 * Durable's Coat cell: the volume cell bound to Durable's goo, resisting a
 * blast and a pick as obsidian does at any fill, never broken by a mob, and
 * keeping the block it stands against.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
public class CoatCellBlock extends VolumeCellBlock {

    /**
     * Obsidian's hardness, which a Coat cell takes at any fill.
     */
    public static final float HARDNESS = 50.0F;
    /**
     * Obsidian's blast resistance, which a Coat cell takes at any fill.
     */
    public static final float BLAST_RESISTANCE = 1200.0F;

    /**
     * Creates a Coat cell.
     *
     * @param properties the block properties
     * @param binding    the Coat binding
     */
    public CoatCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Refuses every mob the cell, the wither and the ender dragon among them.
     *
     * @return false
     */
    @Override
    public boolean canEntityDestroy(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                    @NonNull Entity entity) {
        return false;
    }

    /**
     * The faces of a block a Coat cell stands against.
     *
     * @param level the level
     * @param pos   the block
     * @return each side whose neighbor is a Coat cell
     */
    public static Set<Direction> coatedFaces(BlockGetter level, BlockPos pos) {
        Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).getBlock() instanceof CoatCellBlock) {
                faces.add(side);
            }
        }
        return faces;
    }

    /**
     * Whether a blast keeps off a block: one of the faces the blast stands
     * beyond is coated, so the blast comes from the Coat's side.
     *
     * @param block  the block's center
     * @param blast  the blast's center
     * @param coated the block's coated faces
     * @return true where a coated face looks toward the blast
     */
    public static boolean shieldsFrom(Vec3 block, Vec3 blast, Set<Direction> coated) {
        Vec3 toward = blast.subtract(block);
        return coated.stream().anyMatch(face -> toward.dot(Vec3.atLowerCornerOf(face.getUnitVec3i())) > 0);
    }
}
