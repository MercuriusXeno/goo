package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest that the prism has left the mod: the block registry answers no prism block and the loaded abilities
 * hold no Prism ability. The on_prism reactions and the prism badge leave the source itself, which a grep shows.
 * decision prism-leaves-the-mod
 */
@RegistersGameTests
public final class PrismLeftTests {
    private static final String PRISM_LEFT_THE_REGISTRIES = "prism_left_the_registries";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PRISM_LEFT_THE_REGISTRIES, PrismLeftTests::leftTheRegistries);
    }

    private static final Identifier PRISM_BLOCK = Identifier.parse("goo:prism");
    private static final Identifier PRISM_ABILITY = Identifier.parse("goo:crystal_prism");
    private static final String BLOCK_STANDS = "The block registry should hold no goo:prism";
    private static final String ABILITY_STANDS = "The loaded abilities should hold no goo:crystal_prism";

    private PrismLeftTests() {
    }

    /**
     * The block registry answers no goo:prism and the ability registry answers no goo:crystal_prism.
     *
     * @param helper the gametest helper
     */
    public static void leftTheRegistries(GameTestHelper helper) {
        helper.assertFalse(BuiltInRegistries.BLOCK.containsKey(PRISM_BLOCK), BLOCK_STANDS);
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).getAbility(PRISM_ABILITY) == null, ABILITY_STANDS);
        helper.succeed();
    }
}
