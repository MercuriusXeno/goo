package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pulse stream reaches the cell under the crosshair however narrow its cone
 * (decisions pulser-toggles-rapidly-while-held, zap-disperses-into-signal).
 */
class AimedCellsTest {

    private static final double CONE = 12;
    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    /** A point in the corner of a cell seven blocks out, far off its center. */
    private static final Vec3 CORNER_OF_A_FAR_CELL = new Vec3(5.95, 0.05, 5.05);
    private static final BlockPos FAR_CELL = new BlockPos(5, 0, 5);

    @Test
    void theConeAloneMissesAFarCellAimedAtNearItsCorner() {
        Vec3 reach = EYE.add(CORNER_OF_A_FAR_CELL.subtract(EYE).normalize().scale(8));
        assertFalse(CalcifyStep.blocksInCone(EYE, reach, CONE).contains(FAR_CELL));
    }

    @Test
    void theAimedCellIsReachedWhereverTheAimCrossesIt() {
        Vec3 reach = EYE.add(CORNER_OF_A_FAR_CELL.subtract(EYE).normalize().scale(8));
        assertTrue(AimedCells.along(EYE, reach, CONE).contains(FAR_CELL));
    }

    @Test
    void theAimLinesCellsComeNearestFirst() {
        List<BlockPos> line = AimedCells.onTheLine(new Vec3(0.5, 0.5, 0.5), new Vec3(3.5, 0.5, 0.5));
        assertTrue(line.equals(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0))));
    }
}
