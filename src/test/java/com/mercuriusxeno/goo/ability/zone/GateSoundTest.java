package com.mercuriusxeno.goo.ability.zone;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers how the conjured gates of End, Zone and Tartarus sound over a trip: the conjuring clip once per hold,
 * slowed to span the channel, and the door's sound once, on arrival.
 */
class GateSoundTest {

    private static final float EXACT = 1e-6f;

    @Test
    void theConjuringClipSlowsToSpanTheTwoSecondChannel() {
        assertEquals(0.7f, ConjuredDoors.conjurePitchFor(40), EXACT);
    }

    @Test
    void theConjuringClipPlaysOnTheHoldsFirstTickAlone() {
        assertTrue(ConjuredDoors.conjuresAt(1), "on the first tick");
        assertFalse(ConjuredDoors.conjuresAt(2), "not on a later tick a dropped door is stood again");
    }

    @Test
    void theDoorSoundsOnceAfterItCarriesThePlayer() {
        ServerPlayer player = mock(ServerPlayer.class);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        when(player.getRandom()).thenReturn(RandomSource.create(1));
        TeleportTransition transition = new TeleportTransition(mock(ServerLevel.class), Vec3.ZERO, Vec3.ZERO, 0f, 0f,
                TeleportTransition.DO_NOTHING);

        ConjuredDoors.carry(player, transition);

        InOrder order = inOrder(player, player.connection);
        order.verify(player).teleport(transition);
        order.verify(player.connection).send(any(ClientboundSoundPacket.class));
        verify(player.connection, times(1)).send(any(ClientboundSoundPacket.class));
    }
}
