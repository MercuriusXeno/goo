package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.KineticSlickBlock;
import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * What Goop's slime and Slick's film cost and give back when broken, read
 * from the loaded ability definitions as the blocks read them, so a test
 * follows a price the workbench retunes.
 * breaking-slime-drops-weird-goo-worth-its-layers
 * breaking-slick-drops-kinetic-goo-worth-its-film
 */
final class ResidualPrices {

    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";

    private ResidualPrices() {
    }

    /**
     * One slime layer's price: Goop's cost over the layers a whole block holds.
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    static GooFraction layerPrice(GameTestHelper helper) {
        return costOf(helper, SlimeCoatBlock.GOOP_ABILITY).dividedBy(SlimeCoatBlock.MAX_LAYERS);
    }

    /**
     * One slick film's price: Slick's cost.
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    static GooFraction filmPrice(GameTestHelper helper) {
        return costOf(helper, KineticSlickBlock.SLICK_ABILITY);
    }

    /**
     * The weird goo a broken slime cell gives back.
     *
     * @param helper the gametest helper
     * @param layers the layers the cell held
     * @return the whole mb
     */
    static int layersRefund(GameTestHelper helper, int layers) {
        return layerPrice(helper).times(layers).floor();
    }

    /**
     * The kinetic goo broken slick films give back.
     *
     * @param helper the gametest helper
     * @param films  the films broken
     * @return the whole mb
     */
    static int filmsRefund(GameTestHelper helper, int films) {
        return filmPrice(helper).floor() * films;
    }

    private static GooFraction costOf(GameTestHelper helper, Identifier ability) {
        AbilityDefinition definition = AbilityRegistry.of(helper.getLevel()).getAbility(ability);
        helper.assertTrue(definition != null, String.format(ABILITY_REQUIRED, ability));
        return definition.cost().exactIn(GooValues.of(helper.getLevel()));
    }
}
