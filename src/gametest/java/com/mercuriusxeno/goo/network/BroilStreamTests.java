package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for blaze broil: a held broil burns away the leaves its aim line
 * meets, turns the sand under the crosshair into glass, kills the cow the
 * line runs through with fire so it drops cooked beef, and lights no fire
 * (decision broil-heat-wave-ring-cooks-ahead).
 */
@RegistersGameTests
public final class BroilStreamTests {
    private static final String BROIL_COOKS_SAND_AND_COW = "broil_cooks_sand_and_cow";

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its feet. */
    private static final BlockPos SAND_POS = STAND_POS.east(3);
    /** Right behind the sand, its west face against the sand's, its top inside the cone. */
    private static final BlockPos HIDDEN_POS = SAND_POS.east();
    private static final String BROIL_SPARES_THE_HIDDEN_BLOCK = "broil_spares_the_hidden_block";
    private static final String BROIL_POPS_CHARCOAL_FROM_A_LOG = "broil_pops_charcoal_from_a_log";
    /** Two blocks east, between the player and the sand, inside the cone. */
    private static final BlockPos COW_POS = STAND_POS.east(2);
    /** One block east and one up, where the aim line toward the sand passes. */
    private static final BlockPos LEAVES_POS = STAND_POS.east(1).above();
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the sand's center three blocks off. */
    private static final float LOOKING_AT_SAND = 20.5f;
    private static final int HELD_GOO = 6;
    /**
     * The leaves burn on the first held tick and blaze_broil.json cooks the sand after forty more; a ten-health
     * cow dies on the fourth 3-damage pulse.
     */
    private static final int HOLD_TICKS = 44;
    private static final double ITEM_SEARCH_RADIUS = 4;
    /** The cells around the test the fire check sweeps. */
    private static final BlockPos FIRE_SWEEP_FROM = new BlockPos(0, 1, 0);
    private static final BlockPos FIRE_SWEEP_TO = new BlockPos(5, 4, 4);
    private static final Identifier BLAZE_BROIL = Identifier.parse("goo:blaze_broil");
    private static final String ABILITY_REQUIRED = "Ability registry must hold blaze_broil";
    private static final String COW_SHOULD_DIE = "The broiled cow should be dead";
    private static final String NO_RAW_BEEF = "The broiled cow should drop no raw beef";

    private BroilStreamTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BROIL_COOKS_SAND_AND_COW, BroilStreamTests::broilCooksSandAndCow);
        GooTestFunctions.registerTest(registrar, BROIL_SPARES_THE_HIDDEN_BLOCK,
                BroilStreamTests::broilSparesTheHiddenBlock);
        GooTestFunctions.registerTest(registrar, BROIL_POPS_CHARCOAL_FROM_A_LOG,
                BroilStreamTests::broilPopsCharcoalFromALog);
    }

    /**
     * A mock player holds broil on an oak log, whose furnace result is an
     * item: the log is gone and its charcoal lies where it stood.
     * decision broil-heat-wave-ring-cooks-ahead
     *
     * @param helper the gametest helper
     */
    public static void broilPopsCharcoalFromALog(GameTestHelper helper) {
        helper.setBlock(SAND_POS.below(), Blocks.BEDROCK);
        helper.setBlock(SAND_POS, Blocks.OAK_LOG);
        ServerPlayer player = broiler(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.AIR, SAND_POS);
            helper.assertItemEntityPresent(Items.CHARCOAL, SAND_POS, ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    /**
     * A mock player holds broil on sand with more sand right behind it, its
     * top open to the air inside the cone but hidden from the eye by the
     * aimed sand: the aimed sand becomes glass and the hidden sand stays sand.
     * broil-heat-wave-ring-cooks-ahead: Broil hits only what the player looks at
     *
     * @param helper the gametest helper
     */
    public static void broilSparesTheHiddenBlock(GameTestHelper helper) {
        helper.setBlock(SAND_POS.below(), Blocks.BEDROCK);
        helper.setBlock(SAND_POS, Blocks.SAND);
        helper.setBlock(HIDDEN_POS.below(), Blocks.BEDROCK);
        helper.setBlock(HIDDEN_POS, Blocks.SAND);
        ServerPlayer player = broiler(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.GLASS, SAND_POS);
            helper.assertBlockPresent(Blocks.SAND, HIDDEN_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player holds broil on sand past leaves and a cow on its aim
     * line: the leaves are gone, the sand becomes glass, the cow drops
     * cooked beef, and no fire stands.
     *
     * @param helper the gametest helper
     */
    public static void broilCooksSandAndCow(GameTestHelper helper) {
        helper.setBlock(SAND_POS.below(), Blocks.BEDROCK);
        helper.setBlock(SAND_POS, Blocks.SAND);
        helper.setBlock(LEAVES_POS, Blocks.OAK_LEAVES);
        helper.setBlock(COW_POS.below(), Blocks.BEDROCK);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        ServerPlayer player = broiler(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.GLASS, SAND_POS);
            helper.assertBlockNotPresent(Blocks.OAK_LEAVES, LEAVES_POS);
            helper.assertTrue(!cow.isAlive(), COW_SHOULD_DIE);
            helper.assertItemEntityPresent(Items.COOKED_BEEF, COW_POS, ITEM_SEARCH_RADIUS);
            helper.assertTrue(helper.getEntities(EntityType.ITEM, COW_POS, ITEM_SEARCH_RADIUS).stream()
                    .noneMatch(item -> item.getItem().is(Items.BEEF)), NO_RAW_BEEF);
            BlockPos.betweenClosed(FIRE_SWEEP_FROM, FIRE_SWEEP_TO)
                    .forEach(pos -> helper.assertBlockNotPresent(Blocks.FIRE, pos));
            helper.succeed();
        });
    }

    private static void hold(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.BLAZE), BLAZE_BROIL.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(1 + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer broiler(GameTestHelper helper) {
        AbilityDefinition broil = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_BROIL);
        helper.assertTrue(broil != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_SAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.BLAZE, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, broil);
        return player;
    }
}
