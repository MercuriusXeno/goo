package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data model carrying a single goo type and its amount for tooltip rendering.
 * One component per goo line in the tooltip. Amount is     (int).
 *
 * @param type   the goo type for this tooltip line
 * @param amount the volume
 */
public record GooValueTooltipComponent(ResourceKey<GooTypeDefinition> type, int amount) implements TooltipComponent {
}
