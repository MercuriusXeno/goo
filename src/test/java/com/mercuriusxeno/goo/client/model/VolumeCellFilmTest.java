package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the film a volume cell's model draws on a side holding one layer:
 * a slab one layer thick against that side's wall, from the wall's plane to
 * one layer out, in the goo's layer texture under the shell tint
 * (decision goo-cell-holds-a-fill-and-a-state-per-side).
 */
class VolumeCellFilmTest {

    private static final BlockPos CELL = new BlockPos(4, 64, 4);
    private static final float ONE_LAYER = 1f / VolumeCellBlock.MAX_LAYERS;
    private static final float EPSILON = 1e-5f;
    /** How near its wall a streak lies: within the edge drips' hair, a fraction of a layer. */
    private static final float STREAK_REACH = 0.25f / 16f;
    /** The block's volume a test draws; a lone attached side with no floor draws all of it. */
    private int volume = 1;
    /** Whether the drawn block is attached to its ceiling. */
    private boolean ceiling;

    private final BakedQuad.MaterialInfo film = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
            ChunkSectionLayer.TRANSLUCENT, null, 0, true, 0, false);

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void aSideHoldingOneLayerDrawsASlabOneLayerThickOnItsWall(Direction wall) {
        List<BakedQuad> quads = filmQuadsOf(wall);

        assertFalse(quads.isEmpty(), "a film on the " + wall + " wall draws nothing");
        float plane = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : 0f;
        float out = plane - wall.getAxisDirection().getStep() * ONE_LAYER;
        boolean outerFace = false;
        for (BakedQuad quad : quads) {
            assertEquals(film, quad.materialInfo(), "the film draws in the goo's layer texture");
            float along = coordinateOn(wall.getAxis(), quad.position(0));
            for (int vertex = 0; vertex < 4; vertex++) {
                float at = coordinateOn(wall.getAxis(), quad.position(vertex));
                assertEquals(along, at, EPSILON, wall + " quad should lie flat against its wall");
                assertTrue(Math.abs(at - plane) < EPSILON || Math.abs(at - out) < EPSILON,
                        wall + " quad at " + at + " should lie on the wall's plane " + plane + " or one layer out");
            }
            outerFace |= Math.abs(along - out) < EPSILON && quad.direction() == wall.getOpposite();
        }
        assertTrue(outerFace, wall + " film should face out of its wall one layer out");
    }

    /**
     * A runny side draws a streak flat on its wall's plane, a hair out from
     * it as the edge drips stand, in the drips' texture, the goo's fluid
     * sprite under the shell tint (decision runny-goo-runs-down-the-wall-to-the-first-top-below).
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void aRunnySideDrawsAStreakOnItsWall(Direction wall) {
        List<BakedQuad> quads = quadsOf(wall, SideCoat.RUNNY);

        assertFalse(quads.isEmpty(), "a runny side on the " + wall + " wall draws nothing");
        float plane = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : 0f;
        for (BakedQuad quad : quads) {
            assertEquals(film, quad.materialInfo(), "the streak draws in the drips' texture");
            assertEquals(wall.getOpposite(), quad.direction(), "the streak faces out of its wall");
            for (int vertex = 0; vertex < 4; vertex++) {
                float at = coordinateOn(wall.getAxis(), quad.position(vertex));
                assertTrue(Math.abs(at - plane) < STREAK_REACH && Math.abs(at - plane) > 0,
                        wall + " streak at " + at + " should lie on the wall's plane " + plane + ", clear of it");
            }
        }
    }

    /**
     * A block of volume 8 with no floor attached to its north and south walls
     * draws each wall's half, four layers, as a slab standing four layers'
     * depth from its wall (decision attached-goo-draws-per-attachment-and-divides-its-volume).
     */
    @Test
    void aBlockWithNoFloorDrawsEachWallsShareOfItsVolume() {
        volume = 8;
        List<BakedQuad> quads = quadsOf(List.of(Direction.NORTH, Direction.SOUTH), SideCoat.ATTACHED);

        assertTrue(quads.stream().anyMatch(quad -> quad.direction() == Direction.SOUTH
                        && Math.abs(quad.position(0).z() - 4 * ONE_LAYER) < EPSILON),
                "the north wall's slab should face out four layers from its wall");
    }

    /**
     * A block of volume 6 attached to its ceiling alone, with no floor, draws
     * its whole volume down from the underside: a face looking down six
     * layers' depth below the block's top
     * (decision attached-goo-draws-per-attachment-and-divides-its-volume).
     */
    @Test
    void aBlockOnACeilingDrawsItsVolumeDownFromTheUnderside() {
        volume = 6;
        ceiling = true;
        List<BakedQuad> quads = quadsOf(List.of(), SideCoat.BARE);

        assertTrue(quads.stream().anyMatch(quad -> quad.direction() == Direction.DOWN
                        && Math.abs(quad.position(0).y() - (1 - 6 * ONE_LAYER)) < EPSILON),
                "the ceiling's slab should look down six layers below the block's top");
    }

    private List<BakedQuad> filmQuadsOf(Direction wall) {
        return quadsOf(wall, SideCoat.ATTACHED);
    }

    private List<BakedQuad> quadsOf(Direction wall, SideCoat coat) {
        return quadsOf(List.of(wall), coat);
    }

    private List<BakedQuad> quadsOf(List<Direction> walls, SideCoat coat) {
        VolumeCellBlock goo = mock(VolumeCellBlock.class);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(goo);
        when(state.is(goo)).thenReturn(true);
        when(state.getValue(VolumeCellBlock.LAYERS)).thenReturn(volume);
        when(state.getValue(VolumeCellBlock.CEILING)).thenReturn(ceiling);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            when(state.getValue(VolumeCellBlock.coatProperty(side))).thenReturn(walls.contains(side) ? coat
                    : SideCoat.BARE);
        }
        BlockAndTintGetter level = mock(BlockAndTintGetter.class);
        when(level.getBlockState(any())).thenReturn(mock(BlockState.class));
        BakedQuad.MaterialInfo core = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, 1, true, 0, false);
        SlimeCoreModel model = new SlimeCoreModel(mock(BlockStateModel.class), core, film, Map.of());
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, CELL, state, RandomSource.create(1L), parts);
        return parts.stream().flatMap(part -> part.getQuads(null).stream())
                .filter(quad -> quad.materialInfo() == film).toList();
    }

    private static float coordinateOn(Direction.Axis axis, Vector3fc position) {
        return switch (axis) {
            case X -> position.x();
            case Y -> position.y();
            case Z -> position.z();
        };
    }
}
