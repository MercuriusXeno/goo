package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.client.ability.CameraEye;
import com.mercuriusxeno.goo.client.ability.CastOrigin;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.PalmBlobHop;
import com.mercuriusxeno.goo.client.ability.SprayingClient;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the affordability gate a glove press passes before any swing, packet
 * or sound: the cost at the aimed marker's stack position against the
 * player's holdings (decisions unaffordable-click-does-nothing, flat-cost-per-throw).
 */
class GloveThrowSenderTest {

    private static final int BLINK_TRIP = 8;

    private static ClientAbility clientAbility(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost().in(GooValueTable.EMPTY), definition.delivery(),
                definition.badge(), definition.requires());
    }

    private static boolean affordsWithHoldings(ClientAbility ability, int holdings) {
        return GloveThrowSender.affordsThrow(ability, amount -> holdings >= amount, item -> true);
    }

    /**
     * A throw carries the cast origin as the start the server sends for the
     * flight, the beam and Zap's bolt, and as the glove hand Click's wave
     * leaves: the lifted palm blob where it stands as right click lets go.
     * every-ability-casts-from-the-lifted-palm-blob
     */
    @Nested
    class ThrowsLeaveTheLiftedBlob {

        private static final long RELEASED = 412L;

        @Test
        void aThrowLetGoWhileTheBlobStandsLiftedStartsAtTheCastOrigin() {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                client.at(RELEASED);
                for (long tick = RELEASED - PalmBlobHop.LIFT_TICKS; tick < RELEASED; tick++) {
                    LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
                }

                Vec3 blob = LiftedPalmBlob.placeOf(SprayingClient.CASTER, 0f).orElseThrow();

                assertEquals(blob, GloveThrowSender.throwOrigin());
                assertEquals(CastOrigin.ofLocal(Minecraft.getInstance()), GloveThrowSender.throwOrigin());
            }
        }
    }

    /**
     * The cursor's two points as the player sneaks: the drawn one follows the
     * camera in first person, the one sent to the server stays on what the
     * server computes from, vanilla's pick or the entity eye
     * (decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak).
     */
    @Nested
    class CursorPointsOnSneak {

        private static final double REACH = 4.5;
        private static final Vec3 LOOK = new Vec3(0, -0.3, 1).normalize();
        private static final CameraEye.Sight CROUCHED_EYE = new CameraEye.Sight(new Vec3(0, 65.27, 0), LOOK);
        private static final CameraEye.ViewClip OPEN_AIR =
                (from, to) -> BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        private static final BlockHitResult PICKED_FACE =
                new BlockHitResult(new Vec3(0, 64, 3), Direction.UP, new BlockPos(0, 63, 3), false);

        private static GloveThrowSender.CursorPoints pointsWithCameraAt(double eyeHeight, boolean firstPerson,
                                                                         @Nullable HitResult picked) {
            return GloveThrowSender.cursorPoints(firstPerson, new CameraEye.Sight(new Vec3(0, 64 + eyeHeight, 0),
                    LOOK), CROUCHED_EYE, picked, REACH, OPEN_AIR);
        }

        @Test
        void theSentPointInOpenAirStaysOnTheEntityEyeWhateverTheCameraHeight() {
            Vec3 fromTheEye = CROUCHED_EYE.eye().add(LOOK.scale(REACH));

            assertAll(() -> assertEquals(fromTheEye, pointsWithCameraAt(1.62, true, null).sent()),
                    () -> assertEquals(fromTheEye, pointsWithCameraAt(1.47, true, null).sent()));
        }

        @Test
        void theSentPointOnAPickedFaceStaysOnVanillasPickWhateverTheCameraHeight() {
            assertAll(() -> assertEquals(PICKED_FACE.getLocation(), pointsWithCameraAt(1.62, true, PICKED_FACE).sent()),
                    () -> assertEquals(PICKED_FACE.getLocation(), pointsWithCameraAt(1.47, true, PICKED_FACE).sent()));
        }

        @Test
        void theDrawnPointInFirstPersonFollowsTheCamera() {
            assertEquals(0.15, pointsWithCameraAt(1.62, true, null).drawn().y
                    - pointsWithCameraAt(1.47, true, null).drawn().y, 1e-9);
        }

        @Test
        void theDrawnPointOutsideFirstPersonIsTheSentPoint() {
            GloveThrowSender.CursorPoints points = pointsWithCameraAt(1.62, false, PICKED_FACE);

            assertEquals(points.sent(), points.drawn());
        }
    }

    @Test
    void holdingsOneShortOfTheCostRefuseTheThrow() {
        ClientAbility mine = clientAbility("unstable_lurker");

        assertFalse(affordsWithHoldings(mine, mine.cost() - 1));
    }

    @Test
    void holdingsCoveringTheCostAllowTheThrow() {
        ClientAbility mine = clientAbility("unstable_lurker");

        assertTrue(affordsWithHoldings(mine, mine.cost()));
    }

    // decision ability-json-names-its-reagent
    @Test
    void aMissingReagentRefusesTheThrowTheGooCovers() {
        AbilityDefinition definition = AbilityJson.decode("hex_enchant");
        ClientAbility enchant = new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost().in(GooValueTable.EMPTY),
                definition.delivery(), definition.badge(), definition.requires(), definition.area(),
                definition.indicator(), definition.consumes());

        assertFalse(GloveThrowSender.affordsThrow(enchant, amount -> true, item -> false));
        assertTrue(GloveThrowSender.affordsThrow(enchant, amount -> true, definition.consumes()::contains));
    }

    /**
     * The HUD figure and the affordability gate read one price: the JSON's
     * flat cost plus its per-block amount for the trip plus its wall
     * surcharge (decision blink-lands-safely-costed-by-distance).
     */
    @Test
    void blinkPricesItsTripAsItsJsonNames() {
        AbilityDefinition definition = AbilityJson.decode("ender_blink");
        ClientAbility blink = new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost().in(GooValueTable.EMPTY),
                definition.delivery(), definition.badge(), definition.requires(), definition.area(),
                definition.indicator(), definition.consumes(), definition.upkeep().in(GooValueTable.EMPTY), definition.distancePrice());
        Optional<BlinkLanding> trip = Optional.of(new BlinkLanding(Vec3.ZERO, BLINK_TRIP, true));
        int priced = definition.cost().in(GooValueTable.EMPTY) + BLINK_TRIP * definition.distancePrice().perBlock()
                + definition.distancePrice().wallCost();

        assertTrue(definition.distancePrice().perBlock() > 0 && definition.distancePrice().wallCost() > 0);
        assertEquals(priced, GloveThrowSender.throwCostOf(blink, trip));
        assertEquals(GooFormat.formatAmount(priced), blink.costLabel(trip));
        assertFalse(GloveThrowSender.affordsThrow(blink, trip, amount -> priced - 1 >= amount, item -> true));
        assertTrue(GloveThrowSender.affordsThrow(blink, trip, amount -> priced >= amount, item -> true));
    }

    @Test
    void unsyncedAbilityPricesAtTheServerFallback() {
        assertFalse(GloveThrowSender.affordsThrow(null, amount -> GooThrowHandler.THROW_COST - 1 >= amount, item -> true));
        assertTrue(GloveThrowSender.affordsThrow(null, amount -> GooThrowHandler.THROW_COST >= amount, item -> true));
    }
}
