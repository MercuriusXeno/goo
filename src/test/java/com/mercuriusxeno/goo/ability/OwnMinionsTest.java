package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which mobs are a caster's own minions and which shipped abilities
 * spare them by the harmful tag (decision mob-targeted-attacks-refuse-your-minions).
 */
class OwnMinionsTest {

    private static final UUID CASTER = new UUID(1L, 1L);
    private static final UUID OTHER_PLAYER = new UUID(2L, 2L);
    private static final long NOW = 1000L;
    private static final long LATER = NOW + 100L;
    private static final float STRIKE = 6f;

    @Nested
    class OwnMinionOf {

        @Test
        void aMobTheCasterRalliedIsTheCastersMinion() {
            assertTrue(OwnMinions.ownMinionOf(CASTER, new Rallied(CASTER, LATER, STRIKE), Charmed.NONE, NOW));
        }

        @Test
        void aMobTheCasterCharmedIsTheCastersMinion() {
            assertTrue(OwnMinions.ownMinionOf(CASTER, Rallied.NONE, new Charmed(CASTER), NOW));
        }

        @Test
        void anotherPlayersMinionIsNotTheCasters() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, new Rallied(OTHER_PLAYER, LATER, STRIKE),
                    new Charmed(OTHER_PLAYER), NOW));
        }

        @Test
        void aMobWithNeitherAttachmentIsNoMinion() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, Rallied.NONE, Charmed.NONE, NOW));
        }

        @Test
        void aRallyThatFadedLeavesNoMinion() {
            assertFalse(OwnMinions.ownMinionOf(CASTER, new Rallied(CASTER, NOW, STRIKE), Charmed.NONE, NOW));
        }
    }

    @Nested
    class SparesOwnMinions {

        @Test
        void aHarmfulMobAbilitySparesThem() {
            assertTrue(OwnMinions.sparesOwnMinions(AbilityBadge.MOB, true));
        }

        @Test
        void anUntaggedMobAbilityTargetsThem() {
            assertFalse(OwnMinions.sparesOwnMinions(AbilityBadge.MOB, false));
        }
    }

    @Nested
    class ShippedHarmfulTags {

        @ParameterizedTest
        @ValueSource(strings = {"metal_javelin", "yore_expire", "void_erase", "ender_zone", "leaf_bio",
                "leaf_vines", "typhoon_shulk", "zoo_spawn"})
        void aHarmfulMobAbilityCarriesTheTag(String name) {
            assertTrue(AbilityJson.decode(name).hasTag(AbilityTags.HARMFUL), name + " should carry the harmful tag");
        }

        @ParameterizedTest
        @ValueSource(strings = {"hex_charm", "zoo_allure", "zoo_shape", "quantum_link", "quantum_phase_shift",
                "zoo_rally"})
        void anUntaggedAbilityStaysUntagged(String name) {
            assertFalse(AbilityJson.decode(name).hasTag(AbilityTags.HARMFUL), name + " should carry no harmful tag");
        }
    }
}
