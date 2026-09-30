package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The player host runs a self ability on the invoking player: the player is
 * its own thrower, so a thrower-look step follows the player's look, and a
 * step the host cannot serve refuses at load naming it and the player host
 * (decision self-delivery-runs-on-player).
 */
class PlayerHostTest {

    private static final String PLAYER_LABEL = "player host";
    private static final double BLINK = 8;
    private static final double X = 10;
    private static final double Y = 64;
    private static final double Z = -3;

    private static ServerPlayer playerLooking(Vec3 look) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getLookAngle()).thenReturn(look);
        when(player.getX()).thenReturn(X);
        when(player.getY()).thenReturn(Y);
        when(player.getZ()).thenReturn(Z);
        return player;
    }

    private static void run(List<Step> steps, ServerPlayer player) {
        ProgramBehavior.forHost(steps, HostKind.PLAYER).tick(new PlayerHost(mock(ServerLevel.class), player));
    }

    @Nested
    class ThrowerLook {

        @Test
        void teleportAlongThrowerLookFollowsThePlayersOwnLook() {
            ServerPlayer player = playerLooking(new Vec3(1, 0, 0));

            run(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), player);

            verify(player).teleportTo(X + BLINK, Y, Z);
        }

        @Test
        void teleportAlongThrowerLookClimbsWithThePitch() {
            ServerPlayer player = playerLooking(new Vec3(0, 0.6, 0.8));

            run(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), player);

            verify(player).teleportTo(X, Y + 0.6 * BLINK, Z + 0.8 * BLINK);
        }
    }

    @Nested
    class Load {

        @Test
        void markerOnlyStepRefusesNamingTheStepAndThePlayerHost() {
            List<Step> steps = List.of(LeafSteps.WAIT.step(Expr.literal(1)));

            ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                    () -> ProgramBehavior.forHost(steps, HostKind.PLAYER));

            assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains(PLAYER_LABEL), refusal.getMessage());
        }

        @Test
        void teleportLoadsOnThePlayerHost() {
            assertDoesNotThrow(() -> ProgramBehavior.forHost(
                    List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), HostKind.PLAYER));
        }

        @Test
        void playerHostProvidesTargetExplodeAndEntityScan() {
            assertEquals(Set.of(HostCapability.TARGET, HostCapability.EXPLODE, HostCapability.ENTITY_SCAN),
                    HostKind.PLAYER.capabilities());
        }
    }
}
