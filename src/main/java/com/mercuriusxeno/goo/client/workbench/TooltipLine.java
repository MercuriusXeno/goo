package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.List;

/**
 * One line of a tooltip the values manager shows: text, or a goo type's amount, which draws as the type's icon
 * followed by the amount (decision value-tooltips-draw-each-types-icon-before-its-amount).
 */
public sealed interface TooltipLine permits TooltipLine.Text, TooltipLine.GooAmount, TooltipLine.MemberIcons {

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

    /**
     * A group's members as item icons in one row, which {@link MemberIconsTooltipComponent} draws (decision
     * group-link-hovers-member-icons-then-plus-n).
     *
     * @param ids  the members drawn, in member order
     * @param more the ..+n label counting the members left out, empty where every member draws
     */
    record MemberIcons(List<String> ids, String more) implements TooltipLine, TooltipComponent {
        /**
         * @param ids  the members drawn, in member order
         * @param more the ..+n label counting the members left out, empty where every member draws
         */
        public MemberIcons {
            ids = List.copyOf(ids);
        }
    }
}
