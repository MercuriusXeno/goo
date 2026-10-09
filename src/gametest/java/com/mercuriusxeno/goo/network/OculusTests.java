package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.oculus.OculusNodes;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Gametests for the oculus prism as a blink node: a player looking at an
 * oculus well past Blink's range blinks to stand in its cell, paying the
 * trip's price into the oculus's charge, and a charged oculus makes the
 * blink to it free, spending its charge instead
 * (decision oculus-prism-becomes-a-hovering-eye).
 */
public final class OculusTests {

    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    private static final Identifier ENDER_OCULUS = Identifier.parse("goo:ender_oculus");
    /** Straight up over the player, past Blink's range of eight and inside its node range of forty-eight. */
    private static final int NODE_HEIGHT = 24;
    /** Enough ender goo for the trip up to the oculus. */
    private static final int EXTRA_GOO = 8 * GooStacks.THOUSAND;
    /** A charge that covers any trip a test makes. */
    private static final int FULL_CHARGE = 100_000;
    private static final float LOOKING_STRAIGHT_UP = -90f;
    private static final int NO_ENTITY = -1;
    private static final double LANDING_TOLERANCE = 0.05;
    private static final String ABILITY_REQUIRED = "%s must be loaded";
    private static final String SHOULD_SNAP = "The blink should land in the oculus's cell %s, landed at %s";
    private static final String SHOULD_PAY = "The blink should drain its price %d, drained %d";
    private static final String SHOULD_CHARGE = "The oculus should hold the price %d as its charge, holds %d";
    private static final String SHOULD_BE_FREE = "A blink the charge covers should drain nothing, drained %d";
    private static final String SHOULD_SPEND = "The oculus should spend the price %d from its charge, holds %d";

    private OculusTests() {
    }

    /**
     * A player looking straight up at an oculus twenty-four blocks over it
     * blinks: it lands in the oculus's cell, drains the trip's price, and the
     * oculus holds that price as its charge.
     *
     * @param helper the gametest helper
     */
    public static void blinkSnapsToOculus(GameTestHelper helper) {
        ServerPlayer player = blinker(helper);
        PrismBlockEntity oculus = oculusAbove(helper, player);
        Vec3 cell = Vec3.atBottomCenterOf(oculus.getBlockPos());
        int price = priceTo(helper, player, cell);
        int heldBefore = held(player);

        blink(player);

        Vec3 after = player.position();
        int drained = heldBefore - held(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(after.distanceTo(cell) < LANDING_TOLERANCE, String.format(SHOULD_SNAP, cell, after));
        helper.assertTrue(drained == price, String.format(SHOULD_PAY, price, drained));
        helper.assertTrue(oculus.charge() == price, String.format(SHOULD_CHARGE, price, oculus.charge()));
        helper.succeed();
    }

    /**
     * A player blinks to an oculus whose charge covers the trip: it lands in
     * the oculus's cell draining no goo, and the oculus's charge falls by the
     * trip's price.
     *
     * @param helper the gametest helper
     */
    public static void oculusChargeMakesBlinkFree(GameTestHelper helper) {
        ServerPlayer player = blinker(helper);
        PrismBlockEntity oculus = oculusAbove(helper, player);
        oculus.setCharge(FULL_CHARGE);
        Vec3 cell = Vec3.atBottomCenterOf(oculus.getBlockPos());
        int price = priceTo(helper, player, cell);
        int heldBefore = held(player);

        blink(player);

        Vec3 after = player.position();
        int drained = heldBefore - held(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(after.distanceTo(cell) < LANDING_TOLERANCE, String.format(SHOULD_SNAP, cell, after));
        helper.assertTrue(drained == 0, String.format(SHOULD_BE_FREE, drained));
        helper.assertTrue(oculus.charge() == FULL_CHARGE - price,
                String.format(SHOULD_SPEND, price, oculus.charge()));
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
        prism.runCombo(GooTypes.ENDER, OculusNodes.OCULUS, ability(helper, ENDER_OCULUS).behaviors());
        return prism;
    }

    /**
     * What Blink prices a trip to a spot at, through a wall as the hanging
     * stone puts it.
     *
     * @param helper the gametest helper
     * @param player the player
     * @param spot   where the trip lands
     * @return the price in mB
     */
    private static int priceTo(GameTestHelper helper, ServerPlayer player, Vec3 spot) {
        AbilityDefinition blink = ability(helper, ENDER_BLINK);
        return blink.distancePrice().priceOf(blink.cost(),
                Optional.of(new BlinkLanding(spot, player.position().distanceTo(spot), true)));
    }

    /**
     * Throws Blink from the glove, the path a press takes, with no face pinned.
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
