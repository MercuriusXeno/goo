package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which mobs are a caster's own minions and which shipped abilities
 * spare them by the harmful tag (decision mob-targeted-attacks-refuse-your-minions).
 */
class OwnMinionsTest {

    private static final UUID CASTER = new UUID(1L, 1L);
    private static final UUID OTHER_PLAYER = new UUID(2L, 2L);
    private static final float STRIKE = 6f;

    @Nested
    class OwnMinionOf {

        @Test
        void aMobTheCasterRalliedIsTheCastersMinion() {
            assertTrue(OwnMinions.ownMinionOf(CASTER, new Rallied(CASTER, STRIKE), Charmed.NONE));
        }

        @Test
        void aMobTheCasterCharmedIsTheCastersMinion() {
            assertTrue(OwnMinions.ownMinionOf(CASTER, Rallied.NONE, new Charmed(CASTER)));
        }

        @Test
        void anotherPlayersMinionIsNotTheCasters() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, new Rallied(OTHER_PLAYER, STRIKE),
                    new Charmed(OTHER_PLAYER)));
        }

        @Test
        void aMobWithNeitherAttachmentIsNoMinion() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, Rallied.NONE, Charmed.NONE));
        }

    }

    /**
     * Whom a minion fights for, the player its hit marks a struck mob as
     * (decisions minion-hit-marks-the-struck-mob-as-its-players, charmed-mobs-take-the-rallied-kill-rule).
     */
    @Nested
    class OwnerOf {

        @Test
        void aRalliedMobFightsForItsRallier() {
            assertEquals(Optional.of(CASTER), OwnMinions.ownerOf(new Rallied(CASTER, STRIKE), Charmed.NONE));
        }

        @Test
        void aCharmedMobFightsForItsCharmer() {
            assertEquals(Optional.of(CASTER), OwnMinions.ownerOf(Rallied.NONE, new Charmed(CASTER)));
        }

        @Test
        void aMobRalliedAndCharmedFightsForItsRallier() {
            assertEquals(Optional.of(CASTER), OwnMinions.ownerOf(new Rallied(CASTER, STRIKE),
                    new Charmed(OTHER_PLAYER)));
        }

        @Test
        void aMobHoldingNeitherFightsForNoOne() {
            assertEquals(Optional.empty(), OwnMinions.ownerOf(Rallied.NONE, Charmed.NONE));
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
        @ValueSource(strings = {"metal_dart", "yore_expire", "void_cancel", "ender_seal", "leaf_bio",
                "leaf_vines", "typhoon_shulk", "zoo_spawn"})
        void aHarmfulMobAbilityCarriesTheTag(String name) {
            assertTrue(AbilityJson.decode(name).hasTag(AbilityTags.HARMFUL), name + " should carry the harmful tag");
        }

        @ParameterizedTest
        @ValueSource(strings = {"hex_charm", "zoo_allure", "zoo_shape", "quantum_phase_other",
                "zoo_rally"})
        void anUntaggedAbilityStaysUntagged(String name) {
            assertFalse(AbilityJson.decode(name).hasTag(AbilityTags.HARMFUL), name + " should carry no harmful tag");
        }
    }
}
