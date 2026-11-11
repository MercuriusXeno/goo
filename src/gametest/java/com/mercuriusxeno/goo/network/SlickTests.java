package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Kinetic's Slick, cast through the real stream tick.
 * slick-coats-the-ground-with-kinetic-speed
 */
@RegistersGameTests
public final class SlickTests {

    private static final Identifier SLICK = Identifier.parse("goo:kinetic_slick");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:kinetic_slick";
    private static final String SLICK_SPEEDS_AN_ITEM = "slick_speeds_up_an_item_sliding_on_it";
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the stone's center three blocks off. */
    private static final float AT_THE_STONE = 20.5f;
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos SLICKED_STONE = STREAMER_POS.east(3);
    /** kinetic_slick.json's slick ticks, and some slack past them so the slick has surely landed. */
    private static final int LAY_TICKS = 25;
    /** The item's drop over the slick, low enough that it lands within a few ticks. */
    private static final double DROP_HEIGHT = 0.5;
    /** The push the item lands with, toward the slicked strip's far end. */
    private static final double PUSH = 0.05;
    /** The ticks the item slides on the slick between the two speed readings. */
    private static final int SLIDE_TICKS = 10;
    /** The ticks the item is given to land on the slick. */
    private static final int LANDING_WATCH_TICKS = 20;
    private static final String NO_SLICK = "Slick's stream should lay slick over the stone at %s, holds %s";
    private static final String NEVER_LANDED = "The item dropped onto the slick never landed";
    private static final String NOT_ON_SLICK = "The item should still stand on slick after its slide, stands on %s";
    private static final String NOT_FASTER = "An item sliding on slick should move faster %d ticks after landing"
            + " than at landing: %.4f then %.4f";

    private SlickTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SLICK_SPEEDS_AN_ITEM, SlickTests::slickSpeedsUpAnItemSlidingOnIt);
    }

    /**
     * Slick's stream held on a strip of stone lays slick over it; an item
     * dropped onto the slick with a push east moves faster ten ticks after it
     * lands than it did landing.
     * slick-coats-the-ground-with-kinetic-speed
     *
     * @param helper the gametest helper
     */
    public static void slickSpeedsUpAnItemSlidingOnIt(GameTestHelper helper) {
        for (int east = -1; east <= 2; east++) {
            helper.setBlock(SLICKED_STONE.east(east).below(), Blocks.BEDROCK);
            helper.setBlock(SLICKED_STONE.east(east), Blocks.STONE);
        }
        ServerPlayer streamer = slickStreamer(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= LAY_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        BlockPos slicked = SLICKED_STONE.above();
        helper.runAfterDelay(LAY_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(helper.getBlockState(slicked).is(GooBlocks.KINETIC_SLICK.get()),
                    String.format(NO_SLICK, slicked, helper.getBlockState(slicked)));
            ItemEntity item = helper.spawnItem(Items.SLIME_BALL,
                    Vec3.atBottomCenterOf(slicked).add(0, DROP_HEIGHT, 0));
            item.setDeltaMovement(PUSH, 0, 0);
            watchTheSlide(helper, item);
        });
    }

    /**
     * Reads the item's horizontal speed the tick it lands and ten ticks on,
     * and succeeds where the later speed is the greater.
     *
     * @param helper the gametest helper
     * @param item   the item dropped onto the slick
     */
    private static void watchTheSlide(GameTestHelper helper, ItemEntity item) {
        double[] landed = {-1};
        for (int tick = 1; tick <= LANDING_WATCH_TICKS; tick++) {
            int watched = tick;
            helper.runAfterDelay(tick, () -> {
                if (landed[0] < 0 && item.onGround()) {
                    landed[0] = item.getDeltaMovement().horizontalDistance();
                    helper.runAfterDelay(SLIDE_TICKS, () -> assertFaster(helper, item, landed[0]));
                } else if (landed[0] < 0 && watched == LANDING_WATCH_TICKS) {
                    helper.fail(NEVER_LANDED);
                }
            });
        }
    }

    private static void assertFaster(GameTestHelper helper, ItemEntity item, double atLanding) {
        double later = item.getDeltaMovement().horizontalDistance();
        BlockPos under = item.getOnPos();
        helper.assertTrue(helper.getLevel().getBlockState(under).is(GooBlocks.KINETIC_SLICK.get()),
                String.format(NOT_ON_SLICK, helper.getLevel().getBlockState(under)));
        helper.assertTrue(later > atLanding, String.format(NOT_FASTER, SLIDE_TICKS, atLanding, later));
        helper.succeed();
    }

    /**
     * A Kinetic invoker standing at the bay's west side facing east, pitched
     * down at the stone, taught what Slick requires.
     *
     * @param helper the gametest helper
     * @return the streamer
     */
    private static ServerPlayer slickStreamer(GameTestHelper helper) {
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.KINETIC);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(AT_THE_STONE);
        AbilityDefinition slick = AbilityRegistry.of(helper.getLevel()).getAbility(SLICK);
        helper.assertTrue(slick != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, slick);
        return streamer;
    }
}
