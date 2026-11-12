package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.ChainPlacementRules.CandidateState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A blob striking a block face where neither the struck block nor the cell beyond the face takes it lands in the
 * struck block's free neighbor nearest the struck point, as on the side of a fence arm looking onto the next fence
 * block (decision ability-path-uses-placement-rules).
 */
class LandingSpotTest {

    private static final CandidateState OCCUPIED = new CandidateState(false, false, false, false, false);
    private static final CandidateState AIR = new CandidateState(true, true, false, false, false);
    /** The corner post, fences on its east and south arms. */
    private static final BlockPos CORNER = new BlockPos(2, 1, 2);
    /** On the south side of the east arm, inside the corner's own block, nearer its top than its west face. */
    private static final Vec3 INSIDE_THE_CORNER = new Vec3(2.8, 1.6, 2.5625);

    @Test
    void aBlobStrikingAFenceCornerLandsInTheFreeNeighborNearestThePoint() {
        Set<BlockPos> free = Set.of(CORNER.above(), CORNER.west());

        Optional<LandingSpot> spot = LandingSpot.resolve(pos -> free.contains(pos) ? AIR : OCCUPIED, CORNER,
                Direction.SOUTH, INSIDE_THE_CORNER);

        assertEquals(Optional.of(new LandingSpot(CORNER.above(), false)), spot);
    }

    /** A blob striking a replaceable block, as grass on soil, lands in that block's own cell. */
    @Test
    void aBlobStrikingAReplaceableBlockLandsInItsOwnCell() {
        CandidateState grass = new CandidateState(false, true, false, false, false);

        Optional<LandingSpot> spot = LandingSpot.resolve(pos -> pos.equals(CORNER) ? grass : AIR, CORNER,
                Direction.UP, Vec3.atCenterOf(CORNER));

        assertEquals(Optional.of(new LandingSpot(CORNER, false)), spot);
    }
}
