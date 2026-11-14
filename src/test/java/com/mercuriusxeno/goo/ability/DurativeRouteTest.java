package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The durative route is decided by badge and delivery kind alone: every
 * shipped ability wearing the durative badge on a self delivery is durative
 * and no other is, a made-up durative ability takes the route with no
 * further change, and a self-badged self ability runs on command
 * (decision self-effects-trickle-until-ended).
 */
class DurativeRouteTest {

    private static final Identifier MADE_UP = Identifier.fromNamespaceAndPath("goo", "made_up_brew_ability");
    private static final int A_COST = 1000;
    private static final Delivery SELF = Delivery.of(DeliveryKind.SELF);

    private static AbilityDefinition madeUpBrewAbility() {
        return new AbilityDefinition(MADE_UP, GooTypes.LEAF, MADE_UP.toString(), "", 0, A_COST,
                SELF, List.of(), List.of(), AbilityBadge.DURATIVE, List.of());
    }

    @Nested
    class RouteByBadgeAndDelivery {

        @Test
        void everyShippedAbilityIsDurativeExactlyWhenItWearsTheBadgeOnASelfDelivery() {
            List<Path> files = AbilityJson.files();
            assertFalse(files.isEmpty(), "No ability JSON found under " + AbilityJson.ABILITIES_DIR);
            for (Path file : files) {
                AbilityDefinition ability = AbilityJson.decode(file);
                boolean brewOnSelf = ability.delivery().kind() == DeliveryKind.SELF
                        && ability.badge() == AbilityBadge.DURATIVE;
                assertEquals(brewOnSelf, DurativeRoute.isDurative(ability.delivery(), ability.badge()),
                        ability.id() + " should be durative exactly when it wears the badge on a self delivery");
            }
        }

        @Test
        void theShippedDurativesTakeTheRouteAndTheShippedSelfAbilitiesRunOnCommand() {
            for (String brew : List.of("blaze_kindle", "leaf_barkskin", "jelly_gluttony")) {
                AbilityDefinition ability = AbilityJson.decode(brew);
                assertTrue(DurativeRoute.isDurative(ability.delivery(), ability.badge()), brew);
            }
            for (String onCommand : List.of("ender_blink", "hex_enchant")) {
                AbilityDefinition ability = AbilityJson.decode(onCommand);
                assertFalse(DurativeRoute.isDurative(ability.delivery(), ability.badge()), onCommand);
            }
        }

        @Test
        void aMadeUpDurativeAbilityTakesTheRoute() {
            AbilityDefinition brew = madeUpBrewAbility();

            assertTrue(DurativeRoute.isDurative(brew.delivery(), brew.badge()));
        }

        @Test
        void aSelfBadgedSelfDeliveryRunsOnCommand() {
            assertFalse(DurativeRoute.isDurative(SELF, AbilityBadge.SELF));
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryKind.class, names = "SELF", mode = EnumSource.Mode.EXCLUDE)
        void aDurativeBadgeOnAnyOtherDeliveryIsNoDurative(DeliveryKind kind) {
            assertFalse(DurativeRoute.isDurative(Delivery.of(kind), AbilityBadge.DURATIVE));
        }

        @Test
        void noSelectionIsNoDurative() {
            assertFalse(DurativeRoute.isDurative(null, null));
        }
    }

    /** An invoke of a held durative ability ends it rather than holding it again (decision self-effects-trickle-until-ended). */
    @Nested
    class EndHeld {

        @Test
        void aHeldDurativeTakesTheEndRoute() {
            assertTrue(DurativeRoute.endsHeld(SELF, AbilityBadge.DURATIVE, true));
        }

        @Test
        void aDurativeNotHeldIsHeld() {
            assertFalse(DurativeRoute.endsHeld(SELF, AbilityBadge.DURATIVE, false));
        }

        @Test
        void aSelfBadgedAbilityNeverTakesTheEndRoute() {
            assertFalse(DurativeRoute.endsHeld(SELF, AbilityBadge.SELF, true));
            assertFalse(DurativeRoute.endsHeld(null, null, true));
        }
    }
}
