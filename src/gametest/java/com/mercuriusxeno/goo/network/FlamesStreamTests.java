package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.FlamesStep;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for blaze flames cooking: a held flame burns away the leaves its
 * aim line meets, turns the sand under the crosshair into glass, and lights
 * no fire (decision flames-absorbs-broil-and-cooks-blocks).
 */
@RegistersGameTests
public final class FlamesStreamTests {
    private static final String FLAMES_COOKS_SAND = "flames_cooks_sand";

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its feet. */
    private static final BlockPos SAND_POS = STAND_POS.east(3);
    private static final String FLAMES_RESTS_CHARCOAL_WHERE_THE_LOG_STOOD = "flames_rests_charcoal_where_the_log_stood";
    /** How far from the cell's horizontal center a resting item may stand. */
    private static final double AT_REST = 1e-6;
    private static final String CHARCOAL_AT_REST = "The charcoal should stand at rest at the log's cell center";
    /** One block east and one up, where the aim line toward the sand passes. */
    private static final BlockPos LEAVES_POS = STAND_POS.east(1).above();
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the sand's center three blocks off. */
    private static final float LOOKING_AT_SAND = 20.5f;
    private static final int HELD_GOO = 6;
    /** Ticks held past the flame's cook ticks: the leaves burn on the first held tick, the block cooks after the rest. */
    private static final int HOLD_MARGIN = 4;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier BLAZE_FLAMES = Identifier.parse("goo:blaze_flames");
    private static final String ABILITY_REQUIRED = "Ability registry must hold blaze_flames";

    private FlamesStreamTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FLAMES_COOKS_SAND, FlamesStreamTests::flamesCooksSand);
        GooTestFunctions.registerTest(registrar, FLAMES_RESTS_CHARCOAL_WHERE_THE_LOG_STOOD,
                FlamesStreamTests::flamesRestsCharcoalWhereTheLogStood);
    }

    /**
     * A mock player holds flames on an oak log, whose furnace result is an
     * item: the log is gone and its charcoal stands at rest at the center of
     * the cell it stood in, never thrown out of it.
     * decision blocks-that-cook-into-items-cook-like-blocks
     *
     * @param helper the gametest helper
     */
    public static void flamesRestsCharcoalWhereTheLogStood(GameTestHelper helper) {
        helper.setBlock(SAND_POS.below(), Blocks.BEDROCK);
        helper.setBlock(SAND_POS, Blocks.OAK_LOG);
        ServerPlayer player = flamer(helper);
        int holdTicks = holdTicks(helper);
        hold(helper, player, holdTicks);
        helper.runAfterDelay(holdTicks + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.AIR, SAND_POS);
            Vec3 center = Vec3.atCenterOf(helper.absolutePos(SAND_POS));
            helper.assertTrue(helper.getEntities(EntityType.ITEM, SAND_POS, ITEM_SEARCH_RADIUS).stream()
                    .anyMatch(item -> item.getItem().is(Items.CHARCOAL)
                            && Math.abs(item.getX() - center.x) < AT_REST
                            && Math.abs(item.getZ() - center.z) < AT_REST
                            && item.getDeltaMovement().horizontalDistance() < AT_REST), CHARCOAL_AT_REST);
            helper.succeed();
        });
    }

    /**
     * A mock player holds flames on sand past leaves on its aim line: the
     * leaves are gone and the sand becomes glass.
     *
     * @param helper the gametest helper
     */
    public static void flamesCooksSand(GameTestHelper helper) {
        helper.setBlock(SAND_POS.below(), Blocks.BEDROCK);
        helper.setBlock(SAND_POS, Blocks.SAND);
        helper.setBlock(LEAVES_POS, Blocks.OAK_LEAVES);
        ServerPlayer player = flamer(helper);
        int holdTicks = holdTicks(helper);
        hold(helper, player, holdTicks);
        helper.runAfterDelay(holdTicks + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.GLASS, SAND_POS);
            helper.assertBlockNotPresent(Blocks.OAK_LEAVES, LEAVES_POS);
            helper.succeed();
        });
    }

    /**
     * A hold past the flame's cook ticks, read live from the flames step.
     *
     * @param helper the gametest helper
     * @return the ticks to hold
     */
    private static int holdTicks(GameTestHelper helper) {
        AbilityDefinition flames = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_FLAMES);
        helper.assertTrue(flames != null, ABILITY_REQUIRED);
        return flames.behaviors().stream().filter(FlamesStep.class::isInstance).map(FlamesStep.class::cast)
                .findFirst().orElseThrow().cookTicks() + HOLD_MARGIN;
    }

    private static void hold(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.BLAZE), BLAZE_FLAMES.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(1 + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer flamer(GameTestHelper helper) {
        AbilityDefinition flames = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_FLAMES);
        helper.assertTrue(flames != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_SAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.BLAZE, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, flames);
        return player;
    }
}
