package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.WispBlock;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for glow's Radiant through the real channel path: a mock player
 * holds it in a sealed stone room. In the dark the room fills with wisps
 * that light it; lit past the threshold, the room takes none
 * (decision radiant-wisps-where-light-is-low).
 */
public final class RadiantChannelTests {

    /** The room's walls, floor and roof span 0 to 6 across and 0 to 4 up; its air is inside. */
    private static final int ROOM_EDGE = 6;
    private static final int ROOM_TOP = 4;
    private static final BlockPos STAND_POS = new BlockPos(3, 1, 3);
    /** Two tries a tick for 100 ticks: most of the ball lies outside the room, so this many tries find its air. */
    private static final int HOLD_TICKS = 100;
    private static final int HELD_GOO = 3;
    private static final int LIGHT_LEVEL_FULL = 15;
    private static final Identifier RADIANT = Identifier.parse("goo:glow_radiant");
    private static final String ABILITY_REQUIRED = "Ability registry must hold glow_radiant";
    private static final String NO_WISP = "Holding Radiant in a dark room should leave a wisp in it";
    private static final String UNLIT = "A wisp should light its cell to %d, it reads %d";
    private static final String WISP_IN_THE_LIGHT = "A lit room should take no wisp, it took %d";

    private RadiantChannelTests() {
    }

    /**
     * Holding Radiant in a sealed dark room leaves wisps in its air, each
     * lighting its cell.
     *
     * @param helper the gametest helper
     */
    public static void radiantLightsADarkRoom(GameTestHelper helper) {
        sealRoom(helper);
        holdRadiant(helper, () -> {
            List<BlockPos> wisps = wispsInTheRoom(helper);
            helper.assertTrue(!wisps.isEmpty(), NO_WISP);
            BlockPos wisp = wisps.getFirst();
            int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(wisp));
            int expected = WispBlock.lightLevel(helper.getBlockState(wisp));
            helper.assertTrue(light == expected, String.format(UNLIT, expected, light));
        });
    }

    /**
     * Holding Radiant in a sealed room lit past the threshold, its top layer
     * all light blocks, leaves no wisp.
     *
     * @param helper the gametest helper
     */
    public static void radiantSkipsALitRoom(GameTestHelper helper) {
        sealRoom(helper);
        lightTheTopLayer(helper);
        holdRadiant(helper, () -> {
            int wisps = wispsInTheRoom(helper).size();
            helper.assertTrue(wisps == 0, String.format(WISP_IN_THE_LIGHT, wisps));
        });
    }

    /** Fills the room's top air layer with light blocks, so every air cell below reads past the threshold. */
    private static void lightTheTopLayer(GameTestHelper helper) {
        for (int x = 1; x < ROOM_EDGE; x++) {
            for (int z = 1; z < ROOM_EDGE; z++) {
                helper.setBlock(new BlockPos(x, ROOM_TOP - 1, z), Blocks.LIGHT.defaultBlockState());
            }
        }
    }

    private static void sealRoom(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_TOP; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shell = x == 0 || x == ROOM_EDGE || y == 0 || y == ROOM_TOP || z == 0 || z == ROOM_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    private static List<BlockPos> wispsInTheRoom(GameTestHelper helper) {
        List<BlockPos> wisps = new ArrayList<>();
        for (int x = 1; x < ROOM_EDGE; x++) {
            for (int y = 1; y < ROOM_TOP; y++) {
                for (int z = 1; z < ROOM_EDGE; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(GooBlocks.WISP.get())) {
                        wisps.add(pos);
                    }
                }
            }
        }
        return wisps;
    }

    /**
     * Stands a mock player in the room holding glow goo in a glove, holds
     * Radiant for the hold's ticks, then runs the check and succeeds.
     *
     * @param helper the gametest helper
     * @param check  the assertions once the hold ends
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void holdRadiant(GameTestHelper helper, Runnable check) {
        AbilityDefinition radiant = AbilityRegistry.of(helper.getLevel()).getAbility(RADIANT);
        helper.assertTrue(radiant != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.GLOW, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, radiant);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.GLOW), RADIANT.toString(),
                player.getEyePosition(), player.getEyePosition().add(0, 0, 1), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            check.run();
            helper.succeed();
        });
    }
}
