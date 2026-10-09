package com.mercuriusxeno.goo.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * A mock server player in survival, placed in the level the way vanilla's
 * mock server player is: vanilla's stands in creative, which no mob takes as
 * a target, so a test of a mob targeting a player needs this one. Joining
 * the creative test level leaves it creative's invulnerable abilities, so
 * it is set to survival once placed.
 */
public final class SurvivalPlayers {

    private static final String NAME = "test-survival-player";
    /** Where the player stands in the test bay, clear of its barrier shell. */
    private static final BlockPos STAND_POS = new BlockPos(2, 1, 2);

    private SurvivalPlayers() {
    }

    /**
     * Places a survival mock player in the test bay; the test removes
     * it through the player list before it succeeds.
     *
     * @param helper the gametest helper
     * @return the player
     */
    public static ServerPlayer placeIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), NAME), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        return player;
    }
}
