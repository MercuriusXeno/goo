package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the damage a Reap slash cuts with: a slash is its slasher's own
 * attack, a player's attack where a player slashes, never magic.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class SlashCutsTest {

    private final DamageSources sources = mock(DamageSources.class);

    @Test
    void aPlayersSlashCutsAsThatPlayersAttack() {
        ServerPlayer slasher = mock(ServerPlayer.class);
        DamageSource attack = mock(DamageSource.class);
        when(sources.playerAttack(slasher)).thenReturn(attack);
        assertSame(attack, SlashCuts.cutBy(sources, slasher));
        verify(sources, never()).magic();
    }

}
