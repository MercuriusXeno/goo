package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.List;

/**
 * One line of a tooltip the values manager shows: text, or a goo type's amount, which draws as the type's icon
 * followed by the amount (decision value-tooltips-draw-each-types-icon-before-its-amount).
 */
public sealed interface TooltipLine permits TooltipLine.Text, TooltipLine.GooAmount, TooltipLine.Runs {

    /**
     * A line of text: a name, a scalar value, an error or a hint.
     *
     * @param text what the line reads
     */
    record Text(Component text) implements TooltipLine {
    }

    /**
     * A goo type's amount, which {@link GooAmountTooltipComponent} draws as one cell of its run's grid.
     *
     * @param type   the goo type's id
     * @param amount the amount as the value reads it
     */
    record GooAmount(String type, String amount) implements TooltipLine {
    }

    /**
     * One line of runs, goo types as icons beside numbers and symbols, which {@link RunsTooltipComponent} draws
     * (decision conversion-controls-hover-their-formula).
     *
     * @param runs the line's runs
     */
    record Runs(List<ExpressionLines.Run> runs) implements TooltipLine, TooltipComponent {
        /**
         * @param runs the line's runs
         */
        public Runs {
            runs = List.copyOf(runs);
        }
    }

    /**
     * A run of goo amounts a tooltip lays as one grid, which {@link GooAmountGrid} draws (decision
     * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
     *
     * @param amounts the amounts, in the value's type order
     */
    record GooAmounts(List<GooAmount> amounts) implements TooltipComponent {
        /**
         * @param amounts the amounts, in the value's type order
         */
        public GooAmounts {
            amounts = List.copyOf(amounts);
        }
    }
}
