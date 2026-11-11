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
    private static final String GLOVE_GATED_SELECTION_REFUSED = "glove_gated_selection_refused";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GLOVE_TYPE_ONLY_REFUSED, GloveSelectTests::typeOnlySelectionRefused);
        GooTestFunctions.registerTest(registrar, GLOVE_GATED_SELECTION_REFUSED, GloveSelectTests::gatedSelectionRefusedWithoutTheRecipe);
    }


    private static final String BLAZE_ID = GooTypes.id(GooTypes.BLAZE);
    private static final String BLAZE_IGNITE = "goo:blaze_ignite";
    private static final String BLAZE_FLAMES = "goo:blaze_flames";
    private static final String REMOVAL = "removal";
    private static final String NO_ABILITY = "";
    private static final String TYPE_ONLY_TOOK = "A type with no ability changed the glove's selection";
    private static final String ABILITY_NOT_HELD = "The glove does not hold the type and ability sent";

    private GloveSelectTests() {
    }

    /**
     * Sends a type with no ability to an empty glove, which stays empty,
     * then a type with its ability, which the glove holds.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void typeOnlySelectionRefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerKnowledge.learn(player, Items.FLINT);
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GloveSelectHandler.resolveAndApply(player, glove, new GloveSelectPayload(BLAZE_ID, NO_ABILITY));
        helper.assertTrue(GooGloveItem.getSelection(glove) == null, TYPE_ONLY_TOOK);

        GloveSelectHandler.resolveAndApply(player, glove, new GloveSelectPayload(BLAZE_ID, BLAZE_IGNITE));
        GloveSelection held = GooGloveItem.getSelection(glove);
        helper.assertTrue(held != null && BLAZE_ID.equals(held.gooTypeId())
                && BLAZE_IGNITE.equals(held.abilityId()), ABILITY_NOT_HELD);

        GloveSelectHandler.resolveAndApply(player, glove, new GloveSelectPayload(BLAZE_ID, NO_ABILITY));
        helper.assertTrue(held.equals(GooGloveItem.getSelection(glove)), TYPE_ONLY_TOOK);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A player who has never melted a torchflower selects blaze flames and
     * the glove stays empty; once they know torchflower, the same selection
     * holds (decision ability-hidden-until-recipes-known).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void gatedSelectionRefusedWithoutTheRecipe(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GloveSelectPayload flames = new GloveSelectPayload(BLAZE_ID, BLAZE_FLAMES);

        GloveSelectHandler.resolveAndApply(player, glove, flames);
        helper.assertTrue(GooGloveItem.getSelection(glove) == null,
                "A player without the torchflower recipe should not select blaze flames");

        PlayerKnowledge.learn(player, Items.TORCHFLOWER);
        GloveSelectHandler.resolveAndApply(player, glove, flames);
        GloveSelection held = GooGloveItem.getSelection(glove);
        helper.assertTrue(held != null && BLAZE_FLAMES.equals(held.abilityId()), ABILITY_NOT_HELD);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
