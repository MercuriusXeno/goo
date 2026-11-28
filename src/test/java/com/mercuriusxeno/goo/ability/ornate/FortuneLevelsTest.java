package com.mercuriusxeno.goo.ability.ornate;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.FortuneStep;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the Fortune level a break or a kill reads from the Fortune goo
 * against it, at the rule ornate_fortune.json names: one level per five
 * layers, three at the most, and which cells beside a block hold on its faces.
 * fortune-is-ornates-residual-that-makes-things-drop-more
 */
class FortuneLevelsTest {

    private static final FortuneStep SHIPPED = FortuneStep.in(AbilityJson.decode("ornate_fortune").behaviors())
            .orElseThrow();

    @Nested
    class ForABreak {

        @Test
        void oneLayerAgainstTheBlockIsLevelOne() {
            assertEquals(1, FortuneLevels.forBreak(face -> face == Direction.UP ? 1 : 0, SHIPPED));
        }

        @Test
        void fifteenLayersAgainstTheBlockIsLevelThree() {
            assertEquals(3, FortuneLevels.forBreak(face -> face == Direction.UP ? 15 : 0, SHIPPED));
        }

        @Test
        void noFortuneGooAgainstTheBlockIsLevelZero() {
            assertEquals(0, FortuneLevels.forBreak(face -> 0, SHIPPED));
        }

        @Test
        void theFullestCellAgainstTheBlockSetsTheLevel() {
            assertEquals(2, FortuneLevels.forBreak(face -> face == Direction.NORTH ? 6 : 2, SHIPPED));
        }
    }

    @Nested
    class FacesHeld {

        private final VolumeCellBlock fortune = mock(VolumeCellBlock.class);

        @Test
        void aCellOnTheBlocksTopHoldsOnIt() {
            assertEquals(4, FortuneLevels.layersOnFace(cell(4, SideCoat.BARE, false), Direction.UP, fortune));
        }

        @Test
        void aCellAttachedToTheBlocksSideHoldsOnIt() {
            BlockState beside = cell(7, SideCoat.BARE, false);
            when(beside.getValue(VolumeCellBlock.SOUTH)).thenReturn(SideCoat.ATTACHED);

            assertEquals(7, FortuneLevels.layersOnFace(beside, Direction.NORTH, fortune));
        }

        @Test
        void aCellBesideTheBlockAttachedElsewhereHoldsNothingOnIt() {
            BlockState beside = cell(7, SideCoat.BARE, false);
            when(beside.getValue(VolumeCellBlock.WEST)).thenReturn(SideCoat.ATTACHED);

            assertEquals(0, FortuneLevels.layersOnFace(beside, Direction.NORTH, fortune));
        }

        @Test
        void aCellHangingFromTheBlocksUndersideHoldsOnIt() {
            assertEquals(3, FortuneLevels.layersOnFace(cell(3, SideCoat.BARE, true), Direction.DOWN, fortune));
        }

        @Test
        void anotherGoosCellHoldsNothing() {
            BlockState other = cell(9, SideCoat.BARE, false);
            when(other.getBlock()).thenReturn(mock(VolumeCellBlock.class));

            assertEquals(0, FortuneLevels.layersOnFace(other, Direction.UP, fortune));
        }

        private BlockState cell(int layers, SideCoat sides, boolean ceiling) {
            BlockState state = mock(BlockState.class);
            Block block = fortune;
            when(state.getBlock()).thenReturn(block);
            when(state.getValue(VolumeCellBlock.LAYERS)).thenReturn(layers);
            when(state.getValue(VolumeCellBlock.CEILING)).thenReturn(ceiling);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                when(state.getValue(VolumeCellBlock.coatProperty(side))).thenReturn(sides);
            }
            return state;
        }
    }
}
