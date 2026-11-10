package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.data.GooAmountOrder;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The goo lines every tooltip builds, so the in-world item tooltip and the known items menu's tooltip stay one
 * convention: one line per type, highest first, ties by type key, an exact amount reading as a mixed number.
 * decision menu-tooltip-carries-the-goo-cost
 * decision value-lists-order-highest-first-ties-by-type-key
 * decision tooltip-and-plexer-hud-show-a-mixed-number
 */
public final class GooTooltipLines {

    private GooTooltipLines() {
    }

    /**
     * @param name  the item's name, the tooltip's first line
     * @param value the item's goo value, or null when the client knows none
     * @return the name, then one line per type the item costs
     */
    public static List<Either<FormattedText, TooltipComponent>> named(Component name, @Nullable GooValue value) {
        List<Either<FormattedText, TooltipComponent>> lines = new ArrayList<>();
        lines.add(Either.left(name));
        valueLines(value).forEach(line -> lines.add(Either.right(line)));
        return lines;
    }

    /**
     * @param value an item's goo value, or null when the client knows none
     * @return one line per type it is worth a positive amount of, at its exact amount
     */
    public static List<GooValueTooltipComponent> valueLines(@Nullable GooValue value) {
        if (value == null) {
            return List.of();
        }
        Map<ResourceKey<GooTypeDefinition>, GooFraction> positive = new LinkedHashMap<>();
        value.exactAll().forEach((type, amount) -> {
            if (amount.isPositive()) {
                positive.put(type, amount);
            }
        });
        return lines(positive);
    }

    /**
     * @param volumes each goo type's whole volume, as a container's contents read
     * @return one line per type
     */
    public static List<GooValueTooltipComponent> volumeLines(Map<ResourceKey<GooTypeDefinition>, Integer> volumes) {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> exact = new LinkedHashMap<>();
        volumes.forEach((type, volume) -> exact.put(type, GooFraction.of(volume)));
        return lines(exact);
    }

    /**
     * The one place a goo tooltip line is built.
     *
     * @param amounts each goo type's exact amount
     * @return one line per type, highest first, ties by type key
     */
    private static List<GooValueTooltipComponent> lines(Map<ResourceKey<GooTypeDefinition>, GooFraction> amounts) {
        return amounts.entrySet().stream().sorted(GooAmountOrder.highestFirst())
                .map(entry -> new GooValueTooltipComponent(entry.getKey(), entry.getValue())).toList();
    }
}
