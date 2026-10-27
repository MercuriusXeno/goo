package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * One line of a tooltip the values manager shows: text, or a goo type's amount, which draws as the type's icon
 * followed by the amount (decision value-tooltips-draw-each-types-icon-before-its-amount).
 */
public sealed interface TooltipLine permits TooltipLine.Text, TooltipLine.GooAmount {

    /**
     * A line of text: a name, a scalar value, an error or a hint.
     *
     * @param text what the line reads
     */
    record Text(Component text) implements TooltipLine {
    }

    /**
     * A goo type's amount, which {@link GooAmountTooltipComponent} draws.
     *
     * @param type   the goo type's id
     * @param amount the amount as the value reads it
     */
    record GooAmount(String type, String amount) implements TooltipLine, TooltipComponent {
    }
}
