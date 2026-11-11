package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.HeardSounds;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for the portal sounds the server plays, each heard by every player
 * near it, the one acting too: the placement zap at a freshly placed portal,
 * and the whoosh at both ends of a linked pair a body crosses.
 * decision portal-placement-plays-a-zap-that-opens-into-a-hum
 * decision stepping-through-plays-a-soft-whoosh-at-both-ends
 */
@RegistersGameTests
public final class PortalSoundTests {

    private static final String PLACING_PLAYS_THE_ZAP = "portal_placing_plays_the_placement_sound";
    private static final String MOB_CROSSING_WHOOSHES = "portal_mob_crossing_whooshes_at_both_ends";
    private static final String PLAYER_CROSSING_WHOOSHES = "portal_player_crossing_whooshes_at_both_ends";
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(14, 1, 14);
    private static final Vec3 AWAY = new Vec3(8.5, 1, 8.5);
    /** Feet a block off the first frame's face, in the cell before it: the body does not touch the face. */
    private static final Vec3 A_BLOCK_OFF = new Vec3(1.5, 1, 2.5);
    /** Feet just through the first frame's face, the body's middle a tenth behind it. */
    private static final Vec3 THROUGH_THE_FACE = new Vec3(1.5, 1, 1.9);
    /** How hard the pig is shoved into the first frame's face, in blocks a tick. */
    private static final double SHOVE = 0.8;
    /** Ticks the server is given to gather a pair just linked. */
    private static final int STEP = 3;
    /** Ticks a shoved body is given to go in and come out. */
    private static final int THROUGH = 20;

