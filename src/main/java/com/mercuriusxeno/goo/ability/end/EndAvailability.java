package com.mercuriusxeno.goo.ability.end;

import com.mercuriusxeno.goo.ability.program.EndStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TartarusStep;
import com.mercuriusxeno.goo.ability.tartarus.TartarusAvailability;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Whether End stands available to a player or grays out, read alike by the
 * client drawing it and the server refusing it: in the End it grays out for
 * a player who came by no End gate, holding no way back.
 * decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability
 * Tartarus grays out through the same seam where its gate leads nowhere.
 * decision tartarus-in-the-nether-takes-you-to-the-overworld-at-eight-times-your-spot
 */
public enum EndAvailability {
    /** The player may channel End. */
    AVAILABLE,
    /** End draws grayed out and the server refuses its channel. */
    GRAYED_OUT;

    /**
     * End's availability for a player.
     *
     * @param standingIn   the level the player stands in
     * @param holdsWayBack whether the player keeps a way back from an arrival by the End gate
     * @return the availability
     */
    public static EndAvailability of(ResourceKey<Level> standingIn, boolean holdsWayBack) {
        return standingIn == Level.END && !holdsWayBack ? GRAYED_OUT : AVAILABLE;
    }

    /**
     * Whether an ability running a program grays out for a player: End's does,
     * by {@link #of}, and Tartarus', by {@link TartarusAvailability#of}; every
     * other ability stands available.
     *
     * @param program      the ability's steps
     * @param standingIn   the level the player stands in
     * @param holdsWayBack whether the player keeps a way back from an arrival by the End gate
     * @return true where the ability draws grayed out and the server refuses it
     */
    public static boolean graysOut(List<? extends Step> program, ResourceKey<Level> standingIn,
                                   boolean holdsWayBack) {
        boolean endGraysOut = program.stream().anyMatch(EndStep.class::isInstance)
                && of(standingIn, holdsWayBack) == GRAYED_OUT;
        return endGraysOut || TartarusStep.in(program).isPresent()
                && TartarusAvailability.of(standingIn) == GRAYED_OUT;
    }
}
