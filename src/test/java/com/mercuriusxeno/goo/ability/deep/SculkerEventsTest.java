package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sculker's damage rule on the server: in-wall damage to a player whose Sculker stands is cancelled.
 */
class SculkerEventsTest {

    private static final float SUFFOCATION = 1f;

    /**
     * A player whose Sculker stands, out of the dive: a mocked player in a mocked level.
     *
     * @return the player
     */
    private static Player underSculker() {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.getData(GooAttachments.SCULKER)).thenReturn(new Sculker(Sculker.NEVER_EXPIRES, false));
        return player;
    }

    // decision sculker-grants-suffocation-immunity
    @Test
    void inWallDamageToAPlayerWhoseSculkerStandsIsCancelled() {
        DamageSource inWall = mock(DamageSource.class);
        when(inWall.is(DamageTypes.IN_WALL)).thenReturn(true);
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(underSculker(),
                new DamageContainer(inWall, SUFFOCATION));

        SculkerEvents.onIncomingDamage(event);

        assertTrue(event.isCanceled());
    }
}
