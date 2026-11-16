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

    private List<BakedQuad> filmQuadsOf(Direction wall) {
        VolumeCellBlock goo = mock(VolumeCellBlock.class);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(goo);
        when(state.is(goo)).thenReturn(true);
        when(state.getValue(VolumeCellBlock.LAYERS)).thenReturn(0);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            when(state.getValue(VolumeCellBlock.coatProperty(side))).thenReturn(side == wall ? SideCoat.FILM
                    : SideCoat.BARE);
        }
        BlockAndTintGetter level = mock(BlockAndTintGetter.class);
        when(level.getBlockState(any())).thenReturn(mock(BlockState.class));
        SlimeCoreModel model = new SlimeCoreModel(mock(BlockStateModel.class), film, film, Map.of());
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, CELL, state, RandomSource.create(1L), parts);
        return parts.stream().flatMap(part -> part.getQuads(null).stream()).toList();
    }

    private static float coordinateOn(Direction.Axis axis, Vector3fc position) {
        return switch (axis) {
            case X -> position.x();
            case Y -> position.y();
            case Z -> position.z();
        };
    }
}
