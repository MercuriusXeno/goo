package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Lunar's world border, its own as every level's is: centred on 0, 0 and {@link Lunar#BORDER_HALF_WIDTH} each way on
 * x and z. A level keeps its border in its own saved data, so lunar's is stood where the level loads, in a new world
 * and in one saved before the border, while the overworld's and every other level's stay as they are. A border still
 * at vanilla's default takes lunar's; one a command moved keeps where it was moved.
 * decision moon-is-a-quarter-the-overworld-bordered-at-seven-and-a-half-million
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LunarBorder {

    /** The border's width across. */
    static final double SIZE = 2.0 * Lunar.BORDER_HALF_WIDTH;

    private LunarBorder() {
    }

    /**
     * Stands lunar's border as its level loads.
     *
     * @param event the level load event
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Lunar.LEVEL) {
            standBorder(level.getWorldBorder());
        }
    }

    /**
     * Centres a border still at vanilla's default on 0, 0 and sizes it to lunar's width.
     *
     * @param border the border
     */
    static void standBorder(WorldBorder border) {
        if (border.getSize() != WorldBorder.Settings.DEFAULT.size()) {
            return;
        }
        border.setCenter(0.0, 0.0);
        border.setSize(SIZE);
    }
}
