package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooFormat;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Supplies the crucible HUD panel's heat row: the blaze goo icon, the heat
 * ticks left and the fuel goo in the reservoir (decision fuel-goo-heats-per-mb).
 */
final class CrucibleFuelDisplay {
    /** Heat text color (orange, the blaze color). */
    private static final int HEAT_COLOR = 0xFFFF6600;
    /** Separator color (dim gray). */
    private static final int SEPARATOR_COLOR = 0xFF888888;
    /** Suffix naming the heat count as ticks. */
    private static final String TICKS_SUFFIX = "t";
    /** Separator between the heat ticks and the fuel goo volume. */
    private static final String HEAT_SEPARATOR = " / ";

    private CrucibleFuelDisplay() {}

    /**
     * Builds the heat row: the blaze goo icon, the heat ticks left, then the fuel goo volume.
     *
     * @param heatTicks     the heat ticks left
     * @param fuelGooVolume the mB of fuel goo in the reservoir
     * @return the row, or null when the crucible holds neither heat nor fuel goo
     */
    static @Nullable PanelRow heatRow(int heatTicks, long fuelGooVolume) {
        if (heatTicks <= 0 && fuelGooVolume <= 0) {
            return null;
        }
        return new PanelRow(PanelPainter.gooIcon(GooTypes.BLAZE), List.of(
                new PanelRow.TextSegment(heatTicks + TICKS_SUFFIX, HEAT_COLOR),
                new PanelRow.TextSegment(HEAT_SEPARATOR, SEPARATOR_COLOR),
                new PanelRow.TextSegment(GooFormat.formatFluidDisplayCompact(fuelGooVolume), HEAT_COLOR)),
                false);
    }
}
