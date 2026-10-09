package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A point of the skin reads its place on the texture from the stream it is
 * on: along the liquid at its foot on the spine, and round the spine by arc
 * length in the path's fixed frame; a point on the standing block reads two
 * world axes square to the skin's facing, and knows it is on the block
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkTextureTest {

    private static final double DELTA = 1e-9;
    private static final double RADIUS = 0.2;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    /** A glove due west of the block, so the path runs along -x and its side runs along -z. */
    private static final Vec3 GLOVE = new Vec3(1.5, 2.5, 3.5);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), CENTER, 1, 0, 0, 100);
    private static final Vec3 FROM = new Vec3(8, 2.5, 3.5);
    private static final Vec3 TO = new Vec3(6, 2.5, 3.5);
    private static final double TO_MATERIAL = 2;
    private static final Vec3 BOX_CENTER = new Vec3(7.5, 6.5, 3.5);

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    private static DrinkStream.Ring ring(Vec3 center, double material) {
        return new DrinkStream.Ring(center, EAST.reverse(), RADIUS, material, 0, DrinkStream.FLOW);
    }

    private static DrinkField.Skeleton capsule(DrinkBody.Box box) {
        return new DrinkField.Skeleton(stream(), List.of(ring(FROM, 0), ring(TO, TO_MATERIAL)), box);
    }

    @Test
    void aPointOnTheStreamReadsTheMaterialAtItsFootAndItsArcRoundTheSpine() {
        DrinkField.Skeleton capsule = capsule(null);
        Vec3 middle = FROM.lerp(TO, 0.5);

        DrinkTexture.Place aside = DrinkTexture.onStream(capsule, middle.add(DrinkStream.sideOf(
                capsule.stream().path()).scale(RADIUS)));
        DrinkTexture.Place above = DrinkTexture.onStream(capsule, middle.add(UP.scale(RADIUS)));
        DrinkTexture.Place nearEnd = DrinkTexture.onStream(capsule, TO.add(UP.scale(RADIUS)));

        assertEquals(TO_MATERIAL / 2, aside.along(), DELTA);
        assertEquals(0, aside.around(), DELTA);
        assertEquals(TO_MATERIAL / 2, above.along(), DELTA);
        assertEquals(Math.PI / 2 * RADIUS, Math.abs(above.around()), DELTA);
        assertEquals(TO_MATERIAL, nearEnd.along(), DELTA);
    }

    @Test
    void theFootIsTheShareOfTheSegmentNearestThePointClampedToIt() {
        assertEquals(0.5, DrinkTexture.footOf(FROM, TO, FROM.lerp(TO, 0.5).add(UP)), DELTA);
        assertEquals(0, DrinkTexture.footOf(FROM, TO, FROM.add(EAST)), DELTA);
        assertEquals(1, DrinkTexture.footOf(FROM, TO, TO.subtract(EAST)), DELTA);
        assertEquals(0, DrinkTexture.footOf(FROM, FROM, TO), DELTA);
    }

    @Test
    void aPointKnowsWhetherItIsOnTheStandingBlockOrTheStream() {
        DrinkBody.Box box = new DrinkBody.Box(BOX_CENTER, 0.5, 0);
        DrinkField.Skeleton withBlock = capsule(box);

        assertTrue(DrinkTexture.onBlock(withBlock, BOX_CENTER.add(0.5, 0.1, 0.2)));
        assertFalse(DrinkTexture.onBlock(withBlock, FROM.lerp(TO, 0.5).add(UP.scale(RADIUS))));
        assertFalse(DrinkTexture.onBlock(capsule(null), BOX_CENTER));
    }

    @Test
    void aPointOnTheBlockReadsTheTwoWorldAxesSquareToTheFacing() {
        Vec3 point = new Vec3(1.25, 2.5, 3.75);

        assertEquals(new DrinkTexture.Place(point.x, point.z), DrinkTexture.onBlock(point, UP));
        assertEquals(new DrinkTexture.Place(point.z, point.y), DrinkTexture.onBlock(point, EAST));
        assertEquals(new DrinkTexture.Place(point.x, point.y), DrinkTexture.onBlock(point, SOUTH));
    }
}
