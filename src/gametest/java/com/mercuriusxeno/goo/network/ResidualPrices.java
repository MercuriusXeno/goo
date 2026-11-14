package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * What a layer of Goop's slime and of Slick's slick cost and give back when broken, read
 * from the loaded ability definitions as the blocks read them, so a test
 * follows a price the workbench retunes.
 * breaking-slime-drops-weird-goo-worth-its-layers
 * breaking-slick-drops-kinetic-goo-worth-its-film
 */
final class ResidualPrices {

    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    /** The layers a whole block of slime holds, stated here so a test checks the block rather than echoing it. */
    private static final int LAYERS_PER_BLOCK = 15;
    /** The ability whose cost prices a whole block of slime, named here as the test reads it. */
    private static final Identifier GOOP_ABILITY = Identifier.parse("goo:weird_goop");
    /** The ability whose cost prices a whole block of slick, named here as the test reads it. */
    private static final Identifier SLICK_ABILITY = Identifier.parse("goo:kinetic_slick");

    private ResidualPrices() {
    }

    /**
     * One slime layer's price: Goop's cost, a cubic meter's, over the fifteen layers a whole block holds.
     * world-goo-fills-a-block-space-in-one-to-fifteen-layers
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    static GooFraction layerPrice(GameTestHelper helper) {
        return costOf(helper, GOOP_ABILITY).dividedBy(LAYERS_PER_BLOCK);
    }

    /**
     * One slick layer's price: Slick's cost, a cubic meter's, over the fifteen layers a whole block holds.
     * slick-drains-per-film-it-lays
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    static GooFraction slickLayerPrice(GameTestHelper helper) {
        return costOf(helper, SLICK_ABILITY).dividedBy(LAYERS_PER_BLOCK);
    }

    /**
     * The kinetic goo a broken slick cell gives back.
     *
     * @param helper the gametest helper
     * @param layers the layers the cell held
     * @return the whole mb
     */
    static int slickLayersRefund(GameTestHelper helper, int layers) {
        return slickLayerPrice(helper).times(layers).floor();
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

    private static GooFraction costOf(GameTestHelper helper, Identifier ability) {
        AbilityDefinition definition = AbilityRegistry.of(helper.getLevel()).getAbility(ability);
        helper.assertTrue(definition != null, String.format(ABILITY_REQUIRED, ability));
        return definition.cost().exactIn(GooValues.of(helper.getLevel()));
    }
}
