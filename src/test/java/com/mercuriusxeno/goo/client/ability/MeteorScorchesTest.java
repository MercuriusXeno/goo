package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meteo's scorch is an overlay drawn on the crater, never a block changed:
 * it covers exactly the crater's faces open to air within its radius and
 * fades to nothing over its named ticks.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
class MeteorScorchesTest {

    private static final BlockPos IMPACT = new BlockPos(10, 64, -4);
    private static final Vec3 CENTER = Vec3.atCenterOf(IMPACT);
    /** The crater: a ball of air blasted out of solid ground. */
    private static final double CRATER = 2.5;
    private static final double SCORCH_RADIUS = 3.5;
    private static final Predicate<BlockPos> CRATERED = pos -> Vec3.atCenterOf(pos).distanceTo(CENTER) <= CRATER;

    @Test
    void theScorchCoversExactlyTheCratersExposedFaces() {
        Map<BlockPos, Set<Direction>> scorched = MeteorScorches.facesWithin(CENTER, SCORCH_RADIUS, CRATERED).stream()
                .collect(Collectors.toMap(MeteorScorches.ScorchedBlock::pos, block -> Set.copyOf(block.sides())));

        assertEquals(exposedWithin(SCORCH_RADIUS), scorched);
        assertTrue(!scorched.isEmpty(), "the crater's walls scorch");
    }

    @Test
    void theScorchFadesToNothingOverItsTicks() {
        assertEquals(1f, MeteorScorches.strengthAt(0), 0f);
        float halfway = MeteorScorches.strengthAt(MeteorScorches.SCORCH_TICKS / 2);
        assertTrue(halfway > 0f && halfway < 1f, "halfway it shows " + halfway);
        assertEquals(0f, MeteorScorches.strengthAt(MeteorScorches.SCORCH_TICKS), 0f);
        assertEquals(0f, MeteorScorches.strengthAt(MeteorScorches.SCORCH_TICKS * 2L), 0f);
    }

    /**
     * The faces the crater leaves open, found the slow way: every solid
     * block within the radius and each of its sides facing the crater.
     */
    private static Map<BlockPos, Set<Direction>> exposedWithin(double radius) {
        Map<BlockPos, Set<Direction>> exposed = new HashMap<>();
        int reach = (int) Math.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(IMPACT.offset(-reach, -reach, -reach),
                IMPACT.offset(reach, reach, reach))) {
            if (Vec3.atCenterOf(pos).distanceTo(CENTER) > radius || CRATERED.test(pos)) {
                continue;
            }
            Set<Direction> sides = List.of(Direction.values()).stream()
                    .filter(side -> CRATERED.test(pos.relative(side))).collect(Collectors.toSet());
            if (!sides.isEmpty()) {
                exposed.put(pos.immutable(), sides);
            }
        }
        return exposed;
    }
}
