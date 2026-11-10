package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the one method the in-world item tooltip and the known items menu's tooltip build their goo lines through:
 * an item's name, then one exact mixed-number line per type it costs, highest first, ties by type key
 * (decisions menu-tooltip-carries-the-goo-cost, tooltip-and-plexer-hud-show-a-mixed-number and
 * value-lists-order-highest-first-ties-by-type-key).
 */
class GooTooltipLinesTest {

    private static final Component NAME = Component.literal("Carpet");

    @Test
    void valueWithThreeTypesReadsTheNameThenThreeLinesHighestFirst() {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> byTypeId = new LinkedHashMap<>();
        byTypeId.put(GooTypes.LEAF, GooFraction.of(1, 3));
        byTypeId.put(GooTypes.ROCK, GooFraction.of(9, 4));
        byTypeId.put(GooTypes.GLOW, GooFraction.of(2));
        GooValue value = mock(GooValue.class);
        when(value.exactAll()).thenReturn(byTypeId);

        List<Either<FormattedText, TooltipComponent>> lines = GooTooltipLines.named(NAME, value);

        assertEquals(List.of(Either.left(NAME),
                Either.right(new GooValueTooltipComponent(GooTypes.ROCK, GooFraction.of(9, 4))),
                Either.right(new GooValueTooltipComponent(GooTypes.GLOW, GooFraction.of(2))),
                Either.right(new GooValueTooltipComponent(GooTypes.LEAF, GooFraction.of(1, 3)))), lines);
    }

    @Test
    void nullValueReadsTheNameAlone() {
        assertEquals(List.of(Either.left(NAME)), GooTooltipLines.named(NAME, null));
    }

    /** A whole volume map, as a container's contents read, lays highest first through the same lines. */
    @Test
    void volumesReadHighestFirst() {
        Map<ResourceKey<GooTypeDefinition>, Integer> volumes = new LinkedHashMap<>();
        volumes.put(GooTypes.LEAF, 1);
        volumes.put(GooTypes.BLAZE, 4);

        assertEquals(List.of(new GooValueTooltipComponent(GooTypes.BLAZE, 4),
                new GooValueTooltipComponent(GooTypes.LEAF, 1)), GooTooltipLines.volumeLines(volumes));
    }
}
