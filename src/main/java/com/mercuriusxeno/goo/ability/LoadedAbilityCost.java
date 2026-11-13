package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;

/**
 * An ability's cost as the level's loaded definition and goo values price
 * it, the one number a residual's charge and its refund both read, so a
 * price the workbench changes moves both.
 * breaking-slime-drops-weird-goo-worth-its-layers
 * breaking-slick-drops-kinetic-goo-worth-its-film
 */
public final class LoadedAbilityCost {

    private LoadedAbilityCost() {
    }

    /**
     * The ability's exact cost in the level.
     *
     * @param level   the server level
     * @param ability the ability's id
     * @return the cost, empty where no definition of the ability loads
     */
    public static Optional<GooFraction> of(ServerLevel level, Identifier ability) {
        AbilityDefinition definition = AbilityRegistry.of(level).getAbility(ability);
        return Optional.ofNullable(definition).map(def -> def.cost().exactIn(GooValues.of(level)));
    }
}
