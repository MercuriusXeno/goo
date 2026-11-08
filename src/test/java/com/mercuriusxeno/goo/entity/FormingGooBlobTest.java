package com.mercuriusxeno.goo.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unmade block's chunks converge on the nearest goo blob forming within 3
 * blocks of it, and forming blobs within 3 blocks of each other drift together
 * and merge (decision unmake-converges-near-the-unmade-blocks-into-a-goo-item).
 */
class FormingGooBlobTest {

    private static final Vec3 BLOCK = new Vec3(0.5, 64.5, 0.5);
    /** Ticks two blobs a little under 3 blocks apart are given to meet. */
    private static final int MEETING_TICKS = 200;

    @Nested
    class ChunksConverge {

        @Test
        void theNearestBlobWithinThreeBlocksIsChosen() {
            Vec3 near = BLOCK.add(1, 0, 1);
            Vec3 farther = BLOCK.add(2.5, 0, 0);

            assertEquals(Optional.of(near),
                    FormingGooBlob.nearestWithin(BLOCK, List.of(farther, near), Function.identity()));
        }

        @Test
        void aBlobPastThreeBlocksIsNotChosen() {
            Vec3 past = BLOCK.add(3.2, 0, 0);

            assertEquals(Optional.empty(), FormingGooBlob.nearestWithin(BLOCK, List.of(past), Function.identity()));
        }
    }

    @Nested
    class BlobsDrift {

        @Test
        void twoBlobsWithinThreeBlocksDriftTogetherAndMerge() {
            Vec3 one = BLOCK;
            Vec3 other = BLOCK.add(2.8, 0, 0);
            int ticks = 0;
            while (!FormingGooBlob.merges(one, other) && ticks < MEETING_TICKS) {
                Vec3 oneNext = FormingGooBlob.driftStep(one, List.of(other));
                other = FormingGooBlob.driftStep(other, List.of(one));
                one = oneNext;
                ticks++;
            }

            assertTrue(FormingGooBlob.merges(one, other), "still " + one.distanceTo(other) + " apart");
            assertTrue(one.distanceTo(BLOCK) < 1.5, "one blob drifted " + one.distanceTo(BLOCK));
        }

        @Test
        void twoBlobsPastThreeBlocksStayPut() {
            Vec3 other = BLOCK.add(3.2, 0, 0);

            assertEquals(BLOCK, FormingGooBlob.driftStep(BLOCK, List.of(other)));
        }
    }
}
