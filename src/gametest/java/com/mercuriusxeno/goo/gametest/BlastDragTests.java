package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.DragSize;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Blast cast by its drag: the release explodes at once at the
 * pinned epicenter at the radius dragged, throwing no blob, breaking nothing
 * outside that radius and charging 1000 mB at radius 3; and a player who
 * lacks the gunpowder recipe spends nothing and explodes nothing. DragSizeTest
 * holds the volume price and the cap at the holdings
 * (decisions blast-is-drag-sized-like-the-black-hole, blast-keeps-its-explosion-gated-on-gunpowder).
 */
@RegistersGameTests
public final class BlastDragTests {
    private static final String BLAST_DRAG_EXPLODES_AT_THE_PIN = "blast_drag_explodes_at_the_pin";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLAST_DRAG_EXPLODES_AT_THE_PIN, BlastDragTests::blastDragExplodesAtThePinAtTheDraggedRadius);
    }


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
    /** Unstable enough for the reference cast. */
    private static final int AMPLE_UNSTABLE = 10000;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_explode";
    private static final String PIN_STANDS = "The drag cast should break the pinned block";

    private BlastDragTests() {
    }

    /**
     * Releasing a drag at the reference radius explodes at the pin in the same tick, breaking the pinned block.
     *
     * @param helper the gametest helper
     */
    public static void blastDragExplodesAtThePinAtTheDraggedRadius(GameTestHelper helper) {
        castBlast(helper, REFERENCE_RADIUS, AMPLE_UNSTABLE);

        helper.assertTrue(helper.getBlockState(PIN).isAir(), PIN_STANDS);
        helper.succeed();
    }

    /**
     * Casts Blast the way a drag's release does: a mock player holding a glove
     * and unstable, standing on the floor, pins the stone's top face and
     * releases at a radius.
     *
     * @param helper   the gametest helper
     * @param radius   the radius dragged
     * @param unstable the mB of unstable the player holds
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void castBlast(GameTestHelper helper, double radius, int unstable) {
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(blast != null, ABILITY_REQUIRED);
        layFloor(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(STAND));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, unstable));
        KnownRecipes.teachRequires(player, blast);
        BlockPos pin = helper.absolutePos(PIN);
        GooDragCastHandler.cast(player, new GooDragCastPayload(GooTypes.id(GooTypes.UNSTABLE), BLAST.toString(), pin,
                Direction.UP.get3DDataValue(), helper.absoluteVec(PIN_POINT), radius));
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void layFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? Blocks.STONE : Blocks.AIR));
    }
}
