package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crucible.CrucibleHeat;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Supplies the crucible HUD panel's heat rows: the combo row, then per fuel grade its goo
 * icon and the seconds its heat and stock last (decisions heat-row-reads-seconds,
 * combo-row-above-remainder).
 */
final class CrucibleFuelDisplay {
    /** Heat text color (orange, the blaze color). */
    private static final int HEAT_COLOR = 0xFFFF6600;
    /** Game ticks in one second. */
    private static final double TICKS_PER_SECOND = 20.0;
    /** Seconds to one decimal place, suffixed "s". */
    private static final String SECONDS_FORMAT = "%.1fs";

    private CrucibleFuelDisplay() {}

    /**
     * Builds one heat row per burn in the order they run: the combo row with both fuels' icons
     * above each remainder row with its fuel's icon, each reading the seconds it lasts.
     *
     * @param burns the burns, combo first
     * @return the rows, empty when the crucible holds neither heat nor fuel goo
     */
    static List<PanelRow> heatRows(List<CrucibleHeat.FuelBurn> burns) {
        List<PanelRow> rows = new ArrayList<>();
        for (CrucibleHeat.FuelBurn burn : burns) {
            rows.add(heatRow(burn));
        }
        return rows;
    }

    /**
     * Builds one burn's heat row: its fuels' icons, then its seconds.
     *
     * @param burn the burn
     * @return the row
     */
    private static PanelRow heatRow(CrucibleHeat.FuelBurn burn) {
        String text = seconds(burn.ticks());
        Identifier first = PanelPainter.gooIcon(burn.grades().getFirst().fuel());
        if (burn.isCombo()) {
            return PanelRow.iconPairText(first, PanelPainter.gooIcon(burn.grades().get(1).fuel()), text, HEAT_COLOR);
        }
        return PanelRow.iconText(first, text, HEAT_COLOR);
    }

    /**
     * Formats melt ticks as seconds to one decimal place.
     *
     * @param ticks the melt ticks
     * @return the seconds text, such as "10.6s"
     */
    static String seconds(long ticks) {
        return String.format(Locale.ROOT, SECONDS_FORMAT, ticks / TICKS_PER_SECOND);
    }
}
