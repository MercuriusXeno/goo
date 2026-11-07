package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The aim line ends where the bullseye draws on an aimed mob or block, and on
 * the cursor only for free aim (decision aim-line-roots-to-the-aimed-mob-or-block).
 */
class GooTargetHighlighterTest {

    private static final BlockPos POS = new BlockPos(2, 64, 2);

    @Nested
    class RootedToTheTarget {

        @Test
        void aMobEndsTheLineAtItsBoundingBoxCenter() {
            Entity cow = mock(Entity.class);
            AABB box = new AABB(6.5, 64.0, 0.5, 7.5, 65.4, 1.5);
            when(cow.getBoundingBox()).thenReturn(box);
            Vec3 cursorHit = new Vec3(7.0, 65.3, 1.2);
            TargetResult target = TargetResult.entity(cow, cursorHit);

            assertNotEquals(cursorHit, box.getCenter());
            assertEquals(box.getCenter(), GooTargetHighlighter.aimLineEnd(target));
        }

        @Test
        void aBlockEndsTheLineAtTheStruckFacesCenter() {
            Vec3 cursorHit = new Vec3(2.15, 64.8, 2.0);
            TargetResult target = TargetResult.block(POS, Direction.NORTH, cursorHit);

            assertNotEquals(cursorHit, target.resolveEndpoint());
            assertEquals(target.resolveEndpoint(), GooTargetHighlighter.aimLineEnd(target));
        }
    }

    @Nested
    class FreeAimFollowsTheCursor {

        @Test
        void aPointOnABlockEndsTheLineAtThePoint() {
            Vec3 cursorHit = new Vec3(2.15, 65.0, 2.7);

            assertEquals(cursorHit,
                    GooTargetHighlighter.aimLineEnd(TargetResult.pointOnBlock(cursorHit, POS, Direction.UP)));
        }

        @Test
        void aPointInOpenAirEndsTheLineAtTheRaysEnd() {
            Vec3 reach = new Vec3(40, 90, -30);

            assertEquals(reach, GooTargetHighlighter.aimLineEnd(TargetResult.pointInAir(reach)));
        }

        @Test
        void nothingAimedDrawsNoLine() {
            assertNull(GooTargetHighlighter.aimLineEnd(TargetResult.NONE));
        }
    }
}
