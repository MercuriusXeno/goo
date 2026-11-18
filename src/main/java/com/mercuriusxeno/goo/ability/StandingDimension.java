package com.mercuriusxeno.goo.ability;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The dimension the local player stands in, for the sites that show an ability's face with no player in reach, a
 * held effect's name and icon among them: the client setup installs its reader, and a dedicated server leaves it
 * answering the overworld.
 * decision lunar-reads-as-terran-while-you-are-on-the-moon
 */
public final class StandingDimension {

    private static final AtomicReference<Supplier<ResourceKey<Level>>> STANDING_IN =
            new AtomicReference<>(() -> Level.OVERWORLD);

    private StandingDimension() {
    }

    /**
     * The dimension the local player stands in.
     *
     * @return the dimension, the overworld where no player stands
     */
    public static ResourceKey<Level> current() {
        return STANDING_IN.get().get();
    }

    /**
     * Installs the reader of the local player's dimension, from the client setup.
     *
     * @param standingIn answers the dimension the local player stands in
     */
    public static void readFrom(Supplier<ResourceKey<Level>> standingIn) {
        STANDING_IN.set(standingIn);
    }
}
