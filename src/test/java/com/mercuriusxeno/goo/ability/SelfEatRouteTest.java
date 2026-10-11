package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemUseAnimation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eat route is decided by badge and delivery kind alone: every shipped
 * ability wearing the brew badge on a self delivery eats and no other does,
 * a made-up brew ability eats with no further change, a self-badged self
 * ability runs on command, the glove reports the eat animation and
 * vanilla's eat duration only for an eaten ability, and the finish runs the
 * invoke only under one (decision self-brew-goos-eat-before-the-effect).
 */
class SelfEatRouteTest {

    private static final Identifier MADE_UP = Identifier.fromNamespaceAndPath("goo", "made_up_brew_ability");
    private static final int A_COST = 1000;
    private static final Delivery SELF = Delivery.of(DeliveryKind.SELF);

    private static AbilityDefinition madeUpBrewAbility() {
        return new AbilityDefinition(MADE_UP, GooTypes.LEAF, MADE_UP.toString(), "", 0, A_COST,
                SELF, List.of(), List.of(), AbilityBadge.BREW, List.of());
    }

    @Nested
    class RouteByBadgeAndDelivery {

        @Test
        void everyShippedAbilityEatsExactlyWhenItIsABrewOnASelfDelivery() {
            List<Path> files = AbilityJson.files();
            assertFalse(files.isEmpty(), "No ability JSON found under " + AbilityJson.ABILITIES_DIR);
            for (Path file : files) {
                AbilityDefinition ability = AbilityJson.decode(file);
                boolean brewOnSelf = ability.delivery().kind() == DeliveryKind.SELF
                        && ability.badge() == AbilityBadge.BREW;
                assertEquals(brewOnSelf, SelfEatRoute.eats(ability.delivery(), ability.badge()),
                        ability.id() + " should eat exactly when it wears brew on a self delivery");
            }
        }

        @Test
        void theShippedBrewsEatAndTheShippedSelfAbilitiesRunOnCommand() {
            for (String brew : List.of("blaze_kindle", "leaf_barkskin", "jelly_gluttony")) {
                AbilityDefinition ability = AbilityJson.decode(brew);
                assertTrue(SelfEatRoute.eats(ability.delivery(), ability.badge()), brew);
            }
            for (String onCommand : List.of("ender_blink", "hex_enchant")) {
                AbilityDefinition ability = AbilityJson.decode(onCommand);
                assertFalse(SelfEatRoute.eats(ability.delivery(), ability.badge()), onCommand);
            }
        }

        @Test
        void aMadeUpBrewAbilityEats() {
            AbilityDefinition brew = madeUpBrewAbility();

            assertTrue(SelfEatRoute.eats(brew.delivery(), brew.badge()));
        }

        @Test
        void aSelfBadgedSelfDeliveryRunsOnCommand() {
            assertFalse(SelfEatRoute.eats(SELF, AbilityBadge.SELF));
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryKind.class, names = "SELF", mode = EnumSource.Mode.EXCLUDE)
        void aBrewBadgeOnAnyOtherDeliveryEatsNothing(DeliveryKind kind) {
            assertFalse(SelfEatRoute.eats(Delivery.of(kind), AbilityBadge.BREW));
        }

        @Test
        void noSelectionEatsNothing() {
            assertFalse(SelfEatRoute.eats(null, null));
        }
    }

    /** An invoke of a held self + brew ability ends it rather than eating (decision self-effects-trickle-until-ended). */
    @Nested
    class EndHeld {

        @Test
        void aHeldBrewTakesTheEndRoute() {
            assertTrue(SelfEatRoute.endsHeld(SELF, AbilityBadge.BREW, true));
        }

        @Test
        void aBrewNotHeldEats() {
            assertFalse(SelfEatRoute.endsHeld(SELF, AbilityBadge.BREW, false));
        }

        @Test
        void aSelfBadgedAbilityNeverTakesTheEndRoute() {
            assertFalse(SelfEatRoute.endsHeld(SELF, AbilityBadge.SELF, true));
            assertFalse(SelfEatRoute.endsHeld(null, null, true));
        }
    }

    @Nested
    class AnimationAndDuration {

        @Test
        void anEatenAbilityReportsTheEatForVanillasDuration() {
            boolean eats = SelfEatRoute.eats(SELF, AbilityBadge.BREW);

            assertEquals(ItemUseAnimation.EAT, SelfEatRoute.animation(eats));
            assertEquals(SelfEatRoute.EAT_TICKS, SelfEatRoute.useDuration(eats));
        }

        @Test
        void anAbilityOnCommandReportsNoAnimationAndNoDuration() {
            boolean eats = SelfEatRoute.eats(SELF, AbilityBadge.SELF);

            assertEquals(ItemUseAnimation.NONE, SelfEatRoute.animation(eats));
            assertEquals(SelfEatRoute.NO_USE, SelfEatRoute.useDuration(eats));
        }
    }

    @Nested
    class Finish {

        @Test
        void aFinishUnderABrewRunsTheInvokeOnce() {
            AbilityDefinition brew = madeUpBrewAbility();
            AtomicInteger invoked = new AtomicInteger();

            SelfEatRoute.finish(brew.delivery(), brew.badge(), invoked::incrementAndGet);

            assertEquals(1, invoked.get());
        }

        @Test
        void aFinishUnderASelfBadgedAbilityRunsNothing() {
            AtomicInteger invoked = new AtomicInteger();

            SelfEatRoute.finish(SELF, AbilityBadge.SELF, invoked::incrementAndGet);

            assertEquals(0, invoked.get());
        }
    }
}
