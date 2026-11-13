package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.VanillaGameEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A player whose Sculker stands sends no step and no landing: those vanilla game events are cancelled before
 * any listener hears them.
 */
class SculkerSilenceTest {

    private static final Map<String, Holder<GameEvent>> MOVEMENT = Map.of("step", GameEvent.STEP,
            "hit_ground", GameEvent.HIT_GROUND);

    // decision sculker-mutes-your-movement-sounds
    @ParameterizedTest
    @ValueSource(strings = {"step", "hit_ground"})
    void aSculkerHoldersMovementEventIsCancelled(String movement) {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.getData(GooAttachments.SCULKER)).thenReturn(new Sculker(Sculker.NEVER_EXPIRES, false));
        VanillaGameEvent event = new VanillaGameEvent(level, MOVEMENT.get(movement), Vec3.ZERO,
                GameEvent.Context.of(player));

        SculkerSilence.onVanillaGameEvent(event);

        assertTrue(event.isCanceled());
    }
}
