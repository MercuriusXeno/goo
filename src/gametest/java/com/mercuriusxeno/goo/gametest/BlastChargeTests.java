package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooChargePayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametest for Blast held to charge: a release held past its full charge
 * explodes at the aimed target the release tick at the size the charge
 * capped at, breaking stone out to that size and no farther. BlastSizeTest
 * holds the growth with the hold and the cap
 * (decisions spontaneous-abilities-cast-at-the-target-with-no-thrown-blob,
 * blast-keeps-its-explosion-gated-on-gunpowder).
 */
@RegistersGameTests
public final class BlastChargeTests {
    private static final String CHARGED_BLAST_BREAKS_OUT_TO_ITS_SIZE = "charged_blast_breaks_out_to_its_size";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CHARGED_BLAST_BREAKS_OUT_TO_ITS_SIZE,
                BlastChargeTests::chargedBlastBreaksOutToItsSize);
        GooTestFunctions.registerTest(registrar, UNCHARGED_BLAST_BREAKS_OUT_TO_TWO,
                BlastChargeTests::unchargedBlastBreaksOutToTwo);
        GooTestFunctions.registerTest(registrar, ROOTED_BLAST_EXPLODES_WHERE_THE_CHARGE_BEGAN,
                BlastChargeTests::rootedBlastExplodesWhereTheChargeBegan);
    }

    private static final String ROOTED_BLAST_EXPLODES_WHERE_THE_CHARGE_BEGAN =
            "rooted_blast_explodes_where_the_charge_began";
    private static final double HALF_BLOCK = 0.5;

    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_blast");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The stone block the release aims at, in the middle of the floor. */
    private static final BlockPos AIMED = new BlockPos(8, FLOOR_TOP, 8);
    /** The point on the aimed block's top face the crosshair rests on. */
    private static final Vec3 AIMED_POINT = new Vec3(8.5, FLOOR_TOP + 1.0, 8.5);
    /** Where the caster stands, on the floor near the bay's edge, past the blast's reach. */
    private static final BlockPos STAND = new BlockPos(8, FLOOR_TOP + 1, 1);
    /** The hold a full charge takes, from 2 blocks to 7 at a second a stage. */
    private static final int FULL_CHARGE = 100;
    /** How far out along the floor a full charge breaks the stone, inside its 7 blocks. */
    private static final int INSIDE_THE_CAP = 6;
    /** How far out along the floor the stone stands, past its 7 blocks. */
    private static final int PAST_THE_CAP = 7;
    /** How far out along the floor a release with no hold breaks the stone, inside its 2 blocks. */
    private static final int INSIDE_THE_FIRST_STAGE = 1;
    /** How far out along the floor the stone stands, past its 2 blocks. */
    private static final int PAST_THE_FIRST_STAGE = 2;
    /** Unstable enough for a capped cast. */
    private static final int AMPLE_UNSTABLE = 100_000;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_blast";
    private static final String UNCHARGED_BLAST_BREAKS_OUT_TO_TWO = "uncharged_blast_breaks_out_to_two";

    private BlastChargeTests() {
    }

    /**
     * A release held its full 100 ticks breaks the floor's top stone six
     * blocks out from the aim the release tick, and leaves it standing seven out.
     * decision blast-charges-from-two-to-seven-blocks
     *
     * @param helper the gametest helper
     */
    public static void chargedBlastBreaksOutToItsSize(GameTestHelper helper) {
        castBlast(helper, FULL_CHARGE);
        assertBrokenOutTo(helper, INSIDE_THE_CAP, PAST_THE_CAP);
    }

    /**
     * A release with no hold breaks the stone one block out from the aim,
     * its 2 block opening stage, and leaves it standing two out.
     * decision blast-charges-from-two-to-seven-blocks
     *
     * @param helper the gametest helper
     */
    public static void unchargedBlastBreaksOutToTwo(GameTestHelper helper) {
        castBlast(helper, 0);
        assertBrokenOutTo(helper, INSIDE_THE_FIRST_STAGE, PAST_THE_FIRST_STAGE);
    }

    private static void assertBrokenOutTo(GameTestHelper helper, int inside, int past) {
        helper.assertTrue(helper.getBlockState(AIMED.east(inside)).isAir(),
                "The blast should break the stone " + inside + " blocks out");
        helper.assertTrue(helper.getBlockState(AIMED.east(past)).is(Blocks.STONE),
                "The blast should leave the stone " + past + " blocks out");
        helper.succeed();
    }

    /** A stone block, relative, the caster looks at while the charge stays rooted elsewhere. */
    private static final BlockPos LOOKED_AT = new BlockPos(2, FLOOR_TOP, 4);

    /**
     * A caster looking at one block releases a charge rooted at another: the
     * blast breaks the stone around the rooted spot and leaves the looked-at
     * block standing.
     * decision blast-roots-to-the-spot-the-cast-began
     *
     * @param helper the gametest helper
     */
    public static void rootedBlastExplodesWhereTheChargeBegan(GameTestHelper helper) {
        castBlast(helper, 0, player -> player.lookAt(EntityAnchorArgument.Anchor.EYES,
                helper.absoluteVec(Vec3.atCenterOf(LOOKED_AT).add(0, HALF_BLOCK, 0))));
        helper.assertTrue(helper.getBlockState(AIMED.east(INSIDE_THE_FIRST_STAGE)).isAir(),
                "The blast should break the stone around the rooted spot");
        helper.assertTrue(helper.getBlockState(LOOKED_AT).is(Blocks.STONE),
                "The block the caster looks at should stand");
        helper.succeed();
    }

    private static void castBlast(GameTestHelper helper, int heldTicks) {
        castBlast(helper, heldTicks, player -> { });
    }

    /**
     * Releases Blast held for some ticks at the aimed stone, from a caster
     * standing clear with ample unstable.
     *
     * @param helper    the gametest helper
     * @param heldTicks the ticks the charge was held
     * @param posed     what the caster does before the release, such as looking elsewhere
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void castBlast(GameTestHelper helper, int heldTicks, Consumer<ServerPlayer> posed) {
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(blast != null, ABILITY_REQUIRED);
        layFloor(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(STAND));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(GooTypes.UNSTABLE, BLAST));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, AMPLE_UNSTABLE));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER));
        KnownRecipes.teachRequires(player, blast);
        posed.accept(player);
        GooThrowPayload aimed = new GooThrowPayload(GooTypes.id(GooTypes.UNSTABLE), -1, helper.absolutePos(AIMED),
                Direction.UP.ordinal(), false, BLAST.toString(), player.getEyePosition(),
                helper.absoluteVec(AIMED_POINT));
        GooThrowHandler.releaseCharge(player, new GooChargePayload(GooTypes.id(GooTypes.UNSTABLE), BLAST.toString(),
                heldTicks, 0L, Optional.of(aimed)));
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void layFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? Blocks.STONE : Blocks.AIR));
    }
}
