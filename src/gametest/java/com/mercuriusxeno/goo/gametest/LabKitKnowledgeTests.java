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

/**
 * Gametests that the lab kit teaches its player every item recipe, so every
 * loaded ability stands unlocked and the goo tooltip reveals an item's value,
 * while a player who never took the kit still knows nothing.
 * decision lab-kit-teaches-every-recipe
 */
public final class LabKitKnowledgeTests {

    private static final String REMOVAL = "removal";
    private static final String NO_ABILITIES = "The gametest server should load abilities to unlock";
    private static final String ABILITY_LOCKED = "Kit should unlock the ability ";
    private static final String STONE_UNKNOWN = "Kit should teach stone, so its goo tooltip line shows";
    private static final String STONE_VALUELESS = "Stone should hold a goo value for its tooltip line to show";
    private static final String KNOWS_SOMETHING = "A player who never took the kit should know no item";
    private static final String NO_GATED_ABILITY = "The gametest server should load an ability that requires an item";
    private static final String LOCKED_ABILITY_UNLOCKED = "A player who never took the kit should not hold the ability ";

    private LabKitKnowledgeTests() {
    }

    /**
     * Runs the kit on a mock player and asserts every loaded ability is known
     * to them and stone, which holds a goo value, is a known item.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void kitTeachesEveryAbility(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabKit.give(player, helper.getLevel());
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
     * Asserts a mock player who never took the kit knows no item and holds
     * none of the abilities that require one.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void untouchedPlayerKnowsNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        KnownItems known = PlayerKnowledge.of(player);
        helper.assertTrue(known.items().isEmpty(), KNOWS_SOMETHING);
        Collection<AbilityDefinition> abilities = AbilityRegistry.of(helper.getLevel()).all();
        helper.assertTrue(abilities.stream().anyMatch(ability -> !ability.requires().isEmpty()), NO_GATED_ABILITY);
        for (AbilityDefinition ability : abilities) {
            helper.assertTrue(ability.requires().isEmpty() || !ability.isKnownTo(known), LOCKED_ABILITY_UNLOCKED + ability.id());
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
