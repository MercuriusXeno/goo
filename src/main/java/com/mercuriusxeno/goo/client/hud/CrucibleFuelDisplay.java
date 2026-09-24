package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Supplies the crucible HUD panel's fuel row: the depleted blaze rod stage
 * texture and the seconds of fuel remaining.
 */
final class CrucibleFuelDisplay {
    /** Fuel text color (orange, matching the blaze rod bar). */
    private static final int FUEL_COLOR = 0xFFFF6600;
    /** Number of depleted blaze rod texture stages (0 = most depleted, 7 = fullest). */
    private static final int BLAZE_ROD_STAGES = 8;
    /** Ticks per second for fuel time conversion. */
    private static final float TICKS_PER_SECOND = 20f;
    /** Fuel display threshold: show integer seconds above this. */
    private static final float FUEL_INT_THRESHOLD = 10f;
    /** Format string for integer-second fuel display. */
    private static final String FUEL_INT_FORMAT = "%.0fs";
    /** Format string for decimal-second fuel display. */
    private static final String FUEL_DEC_FORMAT = "%.1fs";
    /** Texture path prefix for depleted blaze rod stages. */
    private static final String BLAZE_ROD_PREFIX = "textures/item/depleted_blaze_rod_";
    /** Texture path suffix for depleted blaze rod stages. */
    private static final String BLAZE_ROD_SUFFIX = ".png";

    private CrucibleFuelDisplay() {}

    /**
     * Builds the fuel row: depleted blaze rod icon at the rod's stage, then the remaining seconds.
     *
     * @param fuelRod the fuel rod item stack
     * @return the row
     */
    static PanelRow fuelRow(ItemStack fuelRod) {
        return PanelRow.iconText(blazeRodTexture(fuelRod), formatFuelSeconds(fuelRod), FUEL_COLOR);
    }

    /**
     * Formats fuel remaining as seconds with one decimal: "42.3s".
     *
     * @param fuelRod the fuel rod item stack
     * @return the formatted string
     */
    private static String formatFuelSeconds(ItemStack fuelRod) {
        int ticks = fuelTicksRemaining(fuelRod);
        float seconds = ticks / TICKS_PER_SECOND;
        if (seconds >= FUEL_INT_THRESHOLD) { return String.format(FUEL_INT_FORMAT, seconds); }
        return String.format(FUEL_DEC_FORMAT, seconds);
    }

    /**
     * Returns fuel ticks remaining: full 1200 for a vanilla blaze rod, else from data component.
     *
     * @param fuelRod the fuel rod item stack
     * @return the result
     */
    private static int fuelTicksRemaining(ItemStack fuelRod) {
        if (fuelRod.is(Items.BLAZE_ROD)) { return DepletedBlazeRodItem.FULL_FUEL_TICKS; }
        return DepletedBlazeRodItem.getTicksRemaining(fuelRod);
    }

    /**
     * Returns the appropriate depleted blaze rod texture for the current fuel level.
     *
     * @param fuelRod the fuel rod item stack
     * @return the blaze rod texture identifier
     */
    private static Identifier blazeRodTexture(ItemStack fuelRod) {
        float fraction = fuelFraction(fuelRod);
        int stage = Math.min((int) (fraction * BLAZE_ROD_STAGES), BLAZE_ROD_STAGES - 1);
        return Identifier.fromNamespaceAndPath(Goo.MODID,
            BLAZE_ROD_PREFIX + stage + BLAZE_ROD_SUFFIX);
    }

    /**
     * Computes fuel fraction (0.0 = depleted, 1.0 = fresh).
     *
     * @param fuelRod the fuel rod item stack
     * @return the result
     */
    private static float fuelFraction(ItemStack fuelRod) {
        if (fuelRod.is(Items.BLAZE_ROD)) { return 1f; }
        int remaining = DepletedBlazeRodItem.getTicksRemaining(fuelRod);
        return (float) remaining / DepletedBlazeRodItem.FULL_FUEL_TICKS;
    }
}
