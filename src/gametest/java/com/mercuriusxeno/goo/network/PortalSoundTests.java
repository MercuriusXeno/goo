package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for the portal sounds the server plays: the placement zap at a
 * freshly placed portal, heard by every player near it, the caster too.
 * decision portal-placement-plays-a-zap-that-opens-into-a-hum
 */
@RegistersGameTests
public final class PortalSoundTests {

    private static final String PLACING_PLAYS_THE_ZAP = "portal_placing_plays_the_placement_sound";
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);
    private static final Vec3 AWAY = new Vec3(8.5, 1, 8.5);
    /** A sound packet carries its position in eighths of a block. */
    private static final double SOUND_GRID = 0.125;

    private PortalSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PLACING_PLAYS_THE_ZAP, PortalSoundTests::placingPlaysTheZap);
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
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Vec3 away = helper.absoluteVec(AWAY);
        caster.teleportTo(away.x, away.y, away.z);
        PacketRecorder heard = PacketRecorder.attachTo(caster);
        Portal portal = wallAt(helper, FRAME, Direction.SOUTH);
        PortalChoice.place(level, caster.getUUID(), portal);
        List<ClientboundSoundPacket> fresh = soundsOf(heard, GooSoundIds.PORTAL_PLACE);
        helper.assertTrue(fresh.size() == 1 && soundsAt(fresh.getFirst(), portal.faceCenter()), "Placing a fresh "
                + "portal should play the placement sound once at its face centre " + portal.faceCenter()
                + ", heard by its caster, plays " + describe(fresh));
        PortalChoice.place(level, caster.getUUID(), portal);
        helper.assertTrue(soundsOf(heard, GooSoundIds.PORTAL_PLACE).size() == 1, "Placing a portal the caster "
                + "already holds should play nothing more, plays " + describe(soundsOf(heard, GooSoundIds.PORTAL_PLACE)));
        level.getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /** Lays a wall frame two stone blocks tall, its lower block at a cell, facing the way given. */
    private static Portal wallAt(GameTestHelper helper, BlockPos lower, Direction face) {
        helper.setBlock(lower, Blocks.STONE);
        helper.setBlock(lower.above(), Blocks.STONE);
        return new Portal(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(lower)), face, Direction.UP);
    }

    private static List<ClientboundSoundPacket> soundsOf(PacketRecorder heard, Identifier sound) {
        return heard.sentOf(ClientboundSoundPacket.class).stream().filter(packet -> packet.getSound().is(sound))
                .toList();
    }

    private static boolean soundsAt(ClientboundSoundPacket packet, Vec3 point) {
        return Math.abs(packet.getX() - point.x) <= SOUND_GRID && Math.abs(packet.getY() - point.y) <= SOUND_GRID
                && Math.abs(packet.getZ() - point.z) <= SOUND_GRID;
    }

    private static String describe(List<ClientboundSoundPacket> sounds) {
        return sounds.stream().map(packet -> "(" + packet.getX() + ", " + packet.getY() + ", " + packet.getZ() + ")")
                .toList().toString();
    }
}
