package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.yarn.Led;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which mobs are a caster's own minions and which shipped abilities
 * spare them by the harmful tag (decision mob-targeted-attacks-refuse-your-minions),
 * and which mobs Charm and Lead refuse for a caster.
 */
class OwnMinionsTest {

    private static final UUID CASTER = new UUID(1L, 1L);
    private static final UUID OTHER_PLAYER = new UUID(2L, 2L);
    private static final float STRIKE = 6f;

    @Nested
    class OwnMinionOf {

        @Test
        void aMobTheCasterLedIsTheCastersMinion() {
            assertTrue(OwnMinions.ownMinionOf(CASTER, new Led(CASTER, STRIKE), Charmed.NONE));
        }

        @Test
        void aMobTheCasterCharmedIsTheCastersMinion() {
            assertTrue(OwnMinions.ownMinionOf(CASTER, Led.NONE, new Charmed(CASTER)));
        }

        @Test
        void anotherPlayersMinionIsNotTheCasters() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, new Led(OTHER_PLAYER, STRIKE),
                    new Charmed(OTHER_PLAYER)));
        }

        @Test
        void aMobWithNeitherAttachmentIsNoMinion() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, Led.NONE, Charmed.NONE));
        }

    }

    /** rallied-mobs-are-immune-to-charm, other-players-rally-and-charm-refuse-your-minions */
    @Nested
    class CharmRefuses {

        @Test
        void charmRefusesAMobTheCasterLed() {
            assertTrue(OwnMinions.charmRefuses(CASTER, new Led(CASTER, STRIKE), Charmed.NONE));
        }

        @Test
        void charmRefusesAMobAnotherPlayerLed() {
            assertTrue(OwnMinions.charmRefuses(CASTER, new Led(OTHER_PLAYER, STRIKE), Charmed.NONE));
        }

        @Test
        void charmRefusesAMobAnotherPlayerCharmed() {
            assertTrue(OwnMinions.charmRefuses(CASTER, Led.NONE, new Charmed(OTHER_PLAYER)));
        }

        @Test
        void charmTakesAMobHoldingNeitherEffect() {
            assertFalse(OwnMinions.charmRefuses(CASTER, Led.NONE, Charmed.NONE));
        }

        @Test
        void charmTakesAMobItsCasterCharmed() {
            assertFalse(OwnMinions.charmRefuses(CASTER, Led.NONE, new Charmed(CASTER)));
        }
    }

    /** charmed-mobs-are-immune-to-rally, other-players-rally-and-charm-refuse-your-minions */
    @Nested
    class LeadRefuses {

        @Test
        void leadRefusesAMobTheCasterCharmed() {
            assertTrue(OwnMinions.leadRefuses(CASTER, Led.NONE, new Charmed(CASTER)));
        }

        @Test
        void leadRefusesAMobAnotherPlayerCharmed() {
            assertTrue(OwnMinions.leadRefuses(CASTER, Led.NONE, new Charmed(OTHER_PLAYER)));
        }

        @Test
        void leadRefusesAMobAnotherCasterLed() {
            assertTrue(OwnMinions.leadRefuses(CASTER, new Led(OTHER_PLAYER, STRIKE), Charmed.NONE));
        }

        @Test
        void leadTakesAMobHoldingNeitherEffect() {
            assertFalse(OwnMinions.leadRefuses(CASTER, Led.NONE, Charmed.NONE));
        }

        /** rally-cast-on-your-rallied-mob-releases-it: the leader's own Lead reaches the mob to release it */
        @Test
        void leadTakesAMobTheCasterLed() {
            assertFalse(OwnMinions.leadRefuses(CASTER, new Led(CASTER, STRIKE), Charmed.NONE));
        }
    }

    /**
     * Whom a minion fights for, the player its hit marks a struck mob as
     * (decisions minion-hit-marks-the-struck-mob-as-its-players, charmed-mobs-take-the-rallied-kill-rule).
     */
    @Nested
    class OwnerOf {

        @Test
        void aLedMobFightsForItsLeader() {
            assertEquals(Optional.of(CASTER), OwnMinions.ownerOf(new Led(CASTER, STRIKE), Charmed.NONE));
        }

        @Test
        void aCharmedMobFightsForItsCharmer() {
            assertEquals(Optional.of(CASTER), OwnMinions.ownerOf(Led.NONE, new Charmed(CASTER)));
        }

        @Test
        void aMobLedAndCharmedFightsForItsLeader() {
            assertEquals(Optional.of(CASTER), OwnMinions.ownerOf(new Led(CASTER, STRIKE),
                    new Charmed(OTHER_PLAYER)));
        }

        @Test
        void aMobHoldingNeitherFightsForNoOne() {
            assertEquals(Optional.empty(), OwnMinions.ownerOf(Led.NONE, Charmed.NONE));
        }
    }

    @Nested
    class SparesOwnMinions {

        @Test
        void aHarmfulMobAbilitySparesThem() {
            assertTrue(OwnMinions.sparesOwnMinions(AbilityBadge.MOB, DeliveryKind.ARC, true));
        }

        @Test
        void anUntaggedMobAbilityTargetsThem() {
            assertFalse(OwnMinions.sparesOwnMinions(AbilityBadge.MOB, DeliveryKind.ARC, false));
        }

        /**
         * Dart wears the held badge, prepared on right click and thrown on
         * left, and still spares the caster's minions as a mob ability does
         * (decision dart-forms-on-right-click-and-throws-on-left-click).
         */
        @Test
        void dartWearsTheHeldBadgeAndStillSparesThem() {
            AbilityDefinition dart = AbilityJson.decode("metal_dart");

            assertEquals(AbilityBadge.HELD, dart.badge());
            assertTrue(OwnMinions.sparesOwnMinions(dart.badge(), dart.delivery().kind(),
                    dart.hasTag(AbilityTags.HARMFUL)));
        }

        @Test
        void aHeldChannelOnTheSelfSparesNone() {
            assertFalse(OwnMinions.sparesOwnMinions(AbilityBadge.HELD, DeliveryKind.SELF, true));
        }
    }

    @Nested
    class ShippedHarmfulTags {

        @ParameterizedTest
        @ValueSource(strings = {"metal_dart", "void_cancel", "ender_seal", "leaf_bio",
                "typhoon_shulk", "zoo_spawn"})
        void aHarmfulMobAbilityCarriesTheTag(String name) {
            assertTrue(AbilityJson.decode(name).hasTag(AbilityTags.HARMFUL), name + " should carry the harmful tag");
        }

        @ParameterizedTest
        @ValueSource(strings = {"hex_charm", "zoo_allure", "zoo_shape", "yarn_lead"})
        void anUntaggedAbilityStaysUntagged(String name) {
            assertFalse(AbilityJson.decode(name).hasTag(AbilityTags.HARMFUL), name + " should carry no harmful tag");
        }
    }
}
