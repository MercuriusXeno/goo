package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.alienstructure.AlienMaterialBlock;
import com.mercuriusxeno.goo.block.alienstructure.AlienPillarBlock;
import com.mercuriusxeno.goo.lunar.RuinLook.LaidBlock;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Part;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Placement;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Shape;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * One of the warrens' little mini structures: a ruin shape drawn from {@link WarrenRuinShapes}, turned a random
 * quarter turn or none, cut from its materials with its pillars in one style, each glyph bearing its material's rune, laid
 * around an open spot on a warren tunnel's floor, where it cuts into the tunnel's rock as buried
 * remains do. A shape reaching outside the warrens' band is not laid. No block of a ruin covers a monolith marker or
 * stands on one, so every marker keeps open air over it, and a ruin's own marker stands only under open air.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public final class WarrenRuinFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<WarrenRuinFeature> CODEC = MapCodec.unit(WarrenRuinFeature::new);

    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public WarrenRuinFeature() {
    }

    @Override
    public MapCodec<WarrenRuinFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockPos spot = WarrenFloors.findNear(level, random, origin);
        if (spot == null) {
            return false;
        }
        Shape shape = WarrenRuinShapes.ALL.get(random.nextInt(WarrenRuinShapes.ALL.size()));
        if (!fitsInWarrens(shape, spot.getY())) {
            return false;
        }
        RuinLook look = RuinLook.pick(random);
        for (LaidBlock laid : look.lay(shape)) {
            BlockPos at = spot.offset(offsetOf(laid.placement(), look.rotation()));
            if (mayLay(level, at, laid.placement().part())) {
                level.setBlock(at, stateOf(laid, look.rotation(), random), PLACE_FLAGS);
            }
        }
        return true;
    }

    /**
     * A placement's offset from the spot once the ruin is turned about it.
     *
     * @param placement the placement
     * @param rotation  how the ruin is turned
     * @return the turned offset
     */
    static BlockPos offsetOf(Placement placement, Rotation rotation) {
        return new BlockPos(placement.dx(), placement.dy(), placement.dz()).rotate(rotation);
    }

    /**
     * Whether a shape laid at a spot's height keeps every block inside the warrens' band.
     *
     * @param shape the shape
     * @param spotY the spot's height
     * @return whether it fits
     */
    static boolean fitsInWarrens(Shape shape, int spotY) {
        return spotY + shape.lowest() >= LunarDepths.SEA_ZONE_TOP_Y
                && spotY + shape.highest() < LunarDepths.WARREN_ZONE_TOP_Y;
    }

    private static boolean mayLay(WorldGenLevel level, BlockPos at, Part part) {
        boolean clearOfMarkers = !level.getBlockState(at).is(GooBlocks.MONOLITH_MARKER.get())
                && !level.getBlockState(at.below()).is(GooBlocks.MONOLITH_MARKER.get());
        if (part != Part.MARKER) {
            return clearOfMarkers;
        }
        return clearOfMarkers && level.getBlockState(at.above()).isAir() && !WarrenFloors.touchesAMarker(level, at);
    }

    private static BlockState stateOf(LaidBlock laid, Rotation rotation, RandomSource random) {
        return switch (laid.placement().part()) {
            case PLAIN -> GooBlocks.ALIEN_STONE.get().defaultBlockState()
                    .setValue(AlienMaterialBlock.MATERIAL, laid.material());
            case PILLAR -> pillarOf(laid, Direction.Axis.Y);
            case PILLAR_LYING -> pillarOf(laid, rotation.rotate(Direction.EAST).getAxis());
            case GLYPH -> GooBlocks.ALIEN_GLYPH.get().defaultBlockState()
                    .setValue(AlienMaterialBlock.MATERIAL, laid.material());
            case TILE -> GooBlocks.ALIEN_TILE.get().defaultBlockState()
                    .setValue(AlienMaterialBlock.MATERIAL, laid.material());
            case MARKER -> MonolithMarkerFeature.markerTurned(random);
        };
    }

    /**
     * A pillar in its part's material and the ruin's style, built only for a pillar part, since the marker a ruin lays
     * is cut from no material.
     *
     * @param laid the laid pillar block
     * @param axis the axis the pillar runs along
     * @return the pillar's state
     */
    private static BlockState pillarOf(LaidBlock laid, Direction.Axis axis) {
        return GooBlocks.ALIEN_PILLAR.get().defaultBlockState().setValue(AlienPillarBlock.STYLE, laid.style())
                .setValue(AlienMaterialBlock.MATERIAL, laid.material()).setValue(RotatedPillarBlock.AXIS, axis);
    }
}
