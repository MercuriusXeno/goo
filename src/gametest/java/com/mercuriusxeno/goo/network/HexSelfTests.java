package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for hex's self abilities, invoked from the glove the way a
 * real invocation reaches the server.
 */
public final class HexSelfTests {

    private static final Identifier HEX_ENCHANT = Identifier.parse("goo:hex_enchant");
    private static final String ABILITY_REQUIRED = "The hex enchant ability should be loaded";
    private static final String SHOULD_TAKE_THE_BOOK = "Enchant should take the one book, %d remain";
    private static final String SHOULD_GIVE_ONE_BOOK = "Enchant should give one enchanted book, %d given";
    private static final String SHOULD_HOLD_ONE_LEVEL_ONE =
            "The enchanted book should hold one enchantment at level one: %s";

    private static final Identifier HEX_FUSE = Identifier.parse("goo:hex_fuse");
    private static final String FUSE_REQUIRED = "The hex fuse ability should be loaded";
    private static final String SHOULD_FUSE_TO_ONE = "Fuse should leave one enchanted book of the pair, %d stand";
    private static final String SHOULD_RAISE_SHARPNESS = "The fused book should hold Sharpness II alone: %s";
    private static final String SHOULD_DRAIN_COST = "Fuse should drain its cost of %d, drained %d";
    private static final String SHOULD_DRAIN_NOTHING = "A refused Fuse should drain nothing, drained %d";
    private static final String SHOULD_KEEP_BOOKS = "A refused Fuse should leave both books, %d stand";
    private static final int SHARPNESS_TWO = 2;
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);

    private static final Identifier HEX_LIFETAP = Identifier.parse("goo:hex_lifetap");
    private static final String LIFETAP_REQUIRED = "The hex lifetap ability should be loaded";
    private static final String SHOULD_HOLD_LIFETAP = "The finished eat should hold a lifetap";
    private static final String SHOULD_NOT_REGEN = "A lifetapped player at full hunger should not regenerate, health %.2f to %.2f";
    private static final String SHOULD_LEECH = "A hit dealing %.2f should heal the lifetapped player by %.2f, healed %.2f";
    /** A player's health low enough that food would regenerate it. */
    private static final float HURT_HEALTH = 10f;
    /** Ticks watched for regeneration, past several of food's saturated heals. */
    private static final int WATCHED_TICKS = 200;
    private static final float HIT_DAMAGE = 4f;
    /** hex_lifetap.json's fraction. */
    private static final float LEECH_FRACTION = 0.3f;
    private static final float HEAL_TOLERANCE = 0.01f;
    /** Hex goo the lifetapper carries, enough upkeep for the watch. */
    private static final int HELD_HEX = 2000;

    private HexSelfTests() {
    }

    /**
     * A survival player at full hunger eats Lifetap from the glove: over
     * the watch food never regenerates their health, and a hit on a zombie
     * heals them by the fraction of the damage it dealt past the zombie's armor
     * (decision lifetap-trades-regen-for-leech).
     *
     * @param helper the gametest helper
     */
    public static void lifetapNoRegenLeechOnHit(GameTestHelper helper) {
        AbilityDefinition lifetap = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_LIFETAP);
        helper.assertTrue(lifetap != null, LIFETAP_REQUIRED);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.HEX, HELD_HEX));
        KnownRecipes.teachRequires(player, lifetap);
        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_LIFETAP);
        SelfDeliveryTests.eatThrough(player);
        helper.assertTrue(player.getData(GooAttachments.LIFETAP).standsAt(player.level().getGameTime()),
                SHOULD_HOLD_LIFETAP);
        player.getFoodData().eat(20, 20f);
        player.setHealth(HURT_HEALTH);
        for (int tick = 0; tick < WATCHED_TICKS; tick++) {
            player.doTick();
        }
        float afterWatch = player.getHealth();
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        float zombieBefore = zombie.getHealth();
        zombie.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), HIT_DAMAGE);
        float dealt = zombieBefore - zombie.getHealth();
        float healed = player.getHealth() - afterWatch;
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(afterWatch <= HURT_HEALTH, String.format(SHOULD_NOT_REGEN, HURT_HEALTH, afterWatch));
        float expected = dealt * LEECH_FRACTION;
        helper.assertTrue(dealt > 0f && Math.abs(healed - expected) < HEAL_TOLERANCE,
                String.format(SHOULD_LEECH, dealt, expected, healed));
        helper.succeed();
    }

    /**
     * A player carrying two Sharpness I books invokes Fuse: one Sharpness II
     * book stands in their place and Fuse's cost drains
     * (decision fuse-two-books-for-hex-goo).
     *
     * @param helper the gametest helper
     */
    public static void fuseTwoSharpnessOne(GameTestHelper helper) {
        AbilityDefinition fuse = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_FUSE);
        helper.assertTrue(fuse != null, FUSE_REQUIRED);
        ServerPlayer player = fuser(helper, fuse);
        Holder<Enchantment> sharpness = sharpness(helper);
        player.getInventory().add(sharpnessOne(sharpness));
        player.getInventory().add(sharpnessOne(sharpness));
        int heldBefore = heldHex(player);

        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_FUSE);

        int drained = heldBefore - heldHex(player);
        List<ItemStack> books = stacksOf(player.getInventory(), Items.ENCHANTED_BOOK);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(books.size() == 1, String.format(SHOULD_FUSE_TO_ONE, books.size()));
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(books.getFirst());
        helper.assertTrue(stored.size() == 1 && stored.getLevel(sharpness) == SHARPNESS_TWO,
                String.format(SHOULD_RAISE_SHARPNESS, stored));
        helper.assertTrue(drained == fuse.cost(), String.format(SHOULD_DRAIN_COST, fuse.cost(), drained));
        helper.succeed();
    }

    /**
     * A player carrying one Sharpness I book and no pair invokes Fuse: it is
     * refused before the cost, so no goo drains and the book stands
     * (decision fuse-two-books-for-hex-goo).
     *
     * @param helper the gametest helper
     */
    public static void fuseWithoutPairCostsNothing(GameTestHelper helper) {
        AbilityDefinition fuse = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_FUSE);
        helper.assertTrue(fuse != null, FUSE_REQUIRED);
        ServerPlayer player = fuser(helper, fuse);
        player.getInventory().add(sharpnessOne(sharpness(helper)));
        int heldBefore = heldHex(player);

        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_FUSE);

        int drained = heldBefore - heldHex(player);
        int books = stacksOf(player.getInventory(), Items.ENCHANTED_BOOK).size();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
        helper.assertTrue(books == 1, String.format(SHOULD_KEEP_BOOKS, books));
        helper.succeed();
    }

    private static ServerPlayer fuser(GameTestHelper helper, AbilityDefinition fuse) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.HEX, HEX_FUSE);
        KnownRecipes.teachRequires(player, fuse);
        return player;
    }

    private static Holder<Enchantment> sharpness(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
    }

    private static ItemStack sharpnessOne(Holder<Enchantment> sharpness) {
        return EnchantmentHelper.createBook(new EnchantmentInstance(sharpness, 1));
    }

    private static int heldHex(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.HEX, 0);
    }

    /**
     * A player holding one book invokes Enchant: the book is gone and one
     * enchanted book stands in its place, holding a single enchantment at
     * level one (decision enchant-book-with-a-purple-afterimage).
     *
     * @param helper the gametest helper
     */
    public static void enchantGivesOneLevelOneBook(GameTestHelper helper) {
        AbilityDefinition enchant = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_ENCHANT);
        helper.assertTrue(enchant != null, ABILITY_REQUIRED);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.HEX, HEX_ENCHANT);
        KnownRecipes.teachRequires(player, enchant);
        player.getInventory().add(new ItemStack(Items.BOOK));

        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_ENCHANT);

        int books = stacksOf(player.getInventory(), Items.BOOK).size();
        List<ItemStack> enchanted = stacksOf(player.getInventory(), Items.ENCHANTED_BOOK);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(books == 0, String.format(SHOULD_TAKE_THE_BOOK, books));
        helper.assertTrue(enchanted.size() == 1, String.format(SHOULD_GIVE_ONE_BOOK, enchanted.size()));
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(enchanted.getFirst());
        helper.assertTrue(stored.size() == 1 && stored.entrySet().stream()
                        .allMatch(entry -> entry.getIntValue() == RandomEnchantment.LEVEL),
                String.format(SHOULD_HOLD_ONE_LEVEL_ONE, stored));
        helper.succeed();
    }

    private static List<ItemStack> stacksOf(Inventory inventory, Item item) {
        List<ItemStack> found = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                found.add(stack);
            }
        }
        return found;
    }
}
