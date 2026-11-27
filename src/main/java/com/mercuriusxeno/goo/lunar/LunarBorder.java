package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Lunar's world border stands clear of the seam, at vanilla's default, since the crossing carries bodies across the
 * seam in place of a wall there. A level keeps its border in its own saved data, so a lunar level saved with the old
 * border, centred on 0, 0 and {@link #OLD_SIZE} across, takes vanilla's default as it loads; a border a command moved
 * keeps where it was moved.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LunarBorder {

    /** The width the old border stood across, the seam to the seam. */
    static final double OLD_SIZE = LunarSeam.PERIOD;

    private LunarBorder() {
    }

    /**
     * Takes lunar's old border down as its level loads.
     *
     * @param event the level load event
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Lunar.LEVEL) {
            takeDownOldBorder(level.getWorldBorder());
        }
    }

    /**
     * Returns a border standing where lunar's old one stood to vanilla's default.
     *
     * @param border the border
     */
    static void takeDownOldBorder(WorldBorder border) {
        if (border.getSize() == OLD_SIZE && border.getCenterX() == 0.0 && border.getCenterZ() == 0.0) {
            border.setSize(WorldBorder.Settings.DEFAULT.size());
        }
    }
}
