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
 * The eat route is decided by delivery kind alone: every shipped self
 * ability eats and no other does, a made-up self ability eats with no
 * further change, the glove reports the eat animation and vanilla's eat
 * duration for a self delivery and none for any other, and the finish
 * runs the invoke only under a self delivery
 * (decision self-brew-goos-eat-before-the-effect).
 */
class SelfEatRouteTest {

    private static final Identifier MADE_UP = Identifier.fromNamespaceAndPath("goo", "made_up_self_ability");
    private static final int A_COST = 1000;

    private static AbilityDefinition madeUpSelfAbility() {
        return new AbilityDefinition(MADE_UP, GooTypes.ENDER, MADE_UP.toString(), "", 0, A_COST,
                Delivery.of(DeliveryKind.SELF), List.of(), List.of(), AbilityBadge.SELF, List.of());
    }

    @Nested
    class RouteByDeliveryKind {

        @Test
        void everyShippedAbilityEatsExactlyWhenItsDeliveryIsSelf() {
            List<Path> files = AbilityJson.files();
            assertFalse(files.isEmpty(), "No ability JSON found under " + AbilityJson.ABILITIES_DIR);
            for (Path file : files) {
                AbilityDefinition ability = AbilityJson.decode(file);
                assertEquals(ability.delivery().kind() == DeliveryKind.SELF, SelfEatRoute.eats(ability.delivery()),
                        ability.id() + " should eat exactly when its delivery is self");
            }
        }

        @Test
        void aMadeUpSelfAbilityEats() {
            assertTrue(SelfEatRoute.eats(madeUpSelfAbility().delivery()));
        }

        @Test
        void noSelectionEatsNothing() {
            assertFalse(SelfEatRoute.eats(null));
        }
    }

    @Nested
    class AnimationAndDuration {

        @Test
        void aSelfDeliveryReportsTheEatForVanillasDuration() {
            Delivery self = Delivery.of(DeliveryKind.SELF);

            assertEquals(ItemUseAnimation.EAT, SelfEatRoute.animation(self));
            assertEquals(SelfEatRoute.EAT_TICKS, SelfEatRoute.useDuration(self));
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryKind.class, names = "SELF", mode = EnumSource.Mode.EXCLUDE)
        void everyOtherDeliveryReportsNoAnimationAndNoDuration(DeliveryKind kind) {
            Delivery delivery = Delivery.of(kind);

            assertEquals(ItemUseAnimation.NONE, SelfEatRoute.animation(delivery));
            assertEquals(SelfEatRoute.NO_USE, SelfEatRoute.useDuration(delivery));
        }
    }

    @Nested
    class Finish {

        @Test
        void aFinishUnderASelfDeliveryRunsTheInvokeOnce() {
            AtomicInteger invoked = new AtomicInteger();

            SelfEatRoute.finish(madeUpSelfAbility().delivery(), invoked::incrementAndGet);

            assertEquals(1, invoked.get());
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryKind.class, names = "SELF", mode = EnumSource.Mode.EXCLUDE)
        void aFinishUnderAnyOtherDeliveryRunsNothing(DeliveryKind kind) {
            AtomicInteger invoked = new AtomicInteger();

            SelfEatRoute.finish(Delivery.of(kind), invoked::incrementAndGet);

            assertEquals(0, invoked.get());
        }
    }
}
