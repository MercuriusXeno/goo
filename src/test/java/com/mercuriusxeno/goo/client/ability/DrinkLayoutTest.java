package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's layout is laid once: the first block runs to the glove, a later
 * block joins the standing stream nearest it a little toward the hand, and a
 * laid block never moves however the glove moves
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkLayoutTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 GLOVE_MOVED = new Vec3(1.5, 3.2, 1.1);
    private static final BlockPos NEAR = new BlockPos(7, 2, 3);
    private static final BlockPos FAR = new BlockPos(7, 3, 4);
    private static final BlockPos LATER = new BlockPos(7, 1, 2);

    @Test
    void theFirstBlockRunsToTheGloveFromHalfASpanBehindItsMiddle() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(NEAR), GLOVE);

        DrinkLayout.Node node = layout.node(NEAR);
        Vec3 center = Vec3.atCenterOf(NEAR);
        assertNull(node.trunk());
        assertEquals(DrinkStream.BLOCK_SPAN / 2, node.farSide().distanceTo(center), DELTA);
        assertTrue(center.subtract(node.farSide()).dot(GLOVE.subtract(center)) > 0);
        assertEquals(0, layout.lineOf(node, GLOVE).to().distanceTo(GLOVE), DELTA);
    }

    @Test
    void aFartherBlockJoinsTheNearerStreamALittleTowardTheHand() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(FAR, NEAR), GLOVE);

        DrinkLayout.Node trunk = layout.node(NEAR);
        DrinkLayout.Node tributary = layout.node(FAR);
        DrinkLayout.Line line = layout.lineOf(trunk, GLOVE);

        assertEquals(List.of(trunk, tributary), List.copyOf(layout.nodes()));
        assertEquals(NEAR, tributary.trunk());
        assertEquals(line.nearestDistance(Vec3.atCenterOf(FAR)) + DrinkLayout.LEAD, tributary.joinAt(), DELTA);
        assertEquals(0, layout.lineOf(tributary, GLOVE).to().distanceTo(line.pointAt(tributary.joinAt())), DELTA);
    }

    @Test
    void aLaidBlockNeverMovesHoweverTheGloveMoves() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(FAR, NEAR), GLOVE);
        List<DrinkLayout.Node> laid = List.copyOf(layout.nodes());

        layout.place(List.of(FAR, NEAR), GLOVE_MOVED);

        assertEquals(laid, List.copyOf(layout.nodes()));
    }

    @Test
    void thePullFollowsTheLookWithALagAndSettlesAgainstIt() {
        DrinkLayout layout = new DrinkLayout();
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 north = new Vec3(0, 0, -1);

        Vec3 first = layout.pullToward(east, 0);
        Vec3 soon = layout.pullToward(north, 1);
        Vec3 settled = layout.pullToward(north, 1 + 20 * DrinkLayout.PULL_LAG);

        assertEquals(0, first.distanceTo(east.scale(-1)), DELTA);
        assertTrue(soon.dot(east.scale(-1)) > 0.5, "a tick later the pull still mostly faces the old look");
        assertTrue(soon.dot(north.scale(-1)) > 0, "and has begun to turn");
        assertEquals(0, settled.distanceTo(north.scale(-1)), 1e-6);
        assertEquals(1, soon.length(), 1e-9);
    }

    @Test
    void aBlockAppearingLaterJoinsTheStandingLayout() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(NEAR), GLOVE);
        layout.place(List.of(NEAR, LATER), GLOVE_MOVED);

        DrinkLayout.Node later = layout.node(LATER);

        assertEquals(NEAR, later.trunk());
        assertEquals(2, layout.nodes().size());
        assertTrue(later.joinAt() > 0);
    }
}
