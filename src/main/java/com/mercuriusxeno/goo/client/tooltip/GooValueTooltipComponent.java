package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data model carrying a single goo type and its exact amount for tooltip rendering,
 * one component per goo line, which reads as a mixed number.
 * decision tooltip-and-plexer-hud-show-a-mixed-number
 *
 * @param type   the goo type for this tooltip line
 * @param amount the exact amount
 */
public record GooValueTooltipComponent(ResourceKey<GooTypeDefinition> type, GooFraction amount)
        implements TooltipComponent {

    /**
     * A line holding a whole amount, as a container's volume does.
     *
     * @param type   the goo type
     * @param amount the volume in mB
     */
    public GooValueTooltipComponent(ResourceKey<GooTypeDefinition> type, int amount) {
        this(type, GooFraction.of(amount));
    }
}
