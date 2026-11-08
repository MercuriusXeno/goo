package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.Variables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast is drag-sized: its JSON wears the drag_sized tag, its explode step
 * reaches the radius dragged (decision blast-is-drag-sized-like-the-black-hole),
 * and its power digs through stone out to that radius, so the blast fills
 * the ring it draws (decision blast-fills-its-drawn-radius).
 */
class BlastSizeTest {

    private static final double TOLERANCE = 1e-6;
    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);
    private static final BlockPos ORIGIN = BlockPos.containing(CENTER);
    private static final float STONE = 6.0F;

    private static ExplodeStep blast() {
        return AbilityJson.decode("unstable_explode").behaviors().stream()
                .filter(ExplodeStep.class::isInstance).map(ExplodeStep.class::cast).findFirst().orElseThrow();
    }

    @Test
    void blastIsDragSizedAndNeverThrownAtAnEntity() {
        AbilityDefinition blast = AbilityJson.decode("unstable_explode");

        assertTrue(blast.hasTag(AbilityTags.DRAG_SIZED));
        assertFalse(blast.hasTag(AbilityTags.ENTITY));
        assertEquals(AbilityArea.NONE, blast.area(), "the drag draws the ghost, not a resting area");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 3, 6})
    void theExplosionsReachIsTheRadiusDragged(double size) {
        assertEquals(size, blast().reachAt(HostVariables.sized(size)), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 6})
    void aBlastDraggedIntoStoneBreaksTheStoneAtItsRing(int size) {
        Variables sized = HostVariables.sized(size);
        float power = blast().power().evaluateFloat(sized);

        Set<BlockPos> broken = ExplosionMarch.markedCells(CENTER, power, blast().reachAt(sized),
                pos -> Optional.of(STONE));

        for (Direction direction : Direction.values()) {
            assertTrue(broken.contains(ORIGIN.relative(direction, size)), direction.getName());
            assertFalse(broken.contains(ORIGIN.relative(direction, size + 1)), direction.getName());
        }
    }

    /**
     * A blast of any size breaks every stone cell whose center lies within
     * its ring and none past it, rather than leaving the striations its rays
     * miss once they spread past a block apart (decision blast-fills-its-drawn-radius).
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
    void aBlastBreaksEveryStoneCellInsideItsRingAndNonePast(int size) {
        Variables sized = HostVariables.sized(size);
        double reach = blast().reachAt(sized);
        Set<BlockPos> broken = ExplosionMarch.markedCells(CENTER, blast().power().evaluateFloat(sized), reach,
                pos -> Optional.of(STONE));

        List<BlockPos> standing = new ArrayList<>();
        int span = size + 1;
        for (BlockPos cell : BlockPos.betweenClosed(ORIGIN.offset(-span, -span, -span), ORIGIN.offset(span, span, span))) {
            if (Vec3.atCenterOf(cell).distanceTo(CENTER) <= reach && !broken.contains(cell)) {
                standing.add(cell.immutable());
            }
        }
        List<BlockPos> past = broken.stream().filter(cell -> Vec3.atCenterOf(cell).distanceTo(CENTER) > reach)
                .toList();
        assertTrue(standing.isEmpty(), standing.size() + " stone cells stand inside the ring, first "
                + standing.stream().limit(5).map(BlockPos::toShortString).toList());
        assertTrue(past.isEmpty(), past.size() + " cells broke past the ring");
    }
}
