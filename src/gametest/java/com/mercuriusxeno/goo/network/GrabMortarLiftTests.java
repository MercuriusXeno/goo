package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Kinetic's Grab at blocks painted with Mortar, driven through the channel's stream tick: the grab
 * lifts the blocks behind the Mortar out of the face it coats as one held hunk, as deep as the Mortar's layers.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-mortar-pulls-out-of-the-coated-face
 */
@RegistersGameTests
public final class GrabMortarLiftTests {
    private static final String LIFTS_THE_PAINTED_GROUND = "grab_lifts_the_ground_mortar_paints";

    private static final Identifier KINETIC_GRAB = Identifier.parse("goo:kinetic_grab");
    /** The ground: two blocks of stone deep across the bay's east side, its top at y 3. */
    private static final int GROUND_LOW_Y = 1;
    private static final int GROUND_HIGH_Y = 2;
    private static final int GROUND_LOW_X = 1;
    private static final int GROUND_HIGH_X = 6;
    private static final int GROUND_LOW_Z = 0;
    private static final int GROUND_HIGH_Z = 4;
    /** The player stands on the ground at its west edge. */
    private static final BlockPos STAND_POS = new BlockPos(1, 3, 2);
    /** Two layers of Mortar lying on the ground's top, three blocks east of the player. */
    private static final BlockPos MORTAR_CELL = new BlockPos(4, 3, 2);
    private static final int MORTAR_LAYERS = 2;
    private static final List<BlockPos> PAINTED_COLUMN = List.of(MORTAR_CELL.below(), MORTAR_CELL.below(2));
    /** The top of the ground, which a lifted hunk's blocks stand above. */
    private static final double GROUND_TOP = GROUND_HIGH_Y + 1;
    /** Channel ticks enough for the held hunk to rise out of its hole to the hold. */
    private static final int HOLD_TICKS = 30;
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    private static final int HELD_GOO = 3;
    /** How far along the look a hold tick names its end; Grab reaches by its own range whatever this. */
    private static final double AIM_LENGTH = 3;
    private static final double HALF = 0.5;
    private static final String ABILITY_REQUIRED = "goo:kinetic_grab must be loaded";
    /** The bay's far corner, so a hunk count reads this test's bay alone beside a batch's other tests. */
    private static final BlockPos BAY_FAR_CORNER = new BlockPos(5, 5, 5);

    private static final String LIFTS_THE_PAINTED_WALL = "grab_lifts_the_wall_mortar_paints";
    /** The wall: two blocks of stone thick along the bay's west side, its east face at x 2. */
    private static final int WALL_LOW_X = 0;
    private static final int WALL_HIGH_X = 1;
    private static final int WALL_LOW_Y = 1;
    private static final int WALL_HIGH_Y = 3;
    private static final double WALL_FACE = WALL_HIGH_X + 1;
    /** Two layers of Mortar clinging to the wall's east face. */
    private static final BlockPos WALL_MORTAR_CELL = new BlockPos(2, 2, 2);
    private static final List<BlockPos> PAINTED_ROW = List.of(WALL_MORTAR_CELL.west(), WALL_MORTAR_CELL.west(2));
    /** The player stands on the bay's floor at its east edge, facing the wall. */
    private static final BlockPos WALL_STAND_POS = new BlockPos(5, 1, 2);
    private static final float FACING_WEST = 90f;
    /** The look raised once the grab takes, so Grab's hold point hangs clear of the wall in the open bay. */
    private static final float LOOKING_UP = -45f;
    /** Channel ticks enough for the held piece to slide out of the wall and rise to the hold. */
    private static final int WALL_HOLD_TICKS = 40;

