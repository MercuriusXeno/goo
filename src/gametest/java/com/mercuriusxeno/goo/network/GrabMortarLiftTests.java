package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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
 * Gametests for Kinetic's Grab at ground painted with Mortar, driven through the channel's stream tick: the grab
 * lifts the blocks under the Mortar out of the ground as one held hunk, as deep as the Mortar's layers.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-rock-goo-is-always-vertical
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

    private GrabMortarLiftTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFTS_THE_PAINTED_GROUND, GrabMortarLiftTests::liftsTheGroundMortarPaints);
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
        ServerPlayer player = grabber(helper);
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
        return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
    }

    private static GooStreamPayload aimTick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), KINETIC_GRAB.toString(),
                eye, eye.add(player.getLookAngle().scale(AIM_LENGTH)));
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer grabber(GameTestHelper helper) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, grab);
        return player;
    }
}
