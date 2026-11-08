package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Unmake's drink: a hold down a cobblestone column at mid
 * range drinks the column the narrow cone covers, deep but not wide, all
 * together over the unstable crucible's own time for a cobblestone after the
 * square's flight, burning the crucible's fuel plus its root for each block,
 * the wall about it standing; the blocks' goo converges into one goo item
 * near them, never into the player's inventory; a block that started goes
 * until it is done though the cursor leaves it or the use is let go, standing
 * as itself until the square lands, while one outside the cone stands; and a
 * mob at the cursor is left alone.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@RegistersGameTests
public final class UnmakeTests {
    private static final String UNMAKE_DRINKS_A_COLUMN_NOT_THE_WALL = "unmake_drinks_a_column_not_the_wall";
    private static final String UNMAKE_FINISHES_WHAT_IT_STARTS = "unmake_finishes_what_it_starts";
    private static final String UNMAKE_FINISHES_AFTER_RELEASE = "unmake_finishes_after_release";
    private static final String UNMAKE_LEAVES_MOBS_ALONE = "unmake_leaves_mobs_alone";
    private static final String UNMAKE_CONVERGES_INTO_ONE_GOO_ITEM = "unmake_converges_into_one_goo_item";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, UNMAKE_DRINKS_A_COLUMN_NOT_THE_WALL, UnmakeTests::unmakeDrinksAColumnNotTheWall);
        GooTestFunctions.registerTest(registrar, UNMAKE_FINISHES_WHAT_IT_STARTS, UnmakeTests::unmakeFinishesWhatItStarts);
        GooTestFunctions.registerTest(registrar, UNMAKE_FINISHES_AFTER_RELEASE, UnmakeTests::unmakeFinishesAfterRelease);
        GooTestFunctions.registerTest(registrar, UNMAKE_LEAVES_MOBS_ALONE, UnmakeTests::unmakeLeavesMobsAlone);
        GooTestFunctions.registerTest(registrar, UNMAKE_CONVERGES_INTO_ONE_GOO_ITEM,
                UnmakeTests::unmakeConvergesTwoBlocksIntoOneGooItem);
    }


    private static final Identifier UNMAKE = Identifier.parse("goo:unstable_unmake");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Six blocks east at eye height, the middle of the wall, at the cone's mid range. */
    private static final BlockPos AIMED_POS = new BlockPos(7, 2, 3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final double FACE_MIDDLE = 0.5;
    /** The blocks the column along the aim holds. */
    private static final int COLUMN_BLOCKS = 3;
    /** Ticks of slack past a block's own time a hold goes on for. */
    private static final int SLACK_TICKS = 2;
    /** How far above the aimed block a block stands outside the cone. */
    private static final int OUT_OF_THE_CONE = 3;
    private static final double DROP_REACH = 4;
    /** How far across from the convergence point a goo item falling straight down from it stands. */
    private static final double AT_THE_POINT = 0.5;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_unmake";
    private static final String STANDS = "The wall's block at %s should be drunk within %d ticks";
    private static final String UNPAID = "The hold should burn exactly the unstable crucible's fuel plus its root per block";
    private static final String SQUARE_EARLY = "A picked block should stand as itself while the square flies";
    private static final String SQUARE_LATE = "A picked block should be the melting stand-in once the square lands";
    private static final String WALL_GONE = "The wall's block at %s, off the aim, should stand";
    private static final String NOT_ONE_ITEM = "The blocks' goo should form one goo item per type, formed %d holding %s";
    private static final String WRONG_ITEM = "The goo item should hold both blocks' goo %s, held %s";
    private static final String OFF_THE_POINT = "The goo item at %s should form at the convergence point %s";
    private static final String INTO_THE_HAND = "The player's inventory should gain none of the goo, held %s";
    private static final String UNFINISHED = "A block that started streaming should go though the cursor leaves it";
    private static final String OUTSIDE_GONE = "A block outside the cone should stand";
    private static final String RELEASED = "A block that started streaming should go though the use is let go";
    private static final String MOB_GONE = "A mob at the cursor should be left alone";

    private UnmakeTests() {
    }

    /**
     * A mock player holding exactly three blocks' fuel holds Unmake down a
     * cobblestone column three deep along the aim, through the middle of a
     * cobblestone wall: the column is drunk front to back, the wall about it
     * stands, and the fuel is spent.
     *
     * @param helper the gametest helper
     */
    public static void unmakeDrinksAColumnNotTheWall(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        List<BlockPos> column = column();
        List<BlockPos> wall = wall();
        column.forEach(pos -> helper.setBlock(pos, Blocks.COBBLESTONE));
        wall.forEach(pos -> helper.setBlock(pos, Blocks.COBBLESTONE));
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        int fuel = SiphonRule.fuelFor(cobblestone.meltFloorTotal(), GooConfig.UNSTABLE_MELT_EXPONENT.get(),
                GooConfig.UNSTABLE_TICKS_PER_MB.get());
        ServerPlayer player = channeler(helper, COLUMN_BLOCKS * fuel);
        KnownRecipes.teachRequires(player, unmake);
        int drinkTicks = drinkTicks(helper);
        GooStreamPayload tick = aimedAt(player, westFace(helper, AIMED_POS));
        for (int held = 1; held <= drinkTicks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(drinkTicks + 1L, () -> {
            boolean fuelLeft = GooSourceScanner.hasEnough(player, GooTypes.UNSTABLE, 1);
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (BlockPos pos : column) {
                helper.assertTrue(helper.getBlockState(pos).isAir(), String.format(STANDS, pos, drinkTicks));
            }
            for (BlockPos pos : wall) {
                helper.assertTrue(helper.getBlockState(pos).is(Blocks.COBBLESTONE), String.format(WALL_GONE, pos));
            }
            helper.assertFalse(fuelLeft, UNPAID);
            helper.succeed();
        });
    }

    /**
     * A mock player holding exactly two blocks' fuel unmakes two cobblestones
     * along the aim: their chunks converge on the nearer block, the first the
     * drink picks, and one goo item per type forms there holding both blocks'
     * goo, falling straight down from it, while the player's inventory gains
     * none of it.
     * unmake-converges-near-the-unmade-blocks-into-a-goo-item
     *
     * @param helper the gametest helper
     */
    public static void unmakeConvergesTwoBlocksIntoOneGooItem(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        List<BlockPos> pair = List.of(AIMED_POS.west(), AIMED_POS);
        pair.forEach(pos -> helper.setBlock(pos, Blocks.COBBLESTONE));
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        int fuel = SiphonRule.fuelFor(cobblestone.meltFloorTotal(), GooConfig.UNSTABLE_MELT_EXPONENT.get(),
                GooConfig.UNSTABLE_TICKS_PER_MB.get());
        ServerPlayer player = channeler(helper, pair.size() * fuel);
        KnownRecipes.teachRequires(player, unmake);
        int drinkTicks = drinkTicks(helper);
        Vec3 point = Vec3.atCenterOf(helper.absolutePos(pair.getFirst()));
        int travel = SiphonRule.travelTicks(Vec3.atCenterOf(helper.absolutePos(AIMED_POS)).distanceTo(point));
        GooStreamPayload tick = aimedAt(player, westFace(helper, AIMED_POS));
        for (int held = 1; held <= drinkTicks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(drinkTicks + travel + 2L, () -> {
            List<ItemEntity> items = gooItemsNear(helper, AIMED_POS, DROP_REACH);
            Map<ResourceKey<GooTypeDefinition>, Integer> held = heldGoo(player.getInventory());
            boolean fuelLeft = GooSourceScanner.hasEnough(player, GooTypes.UNSTABLE, 1);
            helper.getLevel().getServer().getPlayerList().remove(player);
            Map<ResourceKey<GooTypeDefinition>, Integer> expected = new HashMap<>();
            cobblestone.meltFloorAll().forEach((type, amount) -> expected.put(type, amount * pair.size()));
            Map<ResourceKey<GooTypeDefinition>, Integer> formed = new HashMap<>();
            items.forEach(item -> formed.merge(GooStacks.keyOf(item.getItem()), GooStacks.volumeOf(item.getItem()),
                    Integer::sum));
            helper.assertTrue(items.size() == expected.size(), String.format(NOT_ONE_ITEM, items.size(), formed));
            helper.assertTrue(new GooContents(expected).getAll().equals(formed),
                    String.format(WRONG_ITEM, expected, formed));
            items.forEach(item -> helper.assertTrue(horizontalDistance(item.position(), point) <= AT_THE_POINT,
                    String.format(OFF_THE_POINT, item.position(), point)));
            helper.assertTrue(held.isEmpty(), String.format(INTO_THE_HAND, held));
            helper.assertFalse(fuelLeft, UNPAID);
            helper.succeed();
        });
    }

    private static double horizontalDistance(Vec3 from, Vec3 to) {
        return Math.hypot(from.x - to.x, from.z - to.z);
    }

    private static List<ItemEntity> gooItemsNear(GameTestHelper helper, BlockPos around, double reach) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(around))
                .inflate(reach), item -> GooStacks.keyOf(item.getItem()) != null);
    }

    /**
     * A mock player holds Unmake at a cobblestone for one tick, then looks
     * away into the air: the block started and is drunk anyway, and one
     * standing well above it, outside the cone, stands.
     *
     * @param helper the gametest helper
     */
    public static void unmakeFinishesWhatItStarts(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        BlockPos outside = AIMED_POS.above(OUT_OF_THE_CONE);
        helper.setBlock(AIMED_POS, Blocks.COBBLESTONE);
        helper.setBlock(outside, Blocks.COBBLESTONE);
        ServerPlayer player = channeler(helper, GooStacks.THOUSAND);
        KnownRecipes.teachRequires(player, unmake);
        int drinkTicks = drinkTicks(helper);
        GooStreamPayload aimed = aimedAt(player, westFace(helper, AIMED_POS));
        GooStreamPayload away = aimedAt(player, player.getEyePosition().add(0, OUT_OF_THE_CONE, 0));
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, aimed));
        for (int held = 2; held <= drinkTicks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, away));
        }
        helper.runAfterDelay(drinkTicks + 1L, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(helper.getBlockState(AIMED_POS).isAir(), UNFINISHED);
            helper.assertTrue(helper.getBlockState(outside).is(Blocks.COBBLESTONE), OUTSIDE_GONE);
            helper.succeed();
        });
    }

    /**
     * A mock player holds Unmake at a cobblestone for one tick and lets go,
     * no tick following: the block stands as itself while the square flies, is
     * the melting stand-in once the square lands, and is drunk anyway.
     *
     * @param helper the gametest helper
     */
    public static void unmakeFinishesAfterRelease(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        helper.setBlock(AIMED_POS, Blocks.COBBLESTONE);
        ServerPlayer player = channeler(helper, GooStacks.THOUSAND);
        KnownRecipes.teachRequires(player, unmake);
        int drinkTicks = drinkTicks(helper);
        GooStreamPayload aimed = aimedAt(player, westFace(helper, AIMED_POS));
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, aimed));
        helper.runAfterDelay(SiphonRule.INJECT_TICKS, () -> helper.assertTrue(helper.getBlockState(AIMED_POS)
                .is(Blocks.COBBLESTONE), SQUARE_EARLY));
        helper.runAfterDelay(SiphonRule.INJECT_TICKS + 2L, () -> helper.assertTrue(helper.getBlockState(AIMED_POS)
                .is(GooBlocks.MELTING_BLOCK.get()), SQUARE_LATE));
        helper.runAfterDelay(drinkTicks + 1L, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(helper.getBlockState(AIMED_POS).isAir(), RELEASED);
            helper.succeed();
        });
    }

    /**
     * A mock player holds Unmake at a chicken: Unmake drinks blocks only,
     * and the chicken stands.
     *
     * @param helper the gametest helper
     */
    public static void unmakeLeavesMobsAlone(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        Mob chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, AIMED_POS);
        chicken.setNoGravity(true);
        ServerPlayer player = channeler(helper, GooStacks.THOUSAND);
        KnownRecipes.teachRequires(player, unmake);
        int drinkTicks = drinkTicks(helper);
        GooStreamPayload tick = aimedAt(player, chicken.getBoundingBox().getCenter());
        for (int held = 1; held <= drinkTicks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(drinkTicks + 1L, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(chicken.isAlive() && !chicken.isRemoved(), MOB_GONE);
            helper.succeed();
        });
    }

    /**
     * A hold long enough to drink a cobblestone: the square's flight, then the
     * unstable crucible's own time for it, with slack.
     *
     * @param helper the gametest helper
     * @return the ticks to hold
     */
    private static int drinkTicks(GameTestHelper helper) {
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        return SiphonRule.INJECT_TICKS + SiphonRule.siphonTicks(cobblestone.meltFloorTotal(),
                GooConfig.UNSTABLE_MELT_EXPONENT.get(), 1) + SLACK_TICKS;
    }

    /**
     * @return the eight blocks of the 3x3 wall about the aimed block, square to the look, the aimed block left out
     */
    private static List<BlockPos> wall() {
        List<BlockPos> wall = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dy != 0 || dz != 0) {
                    wall.add(AIMED_POS.offset(0, dy, dz));
                }
            }
        }
        return wall;
    }

    /**
     * @return the column along the aim: the aimed block and the two before it
     */
    private static List<BlockPos> column() {
        List<BlockPos> column = new ArrayList<>();
        for (int depth = 0; depth < COLUMN_BLOCKS; depth++) {
            column.add(AIMED_POS.west(depth));
        }
        return column;
    }

    private static GooStreamPayload aimedAt(ServerPlayer player, Vec3 aimPoint) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.UNSTABLE), UNMAKE.toString(), player.getEyePosition(),
                aimPoint);
    }

    private static Vec3 westFace(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new Vec3(pos.getX(), pos.getY() + FACE_MIDDLE, pos.getZ() + FACE_MIDDLE);
    }

    private static Map<ResourceKey<GooTypeDefinition>, Integer> heldGoo(Inventory inventory) {
        Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(stack);
            if (type != null) {
                held.merge(type, GooStacks.volumeOf(stack), Integer::sum);
            }
        }
        return held;
    }

    /**
     * A survival player standing at the stand, glove in hand and the unstable
     * goo in their inventory; a mock player is creative by default, and a
     * creative inventory never fills.
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer channeler(GameTestHelper helper, int unstable) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, unstable));
        return player;
    }
}