    private GrabMortarLiftTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFTS_THE_PAINTED_GROUND, GrabMortarLiftTests::liftsTheGroundMortarPaints);
        GooTestFunctions.registerTest(registrar, LIFTS_THE_PAINTED_WALL, GrabMortarLiftTests::liftsTheWallMortarPaints);
    }

    /**
     * A mock player Grabbing at two layers of Mortar on a stone wall's east face leaves air where the two blocks
     * behind that face stood, and holds one hunk carrying those two blocks out east of the wall.
     *
     * @param helper the gametest helper
     */
    public static void liftsTheWallMortarPaints(GameTestHelper helper) {
        layWall(helper);
        helper.setBlock(WALL_MORTAR_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.coatProperty(Direction.WEST), SideCoat.ATTACHED)
                .setValue(VolumeCellBlock.LAYERS, MORTAR_LAYERS));
        ServerPlayer player = grabber(helper, WALL_STAND_POS, FACING_WEST);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(WALL_MORTAR_CELL)));
        for (int held = 1; held <= WALL_HOLD_TICKS; held++) {
            int tick = held;
            helper.runAfterDelay(tick, () -> {
                if (tick == 2) {
                    player.setXRot(LOOKING_UP);
                }
                GooStreamHandler.streamTick(player, aimTick(player));
            });
        }
        helper.runAfterDelay(WALL_HOLD_TICKS + 1, () -> {
            for (BlockPos cell : PAINTED_ROW) {
                helper.assertBlockPresent(Blocks.AIR, cell);
            }
            BlockHunk hunk = theOneHunk(helper, player);
            helper.assertTrue(hunk.blocks().size() == PAINTED_ROW.size()
                            && hunk.blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                    "The hunk should hold the two stone blocks behind the Mortar, held " + hunk.blocks());
            double west = hunk.getBoundingBox().minX - helper.absolutePos(BlockPos.ZERO).getX();
            helper.assertTrue(west >= WALL_FACE - HALF * HALF,
                    "The hunk should stand east of the wall face at x " + WALL_FACE + ", its west side at " + west);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void layWall(GameTestHelper helper) {
        for (int x = WALL_LOW_X; x <= WALL_HIGH_X; x++) {
            for (int y = WALL_LOW_Y; y <= WALL_HIGH_Y; y++) {
                for (int z = GROUND_LOW_Z; z <= GROUND_HIGH_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static BlockHunk theOneHunk(GameTestHelper helper, ServerPlayer player) {
        List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay(helper));
        helper.assertTrue(hunks.size() == 1, "One hunk should hold the painted blocks, found " + hunks.size());
        BlockHunk hunk = hunks.getFirst();
        helper.assertTrue(GrabEvents.heldBy(player) == hunk, "The player should hold the hunk");
        return hunk;
    }

    /**
     * A mock player Grabbing at two layers of Mortar on stone ground leaves air where the two blocks under it
     * stood, and holds one hunk carrying those two blocks above the hole once the look comes level.
     *
     * @param helper the gametest helper
     */
    public static void liftsTheGroundMortarPaints(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(MORTAR_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, MORTAR_LAYERS));
        ServerPlayer player = grabber(helper, STAND_POS, FACING_EAST);
        Vec3 painted = Vec3.atBottomCenterOf(helper.absolutePos(MORTAR_CELL));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, painted);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            int tick = held;
            helper.runAfterDelay(tick, () -> {
                if (tick == 2) {
                    player.setXRot(LEVEL);
                }
                GooStreamHandler.streamTick(player, aimTick(player));
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            for (BlockPos cell : PAINTED_COLUMN) {
                helper.assertBlockPresent(Blocks.AIR, cell);
            }
            List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay(helper));
            helper.assertTrue(hunks.size() == 1, "One hunk should hold the painted ground, found " + hunks.size());
            BlockHunk hunk = hunks.getFirst();
            helper.assertTrue(hunk.blocks().size() == PAINTED_COLUMN.size()
                            && hunk.blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                    "The hunk should hold the two stone blocks under the Mortar, held " + hunk.blocks());
            double bottom = hunk.getBoundingBox().minY - helper.absolutePos(BlockPos.ZERO).getY();
            helper.assertTrue(bottom >= GROUND_TOP - HALF * HALF,
                    "The hunk should stand above its hole, out of the ground at y " + GROUND_TOP + ", its bottom at "
                            + bottom);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void layGround(GameTestHelper helper) {
        for (int x = GROUND_LOW_X; x <= GROUND_HIGH_X; x++) {
            for (int y = GROUND_LOW_Y; y <= GROUND_HIGH_Y; y++) {
                for (int z = GROUND_LOW_Z; z <= GROUND_HIGH_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static AABB bay(GameTestHelper helper) {
        return AABB.encapsulatingFullBlocks(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(BAY_FAR_CORNER));
    }

    private static GooStreamPayload aimTick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), KINETIC_GRAB.toString(),
                eye, eye.add(player.getLookAngle().scale(AIM_LENGTH)));
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer grabber(GameTestHelper helper, BlockPos standPos, float facing) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standPos));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(facing);
        player.setYHeadRot(facing);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, grab);
        return player;
    }
}
