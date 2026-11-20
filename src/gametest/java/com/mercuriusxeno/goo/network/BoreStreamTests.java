package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for rock bore: streaming bore east into stone drills a one-block
 * tunnel along the eye line to the stream's reach, one block deep at a time,
 * each block over several ticks, dropping cobblestone; a cow penned in the
 * tunnel takes Bore's hit as the player's, with no knockback
 * (decisions bore-breaks-the-block-at-the-point-and-its-surroundings,
 * bore-drills-the-eye-line-with-steady-progress, bore-vortex-with-a-worldspace-shake).
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
        GooTestFunctions.registerTest(registrar, BORE_CUTS_LUNAR_STONE, BoreStreamTests::boreCutsLunarStone);
        GooTestFunctions.registerTest(registrar, BORE_CUTS_LUNAR_CRATER_MATTER, BoreStreamTests::boreCutsLunarCraterMatter);
        GooTestFunctions.registerTest(registrar, BORE_CUTS_LUNAR_BASALT_AND_ICE, BoreStreamTests::boreCutsLunarBasaltAndIce);
        GooTestFunctions.registerTest(registrar, BORE_STRIKES_A_MOB, BoreStreamTests::boreStrikesAMobInTheTunnel);
        GooTestFunctions.registerTest(registrar, BORE_BREAKS_FOUR, BoreStreamTests::boreBreaksTheFourBlocksWhereFourMeet);
        GooTestFunctions.registerTest(registrar, BORE_BREAKS_FOUR_AT_A_SLANT,
                BoreStreamTests::boreBreaksTheFourFloorBlocksWhereFourMeetAtASlant);
    }


    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** The row the eye line runs along: a standing player's eye sits in the block above its feet. */
    private static final BlockPos EYE_ROW = STAND_POS.above();
    private static final int HELD_GOO = 4;
    /** The longest hold the tunnel test streams while it polls, inside its timeout. */
    private static final int MOST_HOLD_TICKS = 90;
    private static final String SHOULD_BREAK_AFTER_THE_NEARER =
            "The block %d east broke on tick %d, not after the nearer block's break on tick %d";
    private static final String SHOULD_STAND_BEHIND = "The block %d east had broken by the tick the block %d east broke";
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier ROCK_BORE = Identifier.parse("goo:rock_bore");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_bore";
    private static final BlockPos TUNNEL_COW = EYE_ROW.east(2).below();
    private static final String SHOULD_HIT_AS_THE_PLAYER = "The first tick should hit the cow as the player, last hit by %s";
    /** A hold long enough to break a block the bore reaches. */
    private static final int REACH_HOLD_TICKS = 20;
    /** How far the eye line passes from the corner where four blocks meet, on both axes. */
    private static final double NEAR_THE_CORNER = 0.05;
    private static final String SHOULD_BREAK_FOUR_TOGETHER =
            "The four blocks where four meet should break on one tick, broke on ticks %s (0 stood)";
    private static final String BORE_BREAKS_FOUR = "bore_breaks_the_four_blocks_where_four_meet";
    private static final String BORE_BREAKS_FOUR_AT_A_SLANT = "bore_breaks_the_four_floor_blocks_at_a_slant";
    /** How far the look tips down at the floor, in degrees. */
    private static final float LOOKING_DOWN_AT_A_SLANT = 45f;
    /** How far the eye stands back from the point it aims at, along the look. */
    private static final double AIM_DISTANCE = 1.5;

    private static final String BORE_CUTS_LUNAR_STONE = "bore_cuts_lunar_stone";
    private static final String BORE_CUTS_LUNAR_CRATER_MATTER = "bore_cuts_lunar_crater_matter";
    private static final String BORE_CUTS_LUNAR_BASALT_AND_ICE = "bore_cuts_lunar_basalt_and_ice";
    private static final String SHOULD_CUT_LUNAR = "Bore should cut lunar's %s, %d east, which still stands";

    private BoreStreamTests() {
    }

    /**
     * A mock player facing east streams bore through anorthosite, regolith and iron core laid along the eye line,
     * and cuts all three, as it cuts the overworld's stone.
     * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
     *
     * @param helper the gametest helper
     */
    public static void boreCutsLunarStone(GameTestHelper helper) {
        boreCutsAlongTheEyeLine(helper, List.of(GooBlocks.ANORTHOSITE.get(), GooBlocks.REGOLITH.get(),
                GooBlocks.IRON_CORE.get()));
    }

    /**
     * A mock player facing east streams bore through meteoric iron, breccia and impact glass laid along the eye line,
     * and cuts all three: lunar's crater matter yields to bore as its stone does (operator ruling 2026-11-15: bore
     * must work on the lunar blocks).
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     *
     * @param helper the gametest helper
     */
    public static void boreCutsLunarCraterMatter(GameTestHelper helper) {
        boreCutsAlongTheEyeLine(helper, List.of(GooBlocks.METEORIC_IRON.get(), GooBlocks.BRECCIA.get(),
                GooBlocks.IMPACT_GLASS.get()));
    }

    /**
     * A mock player facing east streams bore through basalt, lunar's basin rock, and crater ice laid along the eye
     * line, and cuts both.
     * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     *
     * @param helper the gametest helper
     */
    public static void boreCutsLunarBasaltAndIce(GameTestHelper helper) {
        boreCutsAlongTheEyeLine(helper, List.of(Blocks.BASALT, GooBlocks.CRATER_ICE.get()));
    }

    private static void boreCutsAlongTheEyeLine(GameTestHelper helper, List<Block> lunar) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        ServerPlayer player = borer(helper);
        KnownRecipes.teachRequires(player, bore);
        for (int east = 1; east <= lunar.size(); east++) {
            helper.setBlock(EYE_ROW.east(east), lunar.get(east - 1));
        }
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= MOST_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.succeedWhen(() -> {
            for (int east = 1; east <= lunar.size(); east++) {
                helper.assertTrue(helper.getBlockState(EYE_ROW.east(east)).isAir(),
                        String.format(SHOULD_CUT_LUNAR, lunar.get(east - 1), east));
            }
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player facing east streams bore into a block of stone, aimed at
     * the middle of a block's face: the block behind each block on the line
     * stands while the nearer one breaks and breaks after it, so the tunnel
     * goes one block deep at a time, and
     * the hold runs until a one-block tunnel runs along the eye line to the
     * whole blocks inside the stream's reach, read live from the ability, and
     * cobblestone has dropped
     * (decisions bore-breaks-the-block-at-the-point-and-its-surroundings,
     * bore-drills-the-eye-line-with-steady-progress).
     *
     * @param helper the gametest helper
     */
    public static void boreCutsATunnel(GameTestHelper helper) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        ServerPlayer player = borer(helper);
        KnownRecipes.teachRequires(player, bore);
        int reach = (int) Math.floor(bore.delivery().reachingTo(player.blockInteractionRange()).range());
        for (int east = 1; east <= reach + 1; east++) {
            for (BlockPos pos : slice(EYE_ROW.east(east))) {
                helper.setBlock(pos, Blocks.STONE);
            }
        }
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
        int[] brokeOn = new int[reach + 1];
        boolean[] behindStood = new boolean[reach + 1];
        for (int held = 1; held <= MOST_HOLD_TICKS; held++) {
            int at = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                for (int east = 1; east <= reach; east++) {
                    if (brokeOn[east] == 0 && helper.getBlockState(EYE_ROW.east(east)).isAir()) {
                        brokeOn[east] = at;
                        behindStood[east] = helper.getBlockState(EYE_ROW.east(east + 1)).is(Blocks.STONE);
                    }
                }
            });
        }
        helper.succeedWhen(() -> {
            for (int east = 1; east <= reach; east++) {
                helper.assertBlockPresent(Blocks.AIR, EYE_ROW.east(east));
                helper.assertTrue(behindStood[east], String.format(SHOULD_STAND_BEHIND, east + 1, east));
            }
            for (int east = 2; east <= reach; east++) {
                helper.assertTrue(brokeOn[east] > brokeOn[east - 1],
                        String.format(SHOULD_BREAK_AFTER_THE_NEARER, east, brokeOn[east], brokeOn[east - 1]));
            }
            helper.assertItemEntityPresent(Items.COBBLESTONE, EYE_ROW.east(2), ITEM_SEARCH_RADIUS);
            helper.getLevel().getServer().getPlayerList().remove(player);
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
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, TUNNEL_COW);
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
     * A mock player facing east, the eye line 0.05 from the corner where four
     * stone blocks meet on both axes, streams bore: the four blocks break
     * together, on the same tick (decision bore-breaks-what-the-drill-cross-section-intersects).
     *
     * @param helper the gametest helper
     */
    public static void boreBreaksTheFourBlocksWhereFourMeet(GameTestHelper helper) {
        BlockPos wall = EYE_ROW.east(2);
        for (int east = 2; east <= 3; east++) {
            for (BlockPos pos : slice(EYE_ROW.east(east))) {
                helper.setBlock(pos, Blocks.STONE);
                helper.setBlock(pos.above(), Blocks.STONE);
            }
        }
        Vec3 corner = Vec3.atLowerCornerOf(helper.absolutePos(wall.above()));
        holdAtACorner(helper, new Vec3(corner.x, corner.y - NEAR_THE_CORNER, corner.z + NEAR_THE_CORNER), 0f,
                List.of(wall, wall.above(), wall.north(), wall.above().north()));
    }

    /**
     * A mock player looking 45 degrees down at a stone floor, the eye line
     * 0.05 from the corner where four floor blocks meet on both axes, streams
     * bore: the four blocks break together, on the same tick, not the row in
     * front of them (decision bore-breaks-what-the-drill-cross-section-intersects).
     *
     * @param helper the gametest helper
     */
    public static void boreBreaksTheFourFloorBlocksWhereFourMeetAtASlant(GameTestHelper helper) {
        BlockPos floor = STAND_POS.east(3);
        for (int east = 1; east <= 5; east++) {
            for (int across = -2; across <= 1; across++) {
                helper.setBlock(STAND_POS.east(east).offset(0, 0, across), Blocks.STONE);
            }
        }
        Vec3 corner = Vec3.atLowerCornerOf(helper.absolutePos(floor.above()));
        holdAtACorner(helper, new Vec3(corner.x + NEAR_THE_CORNER, corner.y, corner.z + NEAR_THE_CORNER),
                LOOKING_DOWN_AT_A_SLANT, List.of(floor, floor.west(), floor.north(), floor.west().north()));
    }

    /**
     * Holds bore from an eye looking east at a point near where four blocks
     * meet and succeeds once all four break on one tick.
     *
     * @param helper the gametest helper
     * @param aimed  the point the crosshair meets, absolute
     * @param pitch  how far the look tips down, in degrees
     * @param four   the four blocks where four meet, relative
     */
    private static void holdAtACorner(GameTestHelper helper, Vec3 aimed, float pitch, List<BlockPos> four) {
        ServerPlayer player = boringPlayer(helper);
        player.setXRot(pitch);
        Vec3 eye = aimed.subtract(player.getLookAngle().scale(AIM_DISTANCE));
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        holdUntilTogether(helper, player, four);
    }

    /**
     * Holds bore and succeeds once every block named breaks, all on one tick.
     *
     * @param helper the gametest helper
     * @param player the borer, placed and turned
     * @param four   the blocks that break together, relative
     */
    private static void holdUntilTogether(GameTestHelper helper, ServerPlayer player, List<BlockPos> four) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
        int[] brokeOn = new int[four.size()];
        for (int held = 1; held <= REACH_HOLD_TICKS; held++) {
            int at = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                for (int block = 0; block < four.size(); block++) {
                    if (brokeOn[block] == 0 && helper.getBlockState(four.get(block)).isAir()) {
                        brokeOn[block] = at;
                    }
                }
            });
        }
        helper.runAfterDelay(REACH_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Arrays.stream(brokeOn).allMatch(on -> on > 0 && on == brokeOn[0]),
                    String.format(SHOULD_BREAK_FOUR_TOGETHER, Arrays.toString(brokeOn)));
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
