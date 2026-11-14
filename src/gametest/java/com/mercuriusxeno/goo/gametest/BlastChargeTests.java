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
    }

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
    /** A hold far past any full charge. */
    private static final int HELD_PAST_THE_CHARGE = 1000;
    /** How far out along the floor a capped charge breaks the stone, inside its size. */
    private static final int INSIDE_THE_CAP = 4;
    /** How far out along the floor the stone stands, past its size. */
    private static final int PAST_THE_CAP = 7;
    /** Unstable enough for a capped cast. */
    private static final int AMPLE_UNSTABLE = 100_000;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_blast";

    private BlastChargeTests() {
    }

    /**
     * A release held past the full charge breaks the floor's top stone four
     * blocks out from the aim the release tick, and leaves it standing seven out.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void chargedBlastBreaksOutToItsSize(GameTestHelper helper) {
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
        GooThrowPayload aimed = new GooThrowPayload(GooTypes.id(GooTypes.UNSTABLE), -1, helper.absolutePos(AIMED),
                Direction.UP.ordinal(), false, BLAST.toString(), player.getEyePosition(),
                helper.absoluteVec(AIMED_POINT));
        GooThrowHandler.releaseCharge(player, new GooChargePayload(GooTypes.id(GooTypes.UNSTABLE), BLAST.toString(),
                HELD_PAST_THE_CHARGE, 0L, Optional.of(aimed)));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(helper.getBlockState(AIMED.east(INSIDE_THE_CAP)).isAir(),
                "A capped charge should break the stone " + INSIDE_THE_CAP + " blocks out");
        helper.assertTrue(helper.getBlockState(AIMED.east(PAST_THE_CAP)).is(Blocks.STONE),
                "A capped charge should leave the stone " + PAST_THE_CAP + " blocks out");
        helper.succeed();
    }

    private static void layFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? Blocks.STONE : Blocks.AIR));
    }
}
