package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.data.GooValueTable;
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
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for hex's self abilities, invoked from the glove the way a
 * real invocation reaches the server.
 */
@RegistersGameTests
public final class HexSelfTests {
    private static final String ENCHANT_GIVES_BOOK = "enchant_gives_one_level_one_book";
    private static final String FUSE_TWO_SHARPNESS_ONE = "fuse_two_sharpness_one";
    private static final String LIFETAP_NO_REGEN_LEECH_ON_HIT = "lifetap_no_regen_leech_on_hit";
    private static final String DRAIN_HEALS_THE_CASTER = "drain_heals_the_caster";
    private static final String DRAIN_SPARES_RALLIED = "drain_spares_the_casters_rallied_cow";
    private static final String DRAIN_SPARES_CHARMED = "drain_spares_the_casters_charmed_mob";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ENCHANT_GIVES_BOOK, HexSelfTests::enchantGivesOneLevelOneBook);
        GooTestFunctions.registerTest(registrar, FUSE_TWO_SHARPNESS_ONE, HexSelfTests::fuseTwoSharpnessOne);
        GooTestFunctions.registerTest(registrar, LIFETAP_NO_REGEN_LEECH_ON_HIT, HexSelfTests::lifetapNoRegenLeechOnHit);
        GooTestFunctions.registerTest(registrar, DRAIN_HEALS_THE_CASTER, HexSelfTests::drainHealsTheCaster);
        GooTestFunctions.registerTest(registrar, DRAIN_SPARES_RALLIED, HexSelfTests::drainSparesRalliedCow);
        GooTestFunctions.registerTest(registrar, DRAIN_SPARES_CHARMED, HexSelfTests::drainSparesCharmedMob);
    }


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
    private static final String SHOULD_DRAIN_COST = "Fuse should drain hex goo, drained %d";
    private static final int SHARPNESS_TWO = 2;
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);

    private static final Identifier HEX_LIFETAP = Identifier.parse("goo:hex_lifetap");
    private static final String LIFETAP_REQUIRED = "The hex lifetap ability should be loaded";
    private static final String SHOULD_HOLD_LIFETAP = "The finished eat should hold a lifetap";
    private static final String SHOULD_NOT_REGEN = "A lifetapped player at full hunger should not regenerate, health %.2f to %.2f";
    private static final String SHOULD_LEECH = "A hit dealing %.2f should heal the lifetapped player, healed %.2f";
    /** A player's health low enough that food would regenerate it. */
    private static final float HURT_HEALTH = 10f;
    /** Ticks watched for regeneration, past several of food's saturated heals. */
    private static final int WATCHED_TICKS = 200;
    private static final float HIT_DAMAGE = 4f;
    /** Hex goo the lifetapper carries, enough upkeep for the watch. */
    private static final int HELD_HEX = 2000;

    private static final Identifier HEX_DRAIN = Identifier.parse("goo:hex_drain");
    private static final String DRAIN_REQUIRED = "The hex drain ability should be loaded";
    private static final String SHOULD_DRAIN_BOTH = "Both zombies in the cone should take damage, %.2f and %.2f of %.2f";
    private static final String SHOULD_HEAL_CASTER = "The draining caster should heal past %.1f, stands at %.2f";
    private static final BlockPos CASTER_POS = new BlockPos(1, 1, 3);
    private static final BlockPos NEAR_ZOMBIE_POS = CASTER_POS.east(2);
    private static final BlockPos SIDE_ZOMBIE_POS = NEAR_ZOMBIE_POS.south();
    private static final float FACING_EAST = -90f;
    private static final float LOOKING_DOWN_AT_ZOMBIES = 15f;
    private static final int HOLD_TICKS = 20;
    private static final String SHOULD_SPARE_OWN = "Drain should leave the caster's own %s at %.2f, stands at %.2f";
    private static final String SHOULD_DRAIN_UNOWNED = "Drain should take health from the unowned zombie, %.2f of %.2f";
    private static final float RALLY_DAMAGE = 6f;

    private HexSelfTests() {
    }

    /**
     * A hurt caster streams Drain over two zombies in its cone: both take
     * damage and the caster heals (decision drain-field-heals-with-the-lifetap-visuals).
     *
     * @param helper the gametest helper
     */
    public static void drainHealsTheCaster(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_ZOMBIE_POS);
        Mob side = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SIDE_ZOMBIE_POS);
        float full = near.getMaxHealth();
        ServerPlayer player = drainer(helper);
        player.setHealth(HURT_HEALTH);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0);
        GooStreamPayload tick = drainTick(player);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(near.getHealth() < full && side.getHealth() < full,
                    String.format(SHOULD_DRAIN_BOTH, near.getHealth(), side.getHealth(), full));
            helper.assertTrue(player.getHealth() > HURT_HEALTH,
                    String.format(SHOULD_HEAL_CASTER, HURT_HEALTH, player.getHealth()));
            helper.succeed();
        });
    }

    /**
     * Drain held over a cow its caster rallied and an unowned zombie leaves
     * the cow whole and drains the zombie; the rally is laid again each held
     * tick, since a rally whose caster holds no Rally stands down
     * (decision drain-skips-your-rallied-and-charmed-mobs).
     *
     * @param helper the gametest helper
     */
    public static void drainSparesRalliedCow(GameTestHelper helper) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, NEAR_ZOMBIE_POS);
        drainSparesOwn(helper, cow, "rallied cow", caster -> cow.setData(GooAttachments.RALLIED,
                new Rallied(caster.getUUID(), RALLY_DAMAGE)));
    }

    /**
     * Drain held over a husk its caster charmed and an unowned zombie
     * leaves the charmed one whole and drains the other
     * (decision drain-skips-your-rallied-and-charmed-mobs).
     *
     * @param helper the gametest helper
     */
    public static void drainSparesCharmedMob(GameTestHelper helper) {
        // a husk, since a zombie catches fire in the bay's daylight and the burn reads as a drain
        Mob charmed = helper.spawnWithNoFreeWill(EntityType.HUSK, NEAR_ZOMBIE_POS);
        drainSparesOwn(helper, charmed, "charmed husk",
                caster -> charmed.setData(GooAttachments.CHARMED, new Charmed(caster.getUUID())));
    }

    private static void drainSparesOwn(GameTestHelper helper, Mob own, String kind, Consumer<ServerPlayer> makeOwn) {
        Mob unowned = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SIDE_ZOMBIE_POS);
        float ownFull = own.getHealth();
        float unownedFull = unowned.getHealth();
        ServerPlayer player = drainer(helper);
        GooStreamPayload tick = drainTick(player);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                makeOwn.accept(player);
                GooStreamHandler.streamTick(player, tick);
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(own.getHealth() == ownFull,
                    String.format(SHOULD_SPARE_OWN, kind, ownFull, own.getHealth()));
            helper.assertTrue(unowned.getHealth() < unownedFull,
                    String.format(SHOULD_DRAIN_UNOWNED, unowned.getHealth(), unownedFull));
            helper.succeed();
        });
    }

    /**
     * A survival player in the bay facing both cone positions, holding a
     * glove, hex goo and Drain's reagent knowledge.
     */
    private static ServerPlayer drainer(GameTestHelper helper) {
        AbilityDefinition drain = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_DRAIN);
        helper.assertTrue(drain != null, DRAIN_REQUIRED);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_DOWN_AT_ZOMBIES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.HEX, HELD_HEX));
        KnownRecipes.teachRequires(player, drain);
        return player;
    }

    private static GooStreamPayload drainTick(ServerPlayer player) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.HEX), HEX_DRAIN.toString(),
                player.getEyePosition(), player.getEyePosition());
    }

    /**
     * A survival player at full hunger eats Lifetap from the glove: over
     * the watch food never regenerates their health, and a hit on a zombie
     * heals them
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
        helper.assertTrue(dealt > 0f && healed > 0f, String.format(SHOULD_LEECH, dealt, healed));
        helper.succeed();
    }

    /**
     * A player carrying two Sharpness I books invokes Fuse: one Sharpness II
     * book stands in their place and hex goo drains
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
        helper.assertTrue(drained > 0, String.format(SHOULD_DRAIN_COST, drained));
        helper.succeed();
    }

    /** An invoker holding Fuse's live cost on top of the goo it starts with, so a retuned cost still fits. */
    private static ServerPlayer fuser(GameTestHelper helper, AbilityDefinition fuse) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.HEX, HEX_FUSE);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.HEX, fuse.cost().in(GooValueTable.EMPTY)));
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
