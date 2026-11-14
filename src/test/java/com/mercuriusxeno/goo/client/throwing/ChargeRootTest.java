package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Blast's charge roots at the spot it began on: the crosshair moving on
 * leaves the spheroid's anchor and the release's aimed spot at the first
 * target, and a cancel lets the next charge root afresh
 * (decision blast-roots-to-the-spot-the-cast-began).
 */
class ChargeRootTest {

    private static final BlockPos FIRST_BLOCK = new BlockPos(4, 64, 9);
    private static final BlockPos SECOND_BLOCK = new BlockPos(-7, 63, 2);
    private static final TargetResult FIRST = new TargetResult.BlockTarget(FIRST_BLOCK, Direction.UP, false,
            new Vec3(4.5, 65, 9.5), null);
    private static final TargetResult SECOND = new TargetResult.BlockTarget(SECOND_BLOCK, Direction.NORTH, false,
            new Vec3(-6.5, 63.5, 2), null);
    private static final String UNSTABLE = "goo:unstable";
    private static final String BLAST = "goo:unstable_blast";
    private static final Vec3 PALM = new Vec3(0.4, 65.2, 0.3);

    @Test
    void theAnchorStaysWhereTheChargeBeganAsTheAimMoves() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST);
        root.holdAt(SECOND);

        assertEquals(FIRST, root.anchor(SECOND));
    }

    @Test
    void theReleaseSendsTheRootedSpot() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST);
        root.holdAt(SECOND);

        Optional<GooThrowPayload> sent = GloveThrowSender.chargeTarget(root.anchor(SECOND), UNSTABLE, BLAST, PALM);

        assertEquals(Optional.of(FIRST_BLOCK), sent.map(GooThrowPayload::targetPos));
    }

    @Test
    void aClearedRootFollowsTheNextCharge() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST);
        root.clear();
        root.holdAt(SECOND);

        assertEquals(SECOND, root.anchor(FIRST));
    }
}
