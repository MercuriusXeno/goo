package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.yarn.Herd;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Yarn's Herd in a live world: a cow inside Herd's radius
 * walks to the player holding Herd and stops a few blocks short, holding
 * no attack target while led
 * (decision leader-leads-passive-mobs-at-a-short-distance).
 */
@RegistersGameTests
public final class HerdTests {
    private static final String PASSIVE_MOB_TRAILS_THE_PLAYER = "herd_passive_mob_trails_the_player_and_stops_short";

    private static final Identifier YARN_HERD = Identifier.fromNamespaceAndPath(Goo.MODID, "yarn_herd");
    /** Where the player holding Herd stands, at one side of the light bay. */
    private static final BlockPos HERD_POS = new BlockPos(2, 1, 8);
    /** Where the cow starts: ten blocks from the player, inside Herd's radius. */
    private static final BlockPos COW_POS = new BlockPos(12, 1, 8);
    /** The tick by which the cow has closed on the player. */
    private static final int CLOSED_BY = 120;
    /** The tick by which the cow has held short of the player for two seconds. */
    private static final int HELD_BY = CLOSED_BY + 40;
    /** The distance closer than which the cow stands on the player rather than short of it. */
    private static final double ON_THE_PLAYER = 1.5;
    private static final String HERD_NOT_HELD = "The player should hold Herd once it lands";
    private static final String COW_FAR = "The cow should close on the player from %.2f off by tick %d, stands %.2f off";
    private static final String COW_ON_THE_PLAYER = "The cow should stop short of the player, stands %.2f off at tick %d";
    private static final String COW_TARGETS = "A led cow should hold no attack target, holds %s";

    private HerdTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PASSIVE_MOB_TRAILS_THE_PLAYER, HerdTests::passiveMobTrailsThePlayerAndStopsShort);
    }

    /**
     * A player casts Herd from the glove beside a free-willed cow ten blocks
     * off: the cow walks to the player, stops short of it and holds there,
     * targeting nothing.
     * leader-leads-passive-mobs-at-a-short-distance
     *
     * @param helper the gametest helper
     */
    public static void passiveMobTrailsThePlayerAndStopsShort(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.YARN, YARN_HERD);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(HERD_POS));
        player.setPos(stand.x, stand.y, stand.z);
        Herd herd = player.getData(GooAttachments.HERD);
        helper.assertTrue(herd.stands(), HERD_NOT_HELD);
        Cow cow = helper.spawn(EntityType.COW, COW_POS);
        double started = cow.distanceTo(player);
        SelfDeliveryTests.tickFor(helper, player, HELD_BY - 1);
        helper.runAfterDelay(CLOSED_BY, () -> assertStandsShort(helper, player, cow, started, CLOSED_BY));
        helper.runAfterDelay(HELD_BY, () -> {
            player.doTick();
            assertStandsShort(helper, player, cow, started, HELD_BY);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void assertStandsShort(GameTestHelper helper, ServerPlayer player, Cow cow, double started,
                                          int tick) {
        double off = cow.distanceTo(player);
        helper.assertTrue(off < started, String.format(COW_FAR, started, tick, off));
        helper.assertTrue(off >= ON_THE_PLAYER, String.format(COW_ON_THE_PLAYER, off, tick));
        helper.assertTrue(cow.getTarget() == null, String.format(COW_TARGETS, cow.getTarget()));
    }
}
