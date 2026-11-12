package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for rock bore: streaming bore east into stone drills a one-block
 * tunnel along the eye line to the stream's reach, each block over several
 * ticks and the nearest first, dropping cobblestone, and leaves
 * the stone around the tunnel and past the reach standing; a cow penned in
 * the tunnel takes Bore's hit as the player's, with no knockback
 * (decisions bore-drills-the-eye-line-with-steady-progress, bore-breaks-a-15-degree-cone,
 * bore-vortex-with-a-worldspace-shake).
 */
@RegistersGameTests
public final class BoreStreamTests {
    private static final String BORE_CUTS_A_TUNNEL = "bore_cuts_a_tunnel";
    private static final String BORE_STRIKES_A_MOB = "bore_strikes_a_mob_in_the_tunnel";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BORE_CUTS_A_TUNNEL, BoreStreamTests::boreCutsATunnel);
        GooTestFunctions.registerTest(registrar, BORE_STRIKES_A_MOB, BoreStreamTests::boreStrikesAMobInTheTunnel);
    }


    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** The row the eye line runs along: a standing player's eye sits in the block above its feet. */
    private static final BlockPos EYE_ROW = STAND_POS.above();
    /** rock_bore.json's range: the tunnel runs from the eye's block to four blocks east. */
    private static final int REACH = 4;
    private static final int HELD_GOO = 4;
    /** A hold long past the four tunnel blocks at rock_bore.json's pace. */
    private static final int HOLD_TICKS = 30;
    private static final String SHOULD_BREAK_AFTER_THE_NEARER =
            "The block %d east should break after the block nearer the eye, broke on tick %d, the nearer on %d";
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier ROCK_BORE = Identifier.parse("goo:rock_bore");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_bore";
    private static final BlockPos TUNNEL_COW = EYE_ROW.east(2).below();
    private static final String SHOULD_HIT_AS_THE_PLAYER = "The first tick should hit the cow as the player, last hit by %s";

    private BoreStreamTests() {
    }

    /**
     * A mock player facing east streams bore into a block of stone: through the
     * hold each block on the line breaks on a later tick than the block nearer
     * the eye, and after it a one-block tunnel runs along the eye line to the
     * reach and cobblestone dropped (decision bore-drills-the-eye-line-with-steady-progress).
     *
     * @param helper the gametest helper
     */
    public static void boreCutsATunnel(GameTestHelper helper) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        for (int east = 1; east <= REACH + 1; east++) {
            for (BlockPos pos : slice(EYE_ROW.east(east))) {
                helper.setBlock(pos, Blocks.STONE);
            }
        }
        ServerPlayer player = borer(helper);
        KnownRecipes.teachRequires(player, bore);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
        int[] brokeOn = new int[REACH + 1];
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        for (int held = 2; held <= HOLD_TICKS; held++) {
            int at = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                for (int east = 1; east <= REACH; east++) {
                    if (brokeOn[east] == 0 && helper.getBlockState(EYE_ROW.east(east)).isAir()) {
                        brokeOn[east] = at;
                    }
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (int east = 2; east <= REACH; east++) {
                helper.assertTrue(brokeOn[east] > brokeOn[east - 1], String.format(SHOULD_BREAK_AFTER_THE_NEARER,
                        east, brokeOn[east], brokeOn[east - 1]));
            }
            for (int east = 1; east <= REACH; east++) {
                helper.assertBlockPresent(Blocks.AIR, EYE_ROW.east(east));
            }
            helper.assertItemEntityPresent(Items.COBBLESTONE, EYE_ROW.east(2), ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    /**
     * A mock player streams bore east at a cow penned in glass inside the
     * tunnel: the first tick hits it as the player's attack; DamageStepTest holds
     * the amount and the motion it keeps.
     *
     * @param helper the gametest helper
     */
    public static void boreStrikesAMobInTheTunnel(GameTestHelper helper) {
        for (int east = 1; east <= 3; east++) {
            for (int up = 0; up <= 1; up++) {
                helper.setBlock(TUNNEL_COW.offset(east - 2, up, -1), Blocks.GLASS);
                helper.setBlock(TUNNEL_COW.offset(east - 2, up, 1), Blocks.GLASS);
            }
        }
        helper.setBlock(TUNNEL_COW.east(2), Blocks.GLASS);
        helper.setBlock(TUNNEL_COW.east(2).above(), Blocks.GLASS);
        // a floor keeps the cow's head in the one-block tunnel on the eye row
        helper.setBlock(TUNNEL_COW.below(), Blocks.GLASS);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, TUNNEL_COW);
        ServerPlayer player = boringPlayer(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
        helper.runAfterDelay(1, () -> {
            GooStreamHandler.streamTick(player, tick);
            helper.getLevel().getServer().getPlayerList().remove(player);
            boolean asPlayer = cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == player;
            helper.assertTrue(asPlayer, String.format(SHOULD_HIT_AS_THE_PLAYER, cow.getLastDamageSource()));
            helper.succeed();
        });
    }

    /**
     * A borer holding the goo and knowing bore's recipes.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer boringPlayer(GameTestHelper helper) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        ServerPlayer player = borer(helper);
        KnownRecipes.teachRequires(player, bore);
        return player;
    }

    /**
     * The 3x3 slice square to x about a block: the tunnel's middle and the stone around it.
     *
     * @param middle the slice's middle, relative
     * @return the nine blocks
     */
    private static List<BlockPos> slice(BlockPos middle) {
        List<BlockPos> slice = new ArrayList<>();
        for (int up = -1; up <= 1; up++) {
            for (int across = -1; across <= 1; across++) {
                slice.add(middle.offset(0, up, across));
            }
        }
        return slice;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer borer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
