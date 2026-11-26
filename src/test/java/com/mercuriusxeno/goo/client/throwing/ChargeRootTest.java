package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.network.GooChargePayload;
import com.mercuriusxeno.goo.network.GooChargedCastHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast's charge roots at the spot it began on: the crosshair moving on
 * leaves the spheroid's anchor and the release's aimed spot at the first
 * target, the release carries where the caster's eye stood as it rooted,
 * and a cancel lets the next charge root afresh
 * (decisions blast-roots-to-the-spot-the-cast-began, what-starts-in-range-resolves-out-of-range).
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
    /** The caster's eye as the charge roots, ten blocks from the first target, inside Blast's 16. */
    private static final Vec3 EYE_IN_RANGE = new Vec3(4.5, 66.62, -0.5);
    /** The caster's eye at release, walked forty blocks back from the first target, past Blast's 16. */
    private static final Vec3 EYE_BEYOND_RANGE = new Vec3(4.5, 66.62, -30.5);

    @Test
    void theAnchorStaysWhereTheChargeBeganAsTheAimMoves() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST, EYE_IN_RANGE);
        root.holdAt(SECOND, EYE_BEYOND_RANGE);

        assertEquals(FIRST, root.anchor(SECOND));
    }

    @Test
    void theReleaseSendsTheRootedSpot() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST, EYE_IN_RANGE);
        root.holdAt(SECOND, EYE_BEYOND_RANGE);

        Optional<GooThrowPayload> sent = GloveThrowSender.chargeTarget(root.anchor(SECOND), UNSTABLE, BLAST, PALM);

        assertEquals(Optional.of(FIRST_BLOCK), sent.map(GooThrowPayload::targetPos));
    }

    @Test
    void aReleaseFromBeyondRangePassesTheServersGateOnTheEyeAsRooted() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST, EYE_IN_RANGE);
        root.holdAt(SECOND, EYE_BEYOND_RANGE);

        GooChargePayload release = GloveThrowSender.sizedRelease(root, SECOND, UNSTABLE, BLAST, 0, PALM)
                .orElseThrow();

        assertTrue(GooChargedCastHandler.reaches(EYE_BEYOND_RANGE, release.eyeAsRooted(),
                release.aimed().orElseThrow().targetPoint(), AbilityJson.decode("unstable_blast")));
    }

    @Test
    void aClearedRootFollowsTheNextCharge() {
        ChargeRoot root = new ChargeRoot();
        root.holdAt(FIRST, EYE_IN_RANGE);
        root.clear();
        root.holdAt(SECOND, EYE_IN_RANGE);

        assertEquals(SECOND, root.anchor(FIRST));
    }
}
