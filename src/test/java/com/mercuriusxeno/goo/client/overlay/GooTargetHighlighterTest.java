package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The aim line ends where the bullseye draws on an aimed mob or block, and on
 * the cursor only for free aim (decision aim-line-roots-to-the-aimed-mob-or-block).
 */
class GooTargetHighlighterTest {

    /** The vanilla crosshair gives way only to a reticule that shows. */
    @Nested
    class VanillaCrosshair {

        @Test
        void givesWayToTheReticuleDrawnInTheClearWorld() {
            assertTrue(GooTargetHighlighter.hidesVanillaCrosshair(true, 0f));
        }

        @Test
        void staysWhileSculkersSensesDimTheReticuleAway() {
            assertFalse(GooTargetHighlighter.hidesVanillaCrosshair(true, 1f));
        }

        @Test
        void staysWhereNoReticuleWasDrawn() {
            assertFalse(GooTargetHighlighter.hidesVanillaCrosshair(false, 0f));
        }
    }

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

        @Test
        void aCursorSwayingAcrossAMobLeavesTheLineEndPut() {
            Entity cow = mock(Entity.class);
            when(cow.getBoundingBox()).thenReturn(new AABB(6.5, 64.0, 0.5, 7.5, 65.4, 1.5));

            assertEquals(GooTargetHighlighter.aimLineEnd(TargetResult.entity(cow, new Vec3(6.6, 64.2, 0.6))),
                    GooTargetHighlighter.aimLineEnd(TargetResult.entity(cow, new Vec3(7.4, 65.3, 1.4))));
        }

        @Test
        void aCursorSwayingAcrossAFaceLeavesTheLineEndPut() {
            assertEquals(
                    GooTargetHighlighter.aimLineEnd(TargetResult.block(POS, Direction.NORTH, new Vec3(2.1, 64.1, 2.0))),
                    GooTargetHighlighter.aimLineEnd(TargetResult.block(POS, Direction.NORTH, new Vec3(2.9, 64.9, 2.0))));
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

    /** A drag-sized ability is never thrown, so it draws no throw arc (decision blast-draws-no-throw-arc). */
    @Nested
    class DrawsTheThrowArc {

        private static boolean drawsAimLine(String abilityName) {
            AbilityDefinition ability = AbilityJson.decode(abilityName);
            return GooTargetHighlighter.drawsAimLine(ability.delivery(), ability.hasTag(AbilityTags.DRAG_SIZED));
        }

        @Test
        void blastDrawsNoArc() {
            assertFalse(drawsAimLine("unstable_explode"));
        }

        @Test
        void theBlackHoleDrawsNoArc() {
            assertFalse(drawsAimLine("nether_black_hole"));
        }

        @Test
        void aThrownBlobDrawsItsArc() {
            assertTrue(drawsAimLine("frost_orb"));
        }
    }
}
