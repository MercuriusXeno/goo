package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import java.util.List;

/**
 * While a player's Sculker stands its movement makes no vibration and no
 * sound: the movement game events below are cancelled before any sculk
 * sensor, shrieker or warden hears them, and the step, swim and fall sounds
 * the mixins skip go unplayed on both sides. Every other sound and event
 * the player makes, breaking blocks and attacks among them, still plays.
 * decision sculker-mutes-your-movement-sounds
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SculkerSilence {

    /** The movement game events a player under Sculker never sends: walking, swimming, splashing, landing, flapping and gliding. */
    static final List<Holder<GameEvent>> MUTED_EVENTS = List.of(GameEvent.STEP, GameEvent.SWIM, GameEvent.SPLASH,
            GameEvent.HIT_GROUND, GameEvent.FLAP, GameEvent.ELYTRA_GLIDE);

    private SculkerSilence() {
    }

    /**
     * Whether an entity moves in silence now.
     *
     * @param entity the entity moving
     * @return true for a player whose Sculker stands
     */
    public static boolean movesSilently(Entity entity) {
        return entity instanceof Player player && SculkerSwim.dives(player);
    }

    /**
     * Cancels a movement game event a silent player sends.
     *
     * @param event the vanilla game event about to reach its listeners
     */
    @SubscribeEvent
    public static void onVanillaGameEvent(VanillaGameEvent event) {
        if (event.getCause() != null && movesSilently(event.getCause())
                && MUTED_EVENTS.stream().anyMatch(muted -> muted.value() == event.getVanillaEvent().value())) {
            event.setCanceled(true);
        }
    }
}
