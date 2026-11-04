package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.List;

/**
 * One line of a tooltip the values manager shows: text, or a goo type's amount, which draws as the type's icon
 * followed by the amount (decision value-tooltips-draw-each-types-icon-before-its-amount).
 */
public sealed interface TooltipLine permits TooltipLine.Text, TooltipLine.GooAmount, TooltipLine.Runs,
        TooltipLine.RunsGrid, TooltipLine.Members {

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
     * Cells of runs laid as a grid of ceil(sqrt(count)) columns, each as wide as the widest cell, every row past the
     * first indented, which {@link RunsGridTooltipComponent} draws; a prefab's recipe and its reduction read one cell
     * per goo type (decision tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
     *
     * @param cells each cell's runs, in reading order
     */
    record RunsGrid(List<List<ExpressionLines.Run>> cells) implements TooltipLine, TooltipComponent {
        /**
         * @param cells each cell's runs, in reading order
         */
        public RunsGrid {
            cells = cells.stream().map(List::copyOf).toList();
        }
    }

    /**
     * A group's members as their sprites on one line, then +n for the members the line leaves out, which
     * {@link MembersTooltipComponent} draws (decision group-link-hovers-member-icons-then-plus-n).
     *
     * @param ids  the members the line shows, in the group's order
     * @param more how many members follow them unshown
     */
    record Members(List<String> ids, int more) implements TooltipLine, TooltipComponent {
        /**
         * @param ids  the members the line shows, in the group's order
         * @param more how many members follow them unshown
         */
        public Members {
            ids = List.copyOf(ids);
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
