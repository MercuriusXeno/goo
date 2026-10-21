package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How many uses or whole seconds an ability's holdings afford, and the petal label reading cost then count. */
class AffordedCountTest {

    private static final int THOUSAND = 1000;
    private static final int NO_REAGENTS = 0;
    private static final int SLOW_TICKS_PER_CHARGE = 60;
    private static final Identifier BOOK = Identifier.withDefaultNamespace("book");

    private static ClientAbility ability(int cost, Delivery delivery, AbilityBadge badge, List<Identifier> consumes,
                                         int upkeep) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "counted"), "ability.goo.counted", "", 0,
                List.of(), List.of(), cost, delivery, badge, List.of(), AbilityArea.NONE, IndicatorShowing.HELD,
                consumes, upkeep);
    }

    private static ClientAbility oneShot(int cost) {
        return ability(cost, Delivery.ARC, AbilityBadge.WORLD, List.of(), 0);
    }

    private static ClientAbility stream(int cost, int ticksPerCharge) {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, Delivery.DEFAULT_BLOCKS_PER_TICK, 0,
                Delivery.DEFAULT_CONE_DEGREES, ticksPerCharge, true, Optional.empty(), Delivery.DEFAULT_TRANSFORM_AT);
        return ability(cost, delivery, AbilityBadge.WORLD, List.of(), 0);
    }

    /** A one-shot petal reads its shorthand cost then its uses, dimmed at none. */
    @Nested
    class OneShot {

        @Test
        void holdingsForThreeReadTimesThree() {
            assertEquals("1K | x3", oneShot(THOUSAND).petalLabel(3500, NO_REAGENTS));
        }

        @Test
        void holdingsShortOfOneReadTimesZeroDimmed() {
            ClientAbility ability = oneShot(THOUSAND);
            assertEquals("1K | x0", ability.petalLabel(999, NO_REAGENTS));
            assertTrue(ability.dimsAt(999, NO_REAGENTS));
        }
    }

    /** A stream or brew petal reads its cost a second then its whole seconds. */
    @Nested
    class Seconds {

        @Test
        void streamPayingEverySecondReadsTwelveSeconds() {
            assertEquals("1K/s | 12s", stream(THOUSAND, Delivery.DEFAULT_TICKS_PER_CHARGE).petalLabel(12_500, NO_REAGENTS));
        }

        @Test
        void streamPayingEveryThreeSecondsReadsThreeSeconds() {
            assertEquals("333/s | 3s", stream(THOUSAND, SLOW_TICKS_PER_CHARGE).petalLabel(THOUSAND, NO_REAGENTS));
        }

        @Test
        void brewPayingOneATickReadsTwelveSeconds() {
            ClientAbility brew = ability(0, Delivery.of(DeliveryKind.SELF), AbilityBadge.BREW, List.of(), 1);
            assertEquals("20/s | 12s", brew.petalLabel(250, NO_REAGENTS));
        }
    }

    /** A count reads to 3 significant digits truncated (decision counts-read-three-significant-digits). */
    @Nested
    class ShortUnits {

        @Test
        void useCountsReadInShorthand() {
            ClientAbility penny = oneShot(1);
            assertEquals("x42.6K", AffordedCount.reckon(penny, 42_678, NO_REAGENTS).orElseThrow().label());
            assertEquals("x999", AffordedCount.reckon(penny, 999, NO_REAGENTS).orElseThrow().label());
            assertEquals("x1M", AffordedCount.reckon(penny, 1_000_000, NO_REAGENTS).orElseThrow().label());
        }

        @Test
        void brewCountsReadInTheirLargestUnit() {
            ClientAbility brew = ability(0, Delivery.of(DeliveryKind.SELF), AbilityBadge.BREW, List.of(), 1);
            assertEquals("1.5m", AffordedCount.reckon(brew, 1800, NO_REAGENTS).orElseThrow().label());
            assertEquals("12s", AffordedCount.reckon(brew, 250, NO_REAGENTS).orElseThrow().label());
        }
    }

    /** A dynamically priced petal reads its formula and never 0 (decision dynamic-costs-keep-the-mb-quantity). */
    @Nested
    class Dynamic {

        private static final List<String> DYNAMIC_ABILITIES = List.of("ender_blink", "deep_churn",
                "nether_black_hole", "unstable_explode", "rock_spire", "unstable_unmake", "yore_age");

        @Test
        void zeroCostPetalReadsItsFormula() {
            ClientAbility blink = new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "blink"),
                    "ability.goo.blink", "", 0, List.of(), List.of(), 0, Delivery.of(DeliveryKind.SELF),
                    AbilityBadge.SELF, List.of(), AbilityArea.NONE, IndicatorShowing.HELD, List.of(), 0,
                    DistancePrice.NONE, List.of(), ArmPose.defaultFor(DeliveryKind.SELF), "ability.goo.blink.description",
                    Optional.empty(), Optional.of("200 + 100/block"));
            assertEquals("200 + 100/block", blink.petalLabel(THOUSAND, NO_REAGENTS));
        }

        @Test
        void noShippedDynamicPetalReadsZero() {
            for (String name : DYNAMIC_ABILITIES) {
                AbilityDefinition definition = AbilityJson.decode(name);
                ClientAbility ability = ClientAbility.fromEntry(
                        AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)).get(0));
                String label = ability.petalLabel(THOUSAND, NO_REAGENTS);
                assertEquals(definition.costFormula().orElse("no formula"), label, name);
                assertNotEquals("0", label, name);
            }
        }
    }

    /** An ability consuming an item counts the smaller of the goo bound and the item bound. */
    @Nested
    class Reagents {

        private final ClientAbility enchant = ability(THOUSAND, Delivery.ARC, AbilityBadge.WORLD, List.of(BOOK), 0);

        @Test
        void fewerBooksThanGooBoundsTheCount() {
            assertEquals(3, AffordedCount.reckon(enchant, 5000, 3).orElseThrow().count());
        }

        @Test
        void fewerGooThanBooksBoundsTheCount() {
            assertEquals(5, AffordedCount.reckon(enchant, 5000, 7).orElseThrow().count());
        }
    }
}
