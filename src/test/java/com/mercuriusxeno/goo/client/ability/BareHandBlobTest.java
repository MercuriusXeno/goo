package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A caster with no glove who holds raw goo in the right hand lifts and rests the palm blob as a glove does: the
 * lifted blob stands in the held goo's type straight above that hand, a centered ability's blob rests below
 * the crosshair, and a hand holding neither glove nor raw goo lifts no blob. The gloved hand's answers stand in
 * LiftedPalmBlobTest and BlobRestTest.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
class BareHandBlobTest {

    private static final long NOW = 400L;
    private static final long EASED = NOW + PalmBlobHop.LIFT_TICKS;
    private static final double DELTA = 1e-6;

    private static void sprayEveryTickThroughTheEase() {
        for (long tick = NOW; tick <= EASED; tick++) {
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
        }
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    private static MockedStatic<AbilitySyncHandler> syncedAbility(boolean centered) {
        ClientAbility ability = mock(ClientAbility.class);
        when(ability.hasTag(AbilityTags.CENTERED)).thenReturn(centered);
        MockedStatic<AbilitySyncHandler> synced = mockStatic(AbilitySyncHandler.class);
        synced.when(() -> AbilitySyncHandler.findAbility(SprayingClient.BARE_HAND_ABILITY)).thenReturn(ability);
        return synced;
    }

    @Test
    void aBareHandsLiftedBlobStandsInTheHeldGoosTypeStraightAboveThatHand() {
        try (SprayingClient client = SprayingClient.bareHanded(GooTypes.ASTRAL)) {
            client.at(EASED);
            sprayEveryTickThroughTheEase();

            LiftedPalmBlob.Blob blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();

            assertEquals(GooTypes.ASTRAL, blob.type());
            assertNear(SpraySource.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f), blob.place());
        }
    }

    @Test
    void aBareHandsCenteredAbilityRestsTheBlobBelowTheCrosshair() {
        try (SprayingClient client = SprayingClient.bareHanded(GooTypes.ASTRAL);
             MockedStatic<AbilitySyncHandler> synced = syncedAbility(true)) {
            Vec3 rest = BlobRest.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f);

            assertNear(BlobRest.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true,
                    BlobRest.Rest.CENTER_LINE), rest);
        }
    }

    @Test
    void aBareHandsUncenteredAbilityRestsTheBlobStraightAboveThatHand() {
        try (SprayingClient client = SprayingClient.bareHanded(GooTypes.ASTRAL);
             MockedStatic<AbilitySyncHandler> synced = syncedAbility(false)) {
            Vec3 rest = BlobRest.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f);

            assertNear(SpraySource.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f), rest);
        }
    }

    @Test
    void aHandHoldingNeitherGloveNorRawGooLiftsNoBlob() {
        try (SprayingClient client = SprayingClient.emptyHanded()) {
            client.at(EASED);
            sprayEveryTickThroughTheEase();

            assertEquals(Optional.empty(), LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f));
        }
    }
}
