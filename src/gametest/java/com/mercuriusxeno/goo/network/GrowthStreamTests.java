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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for leaf growth: a held stream random-ticks the plants in its
 * cone, so wheat planted three blocks ahead of the player ripens within the
 * hold, and a vine there spreads (decision growth-breeze-ticks-plants).
 */
public final class GrowthStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on farmland the test lays. */
    private static final BlockPos WHEAT_POS = STAND_POS.east(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the wheat's middle three blocks off. */
    private static final float LOOKING_AT_WHEAT = 20.5f;
    /** The longest hold; wheat under the breeze ripens in a fraction of it. */
    private static final int HOLD_TICKS = 160;
    /** leaf_growth.json costs a thousand a second; the player holds enough for the whole hold and then some. */
    private static final int HELD_GOO = 10;
    private static final Identifier LEAF_GROWTH = Identifier.parse("goo:leaf_growth");
    private static final String ABILITY_REQUIRED = "Ability registry must hold leaf_growth";
    private static final String SHOULD_SPREAD = "A crowded vine patch under the breeze should grow from %d, stands %d";
    /** The wall the vines hang on runs from the floor four blocks up, three wide. */
    private static final int VINE_WALL_TOP = 4;
    private static final int VINE_PATCH_MIN_Z = 1;
    private static final int VINE_PATCH_MAX_Z = 3;
    /** The patch's two rows of three: six vines, past the five vanilla's random tick spreads within. */
    private static final int VINE_PATCH_LOW = 2;
    private static final int VINE_PATCH_HIGH = 3;
    private static final String SHOULD_RIPEN = "Wheat under the breeze should ripen within the hold, stands at age %d";

    private GrowthStreamTests() {
    }

    /**
     * A mock player holds Growth on freshly planted wheat on moist farmland,
     * and the wheat reaches its full age before the hold ends.
     *
     * @param helper the gametest helper
     */
    public static void growthMaturesWheat(GameTestHelper helper) {
        helper.setBlock(WHEAT_POS.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,
                FarmlandBlock.MAX_MOISTURE));
        helper.setBlock(WHEAT_POS, Blocks.WHEAT);
        ServerPlayer player = grower(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.LEAF), LEAF_GROWTH.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.succeedWhen(() -> {
            BlockState wheat = helper.getBlockState(WHEAT_POS);
            helper.assertTrue(wheat.is(Blocks.WHEAT) && wheat.getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    String.format(SHOULD_RIPEN, wheat.getValue(CropBlock.AGE)));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player holds Growth on a patch of six vines on a stone wall, a
     * patch crowded past the five vanilla's random tick lets spread, and the
     * patch grows: hanging longer below or creeping along the wall.
     *
     * @param helper the gametest helper
     */
    public static void growthSpreadsAVine(GameTestHelper helper) {
        for (int z = VINE_PATCH_MIN_Z; z <= VINE_PATCH_MAX_Z; z++) {
            for (int y = 1; y <= VINE_WALL_TOP; y++) {
                helper.setBlock(new BlockPos(WHEAT_POS.getX() + 1, y, z), Blocks.STONE);
            }
            for (int y = VINE_PATCH_LOW; y <= VINE_PATCH_HIGH; y++) {
                helper.setBlock(new BlockPos(WHEAT_POS.getX(), y, z),
                        Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
            }
        }
        int planted = vinesIn(helper);
        ServerPlayer player = grower(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.LEAF), LEAF_GROWTH.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.succeedWhen(() -> {
            int grown = vinesIn(helper);
            helper.assertTrue(grown > planted, String.format(SHOULD_SPREAD, planted, grown));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static int vinesIn(GameTestHelper helper) {
        int vines = 0;
        for (int y = 0; y <= VINE_WALL_TOP + 1; y++) {
            for (int z = VINE_PATCH_MIN_Z - 1; z <= VINE_PATCH_MAX_Z + 1; z++) {
                vines += helper.getBlockState(new BlockPos(WHEAT_POS.getX(), y, z)).is(Blocks.VINE) ? 1 : 0;
            }
        }
        return vines;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer grower(GameTestHelper helper) {
        AbilityDefinition growth = AbilityRegistry.of(helper.getLevel()).getAbility(LEAF_GROWTH);
        helper.assertTrue(growth != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_WHEAT);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.LEAF, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, growth);
        return player;
    }
}
