package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A standing goo's hit spares the player who sprayed its cell and no one
 * else; a cell no player sprayed spares nobody
 * (operator ruling 2026-11-17: an ability never attacks its caster).
 */
class SprayerSparedTest {

    private static final UUID SPRAYER = new UUID(1L, 2L);
    private static final UUID SOMEONE_ELSE = new UUID(3L, 4L);

    private static Player playerWith(UUID id) {
        Player player = mock(Player.class);
        when(player.getUUID()).thenReturn(id);
        return player;
    }

    @Test
    void thePlayerWhoSprayedTheCellIsSpared() {
        assertTrue(SprayerSpared.spares(playerWith(SPRAYER), Optional.of(SPRAYER)));
    }

    @Test
    void anotherPlayerIsNotSpared() {
        assertFalse(SprayerSpared.spares(playerWith(SOMEONE_ELSE), Optional.of(SPRAYER)));
    }

    @Test
    void aCellNoPlayerSprayedSparesNobody() {
        assertFalse(SprayerSpared.spares(playerWith(SPRAYER), Optional.empty()));
    }
}
