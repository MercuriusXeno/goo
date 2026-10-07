package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.tooltip.GooValueTooltipComponent;
import com.mercuriusxeno.goo.client.tooltip.VanillaFluidTooltipComponent;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.material.Fluid;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The tooltip shows an item's goo value only once the player has learned the
 * item, while goo a stack carries always shows (decision
 * goo-tooltip-shows-only-known-values); its hint names the key bound to Goo
 * values, and its goo rows follow the line above them with no blank line
 * (decision tooltip-key-is-its-own-g-binding).
 */
class GooTooltipHandlerTest {

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier CANISTER = Identifier.fromNamespaceAndPath("goo", "canister");
    private static final String TITLE = "Title line";
    private static final int CONTENT_AMOUNT = 1000;
    private static final int CONTAINER_AMOUNT = 5;

    @Nested
    class KnownValues {

        @Test
        void unknownItemShowsNoValue() {
            assertFalse(GooTooltipHandler.revealsGooLines(false, COBBLESTONE, KnownItems.NONE));
        }

        @Test
        void knownItemShowsItsValue() {
            assertTrue(GooTooltipHandler.revealsGooLines(false, COBBLESTONE, KnownItems.NONE.with(COBBLESTONE)));
        }

        @Test
        void containerShowsTheGooItCarriesUnlearned() {
            assertTrue(GooTooltipHandler.revealsGooLines(true, CANISTER, KnownItems.NONE));
        }
    }

    @Nested
    class Hint {

        @Test
        void hintNamesGAtTheDefault() {
            assertEquals("Hold [G] for goo values",
                    GooTooltipHandler.revealHint(Component.literal("G")).getString());
        }

        @Test
        void hintNamesShiftOnceReboundToShift() {
            assertEquals("Hold [Shift] for goo values",
                    GooTooltipHandler.revealHint(Component.literal("Shift")).getString());
        }
    }

    @Nested
    class Rows {

        private final List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>(
                List.of(Either.left(Component.literal(TITLE))));

        @Test
        void gooRowFollowsTheTitleDirectly() {
            GooTooltipHandler.appendGooRows(elements, Map.of(GooTypes.ROCK, CONTENT_AMOUNT));

            assertEquals(List.of(TITLE, new GooValueTooltipComponent(GooTypes.ROCK, CONTENT_AMOUNT)),
                    shapes(elements));
        }

        @Test
        void containerRowsKeepThePlusBetweenContentsAndContainer() {
            GooTooltipHandler.appendContainerRows(elements, GooTypes.ROCK, CONTENT_AMOUNT,
                    new GooValue(Map.of(GooTypes.NETHER, CONTAINER_AMOUNT)));

            assertEquals(List.of(TITLE,
                    new GooValueTooltipComponent(GooTypes.ROCK, CONTENT_AMOUNT),
                    "+",
                    new GooValueTooltipComponent(GooTypes.NETHER, CONTAINER_AMOUNT)), shapes(elements));
        }

        @Test
        void containerWithoutValueShowsContentsAlone() {
            GooTooltipHandler.appendContainerRows(elements, GooTypes.ROCK, CONTENT_AMOUNT, null);

            assertEquals(List.of(TITLE, new GooValueTooltipComponent(GooTypes.ROCK, CONTENT_AMOUNT)),
                    shapes(elements));
        }

        @Test
        void vanillaFluidRowFollowsTheTitleDirectly() {
            Fluid water = mock(Fluid.class);

            GooTooltipHandler.appendVanillaFluidRow(elements, water, CONTENT_AMOUNT);

            assertEquals(List.of(TITLE, new VanillaFluidTooltipComponent(water, CONTENT_AMOUNT)), shapes(elements));
        }

        /** Each element as its text line's string or its component, so a blank line reads as "". */
        private List<Object> shapes(List<Either<FormattedText, TooltipComponent>> tooltip) {
            return tooltip.stream()
                    .map(element -> element.<Object>map(FormattedText::getString, component -> component))
                    .toList();
        }
    }
}
