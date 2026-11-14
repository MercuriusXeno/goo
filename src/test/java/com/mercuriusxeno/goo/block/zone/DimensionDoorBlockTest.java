package com.mercuriusxeno.goo.block.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Covers the room doorway cell's shapes: its outline and the shape a player
 * walks into to leave are both the full block, so the doorway stands as the
 * one by one by two cuboid it draws
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class DimensionDoorBlockTest {

    private final DimensionDoorBlock door = mock(DimensionDoorBlock.class, CALLS_REAL_METHODS);

    @Test
    void theOutlineIsTheFullBlock() {
        assertEquals(Shapes.block().toAabbs(), door.getShape(mock(BlockState.class), mock(BlockGetter.class),
                BlockPos.ZERO, mock(CollisionContext.class)).toAabbs(), "the full block");
    }

    @Test
    void theWalkInShapeIsTheFullBlock() {
        assertEquals(Shapes.block().toAabbs(), door.getEntityInsideCollisionShape(mock(BlockState.class),
                mock(BlockGetter.class), BlockPos.ZERO, mock(Entity.class)).toAabbs(), "the full block");
    }
}
