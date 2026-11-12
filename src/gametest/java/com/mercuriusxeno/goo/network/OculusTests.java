package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.oculus.OculusNodes;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for the oculus prism as a blink node: a player looking at an
 * oculus well past Blink's range blinks to stand beside it, a pressed face
 * or not, and every blink to an oculus costs a tenth of its price
 * (decision oculus-prism-becomes-a-hovering-eye); a blink at a bare prism
 * spends itself into the prism, which becomes an oculus
 * (decision blink-at-a-prism-spends-into-an-oculus).
 */
@RegistersGameTests
public final class OculusTests {
    private static final String OCULUS_COSTS_A_TENTH = "oculus_blink_costs_a_tenth";
    private static final String BLINK_MAKES_AN_OCULUS = "blink_at_a_bare_prism_makes_an_oculus";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, OCULUS_COSTS_A_TENTH, OculusTests::oculusBlinkLandsBesideTheOculus);
        GooTestFunctions.registerTest(registrar, BLINK_MAKES_AN_OCULUS, OculusTests::blinkAtABarePrismMakesAnOculus);
    }


    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    /** On the floor four blocks east of where the blinker stands, in Blink's range. */
    private static final BlockPos BARE_PRISM_POS = new BlockPos(5, 1, 3);
    private static final String SHOULD_SPEND_THE_TRIP = "A blink into a prism should drain its trip's %d, drained %d";
    private static final String SHOULD_BECOME_AN_OCULUS = "The prism should hold the oculus combo, held '%s'";
    /** Straight up over the player, past Blink's range of eight and inside its node range of forty-eight. */
    private static final int NODE_HEIGHT = 24;
    /** Enough ender goo for the trip up to the oculus. */
    private static final int EXTRA_GOO = 8 * GooStacks.THOUSAND;
    private static final float LOOKING_STRAIGHT_UP = -90f;
    private static final int NO_ENTITY = -1;
    /** A landing beside the oculus stands one cell off its own, give or take. */
    private static final double BESIDE = 1.05;
    private static final String ABILITY_REQUIRED = "%s must be loaded";
    private static final String SHOULD_SNAP = "The blink should land beside the oculus at %s, landed at %s";
    private static final String SHOULD_PAY_THE_TRIP = "A blink to the oculus should pay for its trip, drained %d";

    private OculusTests() {
    }

    /**
     * A blink up to an oculus lands beside it and pays for the trip.
     *
     * @param helper the gametest helper
     */
    public static void oculusBlinkLandsBesideTheOculus(GameTestHelper helper) {
        ServerPlayer player = blinker(helper);
        PrismBlockEntity oculus = oculusAbove(helper, player);
        int heldBefore = held(player);
        blink(player);
        int drained = heldBefore - held(player);
        Vec3 after = player.position();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(after.distanceTo(Vec3.atBottomCenterOf(oculus.getBlockPos())) < BESIDE,
                String.format(SHOULD_SNAP, oculus.getBlockPos(), after));
        helper.assertTrue(drained > 0, String.format(SHOULD_PAY_THE_TRIP, drained));
        helper.succeed();
    }

    /**
     * A mock player who knows Blink, holds enough ender goo for a long trip
     * and looks straight up.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer blinker(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.ENDER);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ENDER, EXTRA_GOO));
        KnownRecipes.teachRequires(player, ability(helper, ENDER_BLINK));
        player.setXRot(LOOKING_STRAIGHT_UP);
        return player;
    }

    /**
     * Grows an oculus prism on a stone block hanging over the player.
     *
     * @param helper the gametest helper
     * @param player the player it hangs over
     * @return the oculus's block entity
     */
    private static PrismBlockEntity oculusAbove(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = player.blockPosition().above(NODE_HEIGHT);
        level.setBlockAndUpdate(cell.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(cell, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        PrismBlockEntity prism = (PrismBlockEntity) level.getBlockEntity(cell);
        helper.assertTrue(prism != null, "The prism should hold its block entity");
        prism.runCombo(GooTypes.ENDER, OculusNodes.OCULUS, PrismCombos.comboSteps(ability(helper, ENDER_BLINK)));
        return prism;
    }

    /**
     * A player looking at a bare prism on the floor four blocks off blinks:
     * the ender drained is Blink's price for the trip to the prism, and the
     * prism holds the oculus combo.
     * blink-at-a-prism-spends-into-an-oculus
     *
     * @param helper the gametest helper
     */
    public static void blinkAtABarePrismMakesAnOculus(GameTestHelper helper) {
        ServerPlayer player = blinker(helper);
        helper.setBlock(BARE_PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(BARE_PRISM_POS, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING,
                Direction.UP));
        BlockPos prismAt = helper.absolutePos(BARE_PRISM_POS);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(prismAt));
        Vec3 start = player.position();
        AbilityDefinition blink = ability(helper, ENDER_BLINK);
        int price = blink.distancePrice().priceOf(blink.cost().in(GooValueTable.EMPTY),
                Optional.of(BlinkLanding.converting(start, start.distanceTo(Vec3.atCenterOf(prismAt)), prismAt)));
        int heldBefore = held(player);

        blink(player);

        int drained = heldBefore - held(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        String combo = helper.getBlockEntity(BARE_PRISM_POS, PrismBlockEntity.class).getCombo();
        helper.assertTrue(drained == price, String.format(SHOULD_SPEND_THE_TRIP, price, drained));
        helper.assertTrue(OculusNodes.OCULUS.equals(combo), String.format(SHOULD_BECOME_AN_OCULUS, combo));
        helper.succeed();
    }

    /**
     * Throws Blink from the glove, the path a press takes.
     *
     * @param player the player
     */
    private static void blink(ServerPlayer player) {
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(GooTypes.ENDER, ENDER_BLINK));
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.ENDER), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, ENDER_BLINK.toString(), player.getEyePosition()));
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ENDER, 0);
    }
}
