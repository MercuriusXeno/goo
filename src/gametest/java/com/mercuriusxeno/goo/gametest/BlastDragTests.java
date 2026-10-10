package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.DragSize;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooDragCastHandler;
import com.mercuriusxeno.goo.network.GooDragCastPayload;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Blast cast by its drag: the release explodes at once at the
 * pinned epicenter at the radius dragged, throwing no blob, breaking nothing
 * outside that radius and charging the volume price, 1000 mB at radius 3 and
 * 8000 at radius 6; a drag past what the player's unstable pays for opens at
 * the radius it does pay for; and a player who lacks the gunpowder recipe
 * spends nothing and explodes nothing
 * (decisions blast-is-drag-sized-like-the-black-hole, blast-keeps-its-explosion-gated-on-gunpowder).
 */
public final class BlastDragTests {

    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_explode");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The stone block the press pins, in the middle of the floor. */
    private static final BlockPos PIN = new BlockPos(8, FLOOR_TOP, 8);
    /** The point on the pinned block's top face the press pins. */
    private static final Vec3 PIN_POINT = new Vec3(8.5, FLOOR_TOP + 1.0, 8.5);
    /** Where the caster stands, on the floor near the bay's edge, within reach of the pin. */
    private static final BlockPos STAND = new BlockPos(8, FLOOR_TOP + 1, 1);
    /** The radius 1000 mB buys. */
    private static final double REFERENCE_RADIUS = DragSize.REFERENCE_RADIUS;
    /** A drag to twice the reference radius, eight times the price. */
    private static final double WIDE_RADIUS = 6;
    /** Unstable enough for the widest cast. */
    private static final int AMPLE_UNSTABLE = 10000;
    /** Unstable for the reference radius alone. */
    private static final int SCANT_UNSTABLE = 1000;
    private static final int REFERENCE_COST = 1000;
    private static final int WIDE_COST = 8000;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_explode";
    private static final String WRONG_CHARGE = "The drag cast at radius %.0f should charge %d mB, charged %d";
    private static final String BLOB_THROWN = "A drag cast should throw no blob, %d flying";
    private static final String PIN_STANDS = "The drag cast should break the pinned block";
    private static final String BEYOND_RADIUS = "The drag cast broke stone at %s, %.2f blocks from the pin, past its radius %.2f";
    private static final String UNGATED = "A player without the gunpowder recipe should spend nothing, spent %d";
    private static final String UNGATED_BLAST = "A player without the gunpowder recipe should explode nothing";

    private BlastDragTests() {
    }

    /**
     * Releasing a drag at the reference radius charges 1000 mB, throws no
     * blob, breaks the pinned block and nothing beyond three blocks of the pin.
     *
     * @param helper the gametest helper
     */
    public static void blastDragExplodesAtThePinAtTheDraggedRadius(GameTestHelper helper) {
        int spent = castBlast(helper, REFERENCE_RADIUS, AMPLE_UNSTABLE, true);

        helper.assertTrue(spent == REFERENCE_COST, String.format(WRONG_CHARGE, REFERENCE_RADIUS, REFERENCE_COST, spent));
        int flying = helper.getLevel().getEntitiesOfClass(RollingGoo.class, helper.getBounds()).size();
        helper.assertTrue(flying == 0, String.format(BLOB_THROWN, flying));
        assertCraterWithin(helper, REFERENCE_RADIUS);
        helper.succeed();
    }

    /**
     * A drag to twice the reference radius charges eight times the price and
     * breaks nothing beyond six blocks of the pin.
     *
     * @param helper the gametest helper
     */
    public static void blastDragPricesByVolume(GameTestHelper helper) {
        int spent = castBlast(helper, WIDE_RADIUS, AMPLE_UNSTABLE, true);

        helper.assertTrue(spent == WIDE_COST, String.format(WRONG_CHARGE, WIDE_RADIUS, WIDE_COST, spent));
        assertCraterWithin(helper, WIDE_RADIUS);
        helper.succeed();
    }

    /**
     * A drag past what the player's unstable pays for opens at the radius it
     * does pay for: 1000 mB held and radius 6 dragged opens at radius 3 and
     * charges the 1000.
     *
     * @param helper the gametest helper
     */
    public static void blastDragCapsAtTheHoldings(GameTestHelper helper) {
        int spent = castBlast(helper, WIDE_RADIUS, SCANT_UNSTABLE, true);

        helper.assertTrue(spent == REFERENCE_COST, String.format(WRONG_CHARGE, WIDE_RADIUS, REFERENCE_COST, spent));
        assertCraterWithin(helper, REFERENCE_RADIUS);
        helper.succeed();
    }

    /**
     * A player who lacks the gunpowder recipe casting Blast by drag spends
     * nothing and explodes nothing.
     *
     * @param helper the gametest helper
     */
    public static void blastDragNeedsGunpowder(GameTestHelper helper) {
        int spent = castBlast(helper, REFERENCE_RADIUS, AMPLE_UNSTABLE, false);

        helper.assertTrue(spent == 0, String.format(UNGATED, spent));
        helper.assertTrue(helper.getBlockState(PIN).is(Blocks.STONE), UNGATED_BLAST);
        helper.succeed();
    }

    /**
     * Casts Blast the way a drag's release does: a mock player holding a glove
     * and unstable, standing on the floor, pins the stone's top face and
     * releases at a radius.
     *
     * @param helper         the gametest helper
     * @param radius         the radius dragged
     * @param unstable       the mB of unstable the player holds
     * @param knowsGunpowder whether the player knows Blast's gate recipe
     * @return the mB of unstable the cast spent
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static int castBlast(GameTestHelper helper, double radius, int unstable, boolean knowsGunpowder) {
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(blast != null, ABILITY_REQUIRED);
        layFloor(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(STAND));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, unstable));
        if (knowsGunpowder) {
            KnownRecipes.teachRequires(player, blast);
        }
        BlockPos pin = helper.absolutePos(PIN);
        GooDragCastHandler.cast(player, new GooDragCastPayload(GooTypes.id(GooTypes.UNSTABLE), BLAST.toString(), pin,
                Direction.UP.get3DDataValue(), helper.absoluteVec(PIN_POINT), radius));
        int spent = unstable - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.UNSTABLE, 0);
        helper.getLevel().getServer().getPlayerList().remove(player);
        return spent;
    }

    /**
     * Asserts the pinned block broke and every floor cell broken lies within
     * the radius of the pin.
     *
     * @param helper the gametest helper
     * @param radius the radius the cast opened at
     */
    private static void assertCraterWithin(GameTestHelper helper, double radius) {
        helper.assertTrue(helper.getBlockState(PIN).isAir(), PIN_STANDS);
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, FLOOR_TOP, FLOOR_SPAN - 1).forEach(cell -> {
            double distance = Vec3.atCenterOf(cell).distanceTo(PIN_POINT);
            helper.assertTrue(!helper.getBlockState(cell).isAir() || distance <= radius,
                    String.format(BEYOND_RADIUS, cell.toShortString(), distance, radius));
        });
    }

    private static void layFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? Blocks.STONE : Blocks.AIR));
    }
}
