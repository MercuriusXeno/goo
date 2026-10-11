package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Opens Astral's gate: a pair whose near gate lies on the struck face and
 * whose mirror lies on the arrival floor of the lunar dimension by the
 * overworld's night and of the solar dimension by its day. Travellers return
 * through the same pair, which closes on its clock as a Dragon Gate pair does.
 * decision astral-visits-lunar-and-solar-dimensions
 */
public final class AstralGateOpening {

    /** The grey, dusty dimension the gate reaches by night. */
    public static final ResourceKey<Level> LUNAR = dimension("lunar");
    /** The blinding, bright dimension the gate reaches by day. */
    public static final ResourceKey<Level> SOLAR = dimension("solar");
    /**
     * The top block of both dimensions' flat layers under the world origin,
     * where every traveller arrives; the layers in data/goo/dimension stack
     * from y 0 to this height.
     */
    public static final BlockPos ARRIVAL_FLOOR = new BlockPos(0, 63, 0);
    /** Ticks in one overworld day. */
    static final long DAY_LENGTH = 24_000L;
    /** The day time night begins at, when monsters start to spawn. */
    static final long NIGHT_START = 13_000L;
    /** The day time night ends at, as the sun rises. */
    static final long NIGHT_END = 23_000L;

    private AstralGateOpening() {
    }

    /**
     * Opens a pair on a struck face. A cast inside the lunar or solar
     * dimension, or one whose destination the server does not hold, lays
     * nothing; the way back is the pair the traveller came through.
     *
     * @param level    the level the blob landed in
     * @param surface  the struck block
     * @param face     the struck face
     * @param lifetime the ticks the pair stands
     * @return true once the pair stands open
     */
    public static boolean open(ServerLevel level, BlockPos surface, Direction face, int lifetime) {
        if (level.dimension() == LUNAR || level.dimension() == SOLAR) {
            return false;
        }
        ServerLevel mirror = level.getServer().getLevel(destinationAt(level.getOverworldClockTime()));
        return mirror != null
                && DragonGateOpening.openPair(level, surface, face, lifetime, mirror, ARRIVAL_FLOOR);
    }

    /**
     * The dimension the gate reaches at a day time: lunar by night, solar by day.
     *
     * @param dayTime the overworld's day time, any number of days in
     * @return the dimension's key
     */
    public static ResourceKey<Level> destinationAt(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, DAY_LENGTH);
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END ? LUNAR : SOLAR;
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }
}
