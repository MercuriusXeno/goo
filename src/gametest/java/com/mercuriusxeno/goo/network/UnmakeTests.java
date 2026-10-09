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

/**
 * Gametests for Unmake's drink: a hold down a cobblestone column at mid
 * range drinks the column the narrow cone covers, deep but not wide, all
 * together over the unstable crucible's own time for a cobblestone, burning
 * twice the crucible's fuel for each block, the wall about it standing, and
 * the blocks' full goo goes into the player's inventory; a player with no
 * space gets it at their feet;
 * a block that started streaming goes until it is done though the cursor
 * leaves it or the use is let go, while one outside the cone stands; and a
 * mob at the cursor is left alone.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeTests {

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
    /** A full stack of dirt, what fills the inventory. */
    private static final int DIRT_STACK = 64;
    /** How near the player's feet the goo that had no space lands. */
    private static final double AT_THE_FEET = 0.5;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_unmake";
    private static final String STANDS = "The wall's block at %s should be drunk within %d ticks";
    private static final String UNPAID = "The hold should burn exactly twice the unstable crucible's fuel per block";
    private static final String WALL_GONE = "The wall's block at %s, off the aim, should stand";
    private static final String WRONG_YIELD = "The inventory should hold %s, held %s";
    private static final String DROPPED = "With space in the inventory nothing should drop, dropped %s";
    private static final String NOT_AT_FEET =
            "The goo with no space should drop at the player's feet as %s, dropped %s, the inventory holding %s";
    private static final String STILL_CREATIVE = "The channeler should be a survival player, whose inventory fills";
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
     * stands, the fuel is spent, and the three blocks' full goo is in the
     * player's inventory with nothing dropped.
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
        int fuel = SiphonRule.fuelFor(cobblestone.totalGoo(), GooConfig.UNSTABLE_MELT_EXPONENT.get(),
                GooConfig.UNSTABLE_TICKS_PER_MB.get());
        ServerPlayer player = channeler(helper, COLUMN_BLOCKS * fuel);
        KnownRecipes.teachRequires(player, unmake);
        int drinkTicks = drinkTicks(helper);
        GooStreamPayload tick = aimedAt(player, westFace(helper, AIMED_POS));
        for (int held = 1; held <= drinkTicks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(drinkTicks + 1L, () -> {
            for (BlockPos pos : column) {
                helper.assertTrue(helper.getBlockState(pos).isAir(), String.format(STANDS, pos, drinkTicks));
            }
            for (BlockPos pos : wall) {
                helper.assertTrue(helper.getBlockState(pos).is(Blocks.COBBLESTONE), String.format(WALL_GONE, pos));
            }
            helper.assertFalse(GooSourceScanner.hasEnough(player, GooTypes.UNSTABLE, 1), UNPAID);
        });
        helper.runAfterDelay(drinkTicks + 2L, () -> {
            Map<ResourceKey<GooTypeDefinition>, Integer> expected = new HashMap<>();
            cobblestone.getAll().forEach((type, amount) -> expected.put(type, amount * COLUMN_BLOCKS));
            Map<ResourceKey<GooTypeDefinition>, Integer> held = heldGoo(player.getInventory());
            Map<ResourceKey<GooTypeDefinition>, Integer> dropped = droppedGoo(helper, AIMED_POS, DROP_REACH);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(new GooContents(expected).getAll().equals(held), String.format(WRONG_YIELD, expected,
                    held));
            helper.assertTrue(dropped.isEmpty(), String.format(DROPPED, dropped));
            helper.succeed();
        });
    }

    /**
     * A mock player with no space in their inventory drinks one cobblestone:
     * its full goo drops at their feet, measured about the stand they were
     * given, since a mock survival player's own position drifts after the drop.
     *
     * @param helper the gametest helper
     */
    public static void unmakeOverflowDropsAtFeet(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        helper.setBlock(AIMED_POS, Blocks.COBBLESTONE);
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        ServerPlayer player = channeler(helper, GooStacks.THOUSAND);
        KnownRecipes.teachRequires(player, unmake);
        fillTheInventory(player.getInventory());
        int drinkTicks = drinkTicks(helper);
        GooStreamPayload tick = aimedAt(player, westFace(helper, AIMED_POS));
        for (int held = 1; held <= drinkTicks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(drinkTicks + 2L, () -> {
            Map<ResourceKey<GooTypeDefinition>, Integer> dropped = droppedGoo(helper, STAND_POS, AT_THE_FEET + 1);
            Map<ResourceKey<GooTypeDefinition>, Integer> held = heldGoo(player.getInventory());
            boolean creative = player.hasInfiniteMaterials();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(helper.getBlockState(AIMED_POS).isAir(), String.format(STANDS, AIMED_POS, drinkTicks));
            helper.assertFalse(creative, STILL_CREATIVE);
            Map<ResourceKey<GooTypeDefinition>, Integer> expected = cobblestone.toGooContents().getAll();
            helper.assertTrue(expected.equals(dropped), String.format(NOT_AT_FEET, expected, dropped, held));
            helper.succeed();
        });
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
     * no tick following: the block that started is drunk anyway.
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
     * A hold long enough to drink a cobblestone: the unstable crucible's own
     * time for it, with slack.
     *
     * @param helper the gametest helper
     * @return the ticks to hold
     */
    private static int drinkTicks(GameTestHelper helper) {
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        return SiphonRule.siphonTicks(cobblestone.totalGoo(), GooConfig.UNSTABLE_MELT_EXPONENT.get(), 1) + SLACK_TICKS;
    }

    /**
     * Fills every empty slot of an inventory with dirt, so nothing more fits.
     *
     * @param inventory the inventory
     */
    private static void fillTheInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, new ItemStack(Blocks.DIRT, DIRT_STACK));
            }
        }
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

    private static Map<ResourceKey<GooTypeDefinition>, Integer> droppedGoo(GameTestHelper helper, BlockPos around,
                                                                         double reach) {
        AABB box = new AABB(helper.absolutePos(around)).inflate(reach);
        Map<ResourceKey<GooTypeDefinition>, Integer> dropped = new HashMap<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(item.getItem());
            if (type != null) {
                dropped.merge(type, GooStacks.volumeOf(item.getItem()), Integer::sum);
            }
        }
        return dropped;
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
