package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Crush's step blasts a crater: every breakable block whose center stands
 * within its radius of the landing point breaks, throwing debris, and none
 * past it; its force damage lands on the one mob nearest the landing point
 * (decision crush-blob-breaks-along-its-strike).
 */
class CrushStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "bore_breakable"));
    private static final double RADIUS = 2;
    /** The top face of the block at (0, 63, 0), where the blob lands. */
    private static final Vec3 LANDING = new Vec3(0.5, 64, 0.5);
    private static final BlockPos STRUCK = new BlockPos(0, 63, 0);

    @Test
    void theCraterHoldsTheBlocksWithinItsRadius() {
        List<BlockPos> crater = CrushStep.craterCells(LANDING, RADIUS);
        assertTrue(crater.contains(STRUCK));
        assertTrue(crater.contains(STRUCK.below()));
        assertTrue(crater.contains(STRUCK.east()));
        assertFalse(crater.contains(STRUCK.below(2)));
        assertFalse(crater.contains(STRUCK.east(2)));
        assertFalse(crater.contains(STRUCK.east().below().north()));
    }

    @Test
    void landingBreaksAndThrowsDebrisFromTheCraterAlone() {
        AnchoredWorldHost host = mock(AnchoredWorldHost.class, withSettings().extraInterfaces(BlockBreakHost.class));
        BlockBreakHost blocks = (BlockBreakHost) host;
        when(host.kind()).thenReturn(HostKind.LANDING);
        when(host.anchor()).thenReturn(LANDING);
        when(blocks.blockIn(any(), any())).thenReturn(true);

        ProgramBehavior.forHost(List.of(new CrushStep(BREAKABLE, RADIUS, Expr.literal(6))), HostKind.LANDING)
                .tick(host);

        verify(blocks).throwDebris(STRUCK);
        verify(blocks).breakBlock(STRUCK);
        verify(blocks, never()).breakBlock(STRUCK.east(2));
    }

    @Test
    void theStrikeLandsOnTheNearestAlone() {
        assertEquals(Optional.of("on it"), CrushStep.nearest(List.of("at the edge", "on it", "beside it"),
                which -> switch (which) {
                    case "on it" -> 0.1;
                    case "beside it" -> 0.6;
                    default -> 0.9;
                }));
        assertEquals(Optional.empty(), CrushStep.nearest(List.<String>of(), which -> 0));
    }
}
