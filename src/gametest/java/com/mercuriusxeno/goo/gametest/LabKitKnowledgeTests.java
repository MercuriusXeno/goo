package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Collection;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the lab's knowledge routes: the Goo Lab save's first join
 * teaches every item recipe (LabFirstJoinTests), so the lab keeps unlearned
 * items only after {@code /goo lab forget} returns the player to knowing
 * nothing; {@code /goo lab learn} teaches every recipe again so every loaded
 * ability stands unlocked, and the kit teaches nothing.
 * decision lab-kit-teaches-every-recipe
 */
@RegistersGameTests
public final class LabKitKnowledgeTests {
    private static final String LAB_LEARN_TEACHES_EVERY_ABILITY = "lab_learn_teaches_every_ability";
    private static final String LAB_FORGET_RETURNS_TO_NOTHING = "lab_forget_returns_to_nothing";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LAB_LEARN_TEACHES_EVERY_ABILITY, LabKitKnowledgeTests::learnTeachesEveryAbility);
        GooTestFunctions.registerTest(registrar, LAB_FORGET_RETURNS_TO_NOTHING, LabKitKnowledgeTests::forgetReturnsToKnowingNothing);
    }


    private static final String REMOVAL = "removal";
    private static final String NO_ABILITIES = "The gametest server should load abilities to unlock";
    private static final String ABILITY_LOCKED = "Learn should unlock the ability ";
    private static final String STONE_UNKNOWN = "Learn should teach stone, so its goo tooltip line shows";
    private static final String STONE_VALUELESS = "Stone should hold a goo value for its tooltip line to show";

    private LabKitKnowledgeTests() {
    }

    /**
     * Runs the learn route on a mock player and asserts every loaded ability is known
     * to them and stone, which holds a goo value, is a known item.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void learnTeachesEveryAbility(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabKit.learn(player, helper.getLevel());
        KnownItems known = PlayerKnowledge.of(player);
        Collection<AbilityDefinition> abilities = AbilityRegistry.of(helper.getLevel()).all();
        helper.assertFalse(abilities.isEmpty(), NO_ABILITIES);
        for (AbilityDefinition ability : abilities) {
            helper.assertTrue(ability.isKnownTo(known), ABILITY_LOCKED + ability.id());
        }
        helper.assertTrue(known.contains(PlayerKnowledge.idOf(Items.STONE)), STONE_UNKNOWN);
        GooValue stoneValue = GooValues.of(helper.getLevel()).lookup(new ItemStack(Items.STONE));
        helper.assertTrue(stoneValue != null && !stoneValue.isEmpty(), STONE_VALUELESS);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Runs the learn route, then the forget route, and asserts the player knows no item.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void forgetReturnsToKnowingNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabKit.learn(player, helper.getLevel());
        helper.assertFalse(PlayerKnowledge.of(player).items().isEmpty(), "Learn should teach the player something");
        LabKit.forget(player);
        assertKnowsNothing(helper, player, "Forget should leave the player knowing no item");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Asserts the player knows no item; AbilityDefinitionTest holds which abilities a player knowing none holds.
     *
     * @param helper  the gametest helper
     * @param player  the player
     * @param message the failure message for a known item
     */
    private static void assertKnowsNothing(GameTestHelper helper, ServerPlayer player, String message) {
        KnownItems known = PlayerKnowledge.of(player);
        helper.assertTrue(known.items().isEmpty(), message);
    }
}
