package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.alienstructure.AlienGlyphBlock;
import com.mercuriusxeno.goo.block.alienstructure.AlienMaterial;
import com.mercuriusxeno.goo.block.alienstructure.AlienMaterialBlock;
import com.mercuriusxeno.goo.block.alienstructure.AlienPillarBlock;
import com.mercuriusxeno.goo.block.alienstructure.GlyphMark;
import com.mercuriusxeno.goo.block.alienstructure.PillarStyle;
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
 * quarter turn or none, cut from one alien material with its pillars in one style and each glyph drawn at random, laid
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
        RuinLook look = new RuinLook(pick(AlienMaterial.values(), random), pick(PillarStyle.values(), random),
                pick(Rotation.values(), random));
        for (Placement placement : shape.blocks()) {
            BlockPos at = spot.offset(offsetOf(placement, look.rotation()));
            if (mayLay(level, at, placement.part())) {
                level.setBlock(at, stateOf(placement.part(), look, random), PLACE_FLAGS);
            }
        }
        return true;
    }

    /**
     * What one ruin is cut from: one material and one pillar style for the whole ruin, and how it is turned.
     *
     * @param material the material its stone, glyphs and tiles are cut from
     * @param style    the style its pillars are carved in
     * @param rotation how it is turned about its spot
     */
    private record RuinLook(AlienMaterial material, PillarStyle style, Rotation rotation) {
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

    private static <T> T pick(T[] values, RandomSource random) {
        return values[random.nextInt(values.length)];
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

    private static BlockState stateOf(Part part, RuinLook look, RandomSource random) {
        BlockState pillar = GooBlocks.ALIEN_PILLAR.get().defaultBlockState().setValue(AlienPillarBlock.STYLE,
                look.style());
        return switch (part) {
            case PLAIN -> GooBlocks.ALIEN_STONE.get().defaultBlockState()
                    .setValue(AlienMaterialBlock.MATERIAL, look.material());
            case PILLAR -> pillar.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            case PILLAR_LYING -> pillar.setValue(RotatedPillarBlock.AXIS,
                    look.rotation().rotate(Direction.EAST).getAxis());
            case GLYPH -> GooBlocks.ALIEN_GLYPH.get().defaultBlockState()
                    .setValue(AlienMaterialBlock.MATERIAL, look.material())
                    .setValue(AlienGlyphBlock.MARK, pick(GlyphMark.values(), random));
            case TILE -> GooBlocks.ALIEN_TILE.get().defaultBlockState()
                    .setValue(AlienMaterialBlock.MATERIAL, look.material());
            case MARKER -> MonolithMarkerFeature.markerTurned(random);
        };
    }
}