    private PortalSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PLACING_PLAYS_THE_ZAP, PortalSoundTests::placingPlaysTheZap);
        GooTestFunctions.registerTest(registrar, MOB_CROSSING_WHOOSHES, PortalSoundTests::mobCrossingWhooshes);
        GooTestFunctions.registerTest(registrar, PLAYER_CROSSING_WHOOSHES, PortalSoundTests::playerCrossingWhooshes);
    }

    /**
     * Placing a fresh portal plays the placement sound once at its face's
     * centre, sent to the caster as to everyone near, since no player is
     * excepted; placing the same portal again plays nothing more.
     *
     * @param helper the gametest helper
     */
    public static void placingPlaysTheZap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer caster = standingAway(helper);
        PacketRecorder heard = PacketRecorder.attachTo(caster);
        Portal portal = wallAt(helper, FIRST, Direction.SOUTH);
        PortalChoice.place(level, caster.getUUID(), portal);
        List<ClientboundSoundPacket> fresh = HeardSounds.of(heard, GooSoundIds.PORTAL_PLACE);
        helper.assertTrue(fresh.size() == 1 && HeardSounds.playedAt(fresh.getFirst(), portal.faceCenter()),
                "Placing a fresh portal should play the placement sound once at its face centre "
                        + portal.faceCenter() + ", heard by its caster, plays " + HeardSounds.describe(fresh));
        PortalChoice.place(level, caster.getUUID(), portal);
        List<ClientboundSoundPacket> again = HeardSounds.of(heard, GooSoundIds.PORTAL_PLACE);
        helper.assertTrue(again.size() == 1, "Placing a portal the caster already holds should play nothing more, "
                + "plays " + HeardSounds.describe(again));
        level.getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * A pig shoved through a linked pair's entry plays the whoosh once at the
     * entry's centre and once at the exit's, heard by a player standing
     * between them.
     *
     * @param helper the gametest helper
     */
    public static void mobCrossingWhooshes(GameTestHelper helper) {
        ServerPlayer owner = standingAway(helper);
        PacketRecorder heard = PacketRecorder.attachTo(owner);
        Portal entry = wallAt(helper, FIRST, Direction.SOUTH);
        Portal exit = wallAt(helper, SECOND, Direction.NORTH);
        PortalChoice.place(helper.getLevel(), owner.getUUID(), entry);
        PortalChoice.place(helper.getLevel(), owner.getUUID(), exit);
        helper.runAfterDelay(STEP, () -> {
            Pig pig = helper.spawn(EntityType.PIG, A_BLOCK_OFF);
            pig.setDeltaMovement(0, 0, -SHOVE);
        });
        helper.runAfterDelay(STEP + THROUGH, () -> {
            assertWhooshedAtBothEnds(helper, heard, entry, exit, "A pig carried through a linked pair");
            helper.getLevel().getServer().getPlayerList().remove(owner);
            helper.succeed();
        });
    }

    /**
     * A player whose client says it crossed a linked pair's entry, a crossing
     * the server finds true, plays the whoosh once at each end, heard by an
     * onlooker between them, and the crossing player, standing at the exit,
     * hears the exit's; a crossing the server refuses, its feet beyond the
     * step the server trusts, plays nothing.
     *
     * @param helper the gametest helper
     */
    public static void playerCrossingWhooshes(GameTestHelper helper) {
        ServerPlayer onlooker = standingAway(helper);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        ServerPlayer player = standingAway(helper);
        PacketRecorder crosserHeard = PacketRecorder.attachTo(player);
        Portal entry = wallAt(helper, FIRST, Direction.SOUTH);
        Portal exit = wallAt(helper, SECOND, Direction.NORTH);
        PortalChoice.place(helper.getLevel(), player.getUUID(), entry);
        PortalChoice.place(helper.getLevel(), player.getUUID(), exit);
        helper.runAfterDelay(STEP, () -> {
            GatePassage.announced(player, entry.gate(), helper.absoluteVec(THROUGH_THE_FACE), Vec3.ZERO,
                    player.getYRot(), player.getXRot());
            List<ClientboundSoundPacket> refused = HeardSounds.of(onlookerHeard, GooSoundIds.PORTAL_CROSS);
            helper.assertTrue(refused.isEmpty(), "A crossing the server refuses, the feet far from where it holds "
                    + "the player, should play nothing, plays " + HeardSounds.describe(refused));
            Vec3 feet = helper.absoluteVec(THROUGH_THE_FACE);
            player.teleportTo(feet.x, feet.y, feet.z);
            GatePassage.announced(player, entry.gate(), player.position(), Vec3.ZERO, player.getYRot(),
                    player.getXRot());
            assertWhooshedAtBothEnds(helper, onlookerHeard, entry, exit, "A player's crossing the server accepts");
            helper.assertTrue(HeardSounds.at(crosserHeard, GooSoundIds.PORTAL_CROSS, exit.gate().center()) == 1,
                    "The crossing player should hear the whoosh at the exit it stands before, hears "
                            + HeardSounds.describe(HeardSounds.of(crosserHeard, GooSoundIds.PORTAL_CROSS)));
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.getLevel().getServer().getPlayerList().remove(onlooker);
            helper.succeed();
        });
    }

    private static void assertWhooshedAtBothEnds(GameTestHelper helper, PacketRecorder heard, Portal entry,
            Portal exit, String crossing) {
        Vec3 in = entry.gate().center();
        Vec3 out = exit.gate().center();
        helper.assertTrue(HeardSounds.at(heard, GooSoundIds.PORTAL_CROSS, in) == 1
                && HeardSounds.at(heard, GooSoundIds.PORTAL_CROSS, out) == 1, crossing + " should play the whoosh "
                + "once at the entry's centre " + in + " and once at the exit's " + out + ", plays "
                + HeardSounds.describe(HeardSounds.of(heard, GooSoundIds.PORTAL_CROSS)));
    }

    /** A Quantum player standing in the bay's middle, between the frames, in hearing of both. */
    private static ServerPlayer standingAway(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Vec3 away = helper.absoluteVec(AWAY);
        player.teleportTo(away.x, away.y, away.z);
        return player;
    }

    /** Lays a wall frame two stone blocks tall, its lower block at a cell, facing the way given. */
    private static Portal wallAt(GameTestHelper helper, BlockPos lower, Direction face) {
        helper.setBlock(lower, Blocks.STONE);
        helper.setBlock(lower.above(), Blocks.STONE);
        return new Portal(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(lower)), face, Direction.UP);
    }
}
