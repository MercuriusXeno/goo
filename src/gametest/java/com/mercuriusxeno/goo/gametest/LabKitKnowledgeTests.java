package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Collection;

/**
 * Gametests that knowledge comes on request: {@code /goo lab learn} teaches every
 * item recipe so every loaded ability stands unlocked, {@code /goo lab forget}
 * returns the player to knowing nothing, and the kit teaches nothing.
 * decision lab-kit-teaches-every-recipe
 */
public final class LabKitKnowledgeTests {

    private static final String REMOVAL = "removal";
    private static final String NO_ABILITIES = "The gametest server should load abilities to unlock";
    private static final String ABILITY_LOCKED = "Learn should unlock the ability ";
    private static final String STONE_UNKNOWN = "Learn should teach stone, so its goo tooltip line shows";
    private static final String STONE_VALUELESS = "Stone should hold a goo value for its tooltip line to show";
    private static final String KNOWS_SOMETHING = "A player who never ran learn should know no item";
    private static final String NO_GATED_ABILITY = "The gametest server should load an ability that requires an item";
    private static final String LOCKED_ABILITY_UNLOCKED = "A player who knows nothing should not hold the ability ";

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
     * Runs the learn route, then the forget route, and asserts the player knows no item
     * and every ability that requires one is locked again.
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
     * Hands the kit to a mock player who knows nothing and asserts they still know
     * nothing while the inventory holds the kit.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void kitTeachesNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabKit.give(player, helper.getLevel());
        helper.assertTrue(player.getInventory().contains(new ItemStack(GooItems.GOO_GLOVE.get())),
                "The kit should fill the inventory");
        assertKnowsNothing(helper, player, "The kit should teach the player no item");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Asserts a mock player who never ran learn knows no item and holds none of
     * the abilities that require one.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void untouchedPlayerKnowsNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        assertKnowsNothing(helper, player, KNOWS_SOMETHING);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Asserts the player knows no item and holds no ability that requires one.
     *
     * @param helper  the gametest helper
     * @param player  the player
     * @param message the failure message for a known item
     */
    private static void assertKnowsNothing(GameTestHelper helper, ServerPlayer player, String message) {
        KnownItems known = PlayerKnowledge.of(player);
        helper.assertTrue(known.items().isEmpty(), message);
        Collection<AbilityDefinition> abilities = AbilityRegistry.of(helper.getLevel()).all();
        helper.assertTrue(abilities.stream().anyMatch(ability -> !ability.requires().isEmpty()), NO_GATED_ABILITY);
        for (AbilityDefinition ability : abilities) {
            helper.assertTrue(ability.requires().isEmpty() || !ability.isKnownTo(known),
                    LOCKED_ABILITY_UNLOCKED + ability.id());
        }
    }
}
