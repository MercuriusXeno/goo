package com.mercuriusxeno.goo.ability.pulse;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GooBrewEffect;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import java.util.List;

/**
 * The pulse brew's flat extension: drinking it lengthens every timed effect
 * standing on the player, each prepaid goo brew and each vanilla effect, by
 * the brew's duration, and while the brew stands each timed effect applied
 * to the player lands lengthened the same, goo brew or vanilla potion.
 * extender-multiplies-the-next-self-duration
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ExtenderEvents {

    private ExtenderEvents() {
    }

    /**
     * Lengthens every timed effect standing on the player by the drunk pulse
     * brew's duration, the pulse brew's own aside.
     *
     * @param player the drinking player
     * @param extra  the brew's duration in ticks
     */
    public static void extendStanding(ServerPlayer player, int extra) {
        HeldEffects held = player.getData(GooAttachments.HELD_EFFECTS).extendPrepaid(extra);
        player.setData(GooAttachments.HELD_EFFECTS, held);
        for (MobEffectInstance standing : List.copyOf(player.getActiveEffects())) {
            if (lengthens(standing, held)) {
                lengthen(standing, extra);
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), standing, false));
            }
        }
    }

    /**
     * Lengthens a timed effect landing on a player while a drunk pulse brew
     * stands. It runs before the goo brew's own handler, so a goo brew drunk
     * under the extension starts prepaid for the lengthened time.
     *
     * @param event the effect added
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || HeldEffectsEvents.mirroring()
                || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        long extra = player.getData(GooAttachments.HELD_EFFECTS).extensionTicks();
        MobEffectInstance landing = event.getEffectInstance();
        if (extra > 0 && !landing.isInfiniteDuration()) {
            lengthen(landing, (int) extra);
        }
    }

    /**
     * Whether a standing effect lengthens: a timed one, and of the goo brews
     * only a prepaid one's clock; a glove effect's shown time follows its goo,
     * and the pulse brew's own clock is the extension.
     *
     * @param standing the effect standing
     * @param held     the player's held effects
     * @return true for an effect the drink lengthens
     */
    private static boolean lengthens(MobEffectInstance standing, HeldEffects held) {
        if (standing.isInfiniteDuration()) {
            return false;
        }
        if (!(standing.getEffect().value() instanceof GooBrewEffect brew)) {
            return true;
        }
        return brew.gooType() != GooTypes.PULSE && held.held().stream()
                .anyMatch(effect -> effect.prepaid() && effect.gooType() == brew.gooType());
    }

    private static void lengthen(MobEffectInstance effect, int extra) {
        effect.update(new MobEffectInstance(effect.getEffect(), effect.getDuration() + extra, effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon()));
    }
}
