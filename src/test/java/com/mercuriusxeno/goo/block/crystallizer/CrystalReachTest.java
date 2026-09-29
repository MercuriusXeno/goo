package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.item.ChrysmTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A sight line reaches a crystal standing above its crystallizer from any side
 * (operator ruling: the crystal's shape is the thing to interact with), a level look
 * included, and the click's point is pulled within the reach the server grants.
 */
class CrystalReachTest {

    private static final BlockPos CRYSTALLIZER = new BlockPos(4, 10, 4);
    /** A budding crystal's box: it rises from the crystallizer's top half a block into the space above. */
    private static final VoxelShape CRYSTAL = Shapes.box(0.3, 1.0, 0.3, 0.7, 1.5, 0.7);

    private static VoxelShape crystalAt(BlockPos pos) {
        return pos.equals(CRYSTALLIZER) ? CRYSTAL : Shapes.empty();
    }

    @Test
    void aLevelLookFromTheSideLandsOnTheCrystalAboveItsCrystallizer() {
        Vec3 eye = new Vec3(1.5, 11.3, 4.5);
        BlockHitResult hit = CrystalReach.nearestCrystalHit(CrystalReachTest::crystalAt, eye, eye.add(5, 0, 0));
        assertNotNull(hit, "a level look through the space above should reach the crystal");
        assertEquals(CRYSTALLIZER, hit.getBlockPos());
        assertEquals(4.3, hit.getLocation().x, 1e-9);
    }

    @Test
    void aLookThatPassesTheCrystalReachesNothing() {
        Vec3 eye = new Vec3(1.5, 11.8, 4.5);
        assertNull(CrystalReach.nearestCrystalHit(CrystalReachTest::crystalAt, eye, eye.add(5, 0, 0)));
    }

    @Test
    void aClickOnTheBuddingCrystalsFaceLandsOnItThoughRoundingPutsItJustOutside() {
        double halfWidth = CrystalCluster.reach(ChrysmTier.BUDDING_CHRYSM.volume())[0];
        double height = CrystalCluster.reach(ChrysmTier.BUDDING_CHRYSM.volume())[1];
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB((8 - halfWidth) / 16, 1,
                (5 - halfWidth) / 16, (8 + halfWidth) / 16, 1 + height / 16, (5 + halfWidth) / 16);
        // The point the server read for a dev-instance click on the budding crystal's north face.
        Vec3 logged = new Vec3(0.50363588333129883, 1.49498808383942, 0.0972417071461678);
        assertEquals(true, logged.z < box.minZ, "the logged point sits a hair outside the face");
        assertEquals(true, CrystalReach.landsOn(box, logged), "a click on the face should land on the crystal");
        assertEquals(false, CrystalReach.landsOn(box, logged.add(0, 0, -1.0 / 16)), "a pixel off misses");
    }

    @Test
    void aClickHighOnATallCrystalIsPulledWithinTheServersReach() {
        BlockHitResult high = new BlockHitResult(new Vec3(4.5, 11.7, 4.3), net.minecraft.core.Direction.NORTH,
                CRYSTALLIZER, false);
        BlockHitResult reachable = CrystalReach.reachableByServer(high);
        double fromCenter = reachable.getLocation().y - Vec3.atCenterOf(CRYSTALLIZER).y;
        assertEquals(true, fromCenter < 1.0000001, "the server refuses a point " + fromCenter + " from the center");
        assertEquals(true, reachable.getLocation().y > CRYSTALLIZER.getY() + 1, "the point stays on the crystal");
    }
}
