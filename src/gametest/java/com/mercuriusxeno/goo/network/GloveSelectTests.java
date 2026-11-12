package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for {@link GloveSelectHandler}: a selection naming a type and
 * no ability leaves the glove as it was, and a type with one of its
 * abilities is what the glove then holds (decision
 * no-throw-without-ability). They sit in the handler's package because the
 * handler's apply step is package-private.
 */
@RegistersGameTests
public final class GloveSelectTests {
    private static final String GLOVE_TYPE_ONLY_REFUSED = "glove_type_only_refused";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GLOVE_TYPE_ONLY_REFUSED, GloveSelectTests::typeAndAbilitySelectionHeld);
    }


    private static final String BLAZE_ID = GooTypes.id(GooTypes.BLAZE);
    private static final String BLAZE_IGNITE = "goo:blaze_ignite";
    private static final String REMOVAL = "removal";
    private static final String ABILITY_NOT_HELD = "The glove does not hold the type and ability sent";

    private GloveSelectTests() {
    }

    /**
     * Sends a type with its ability to an empty glove, which the glove holds.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void typeAndAbilitySelectionHeld(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerKnowledge.learn(player, Items.FLINT);
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GloveSelectHandler.resolveAndApply(player, glove, new GloveSelectPayload(BLAZE_ID, BLAZE_IGNITE));
        GloveSelection held = GooGloveItem.getSelection(glove);
        helper.assertTrue(held != null && BLAZE_ID.equals(held.gooTypeId())
                && BLAZE_IGNITE.equals(held.abilityId()), ABILITY_NOT_HELD);

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

}
