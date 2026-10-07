package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.List;
import java.util.Map;

/**
 * Holds a player's self + brew effects on the server: starts one when the
 * glove's eat finishes or a brew is drunk, ends one when the player invokes
 * it again, and each tick draws every glove effect's upkeep from the
 * inventory, ending one the inventory can no longer pay and a brew's at its
 * expiry. Ending an effect clears the state its program laid, and a brew's
 * effect instance with it.
 * self-effects-trickle-until-ended
 * brew-runs-the-crawl-prepaid-on-a-shown-clock
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HeldEffectsEvents {

    private HeldEffectsEvents() {
    }

    /**
     * Starts a held effect for an ability, ending any heart-changing effect a
     * heart-changing one replaces. The caller runs the program after, so the
     * state it lays outlives the clearing of what it replaced.
     * one-heart-overlay-at-a-time
     *
     * @param player  the player
     * @param gooType the goo type its upkeep draws
     * @param ability the self + brew ability
     */
    public static void start(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        HeldEffects.Held started = new HeldEffects.Held(ability.id(), gooType, ability.upkeep(),
                LaidState.laidBy(ability.behaviors()), player.level().getGameTime(), HeldEffects.NEVER_EXPIRES,
                downSoundOf(ability));
        apply(player, player.getData(GooAttachments.HELD_EFFECTS).start(started));
    }

    /**
     * Starts a drunk brew's effect prepaid: the same held effect the glove
     * starts, paying no upkeep and ending at the brew's expiry. The caller
     * runs the program after, as for the glove.
     * brew-runs-the-crawl-prepaid-on-a-shown-clock
     *
     * @param player   the drinking player
     * @param gooType  the brew's goo type
     * @param ability  the type's brew ability
     * @param duration the brew's duration in ticks
     */
    public static void startPrepaid(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                    AbilityDefinition ability, int duration) {
        long now = player.level().getGameTime();
        HeldEffects.Held started = new HeldEffects.Held(ability.id(), gooType, ability.upkeep(),
                LaidState.laidBy(ability.behaviors()), now, now + duration, downSoundOf(ability));
        apply(player, player.getData(GooAttachments.HELD_EFFECTS).start(started));
    }

    /**
     * Ends a held effect deliberately, clearing the state it laid.
     *
     * @param player  the player
     * @param ability the ability to end
     */
    public static void end(ServerPlayer player, Identifier ability) {
        apply(player, player.getData(GooAttachments.HELD_EFFECTS).end(ability));
    }

    /**
     * Answers whether a player holds an ability.
     *
     * @param player  the player
     * @param ability the ability
     * @return true while the ability stands on the player
     */
    public static boolean holds(ServerPlayer player, Identifier ability) {
        return player.hasData(GooAttachments.HELD_EFFECTS) && player.getData(GooAttachments.HELD_EFFECTS).holds(ability);
    }

    /**
     * Draws each held effect's upkeep on the server, ending one the
     * inventory can no longer pay. A heart effect whose shields are all
     * broken stays held, its crawl regrowing them.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        HeldEffects held = player.getData(GooAttachments.HELD_EFFECTS);
        if (held.isEmpty()) {
            return;
        }
        HeldEffects.Ticked ticked = held.tick(type -> GooSourceScanner.aggregateAvailable(player)
                .getOrDefault(type, 0), player.level().getGameTime());
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> draw : ticked.drawn().entrySet()) {
            GooSourceScanner.deplete(player, draw.getKey(), draw.getValue());
        }
        apply(player, new HeldEffects.Changed(ticked.after(), ticked.ended()));
    }

    /**
     * Stores the effects a change leaves and clears what each ended effect laid.
     *
     * @param player  the player
     * @param changed the change
     * @return the effects standing after it
     */
    private static HeldEffects apply(ServerPlayer player, HeldEffects.Changed changed) {
        if (player.getData(GooAttachments.HELD_EFFECTS) != changed.after()) {
            player.setData(GooAttachments.HELD_EFFECTS, changed.after());
        }
        clearLaid(player, changed.ended());
        HeldEffects.soundEnds(changed.ended(), new PlayerHost(player.level(), player)::playSound);
        return changed.after();
    }

    /**
     * The cue an ability's held effect plays when it ends: its own where its
     * JSON names one, the shared ability-down cue otherwise.
     * held-effects-sound-up-and-down
     *
     * @param ability the held ability
     * @return the cue its end plays
     */
    private static SoundCue downSoundOf(AbilityDefinition ability) {
        return ability.downSound().orElse(HeldEffects.Held.SHARED_DOWN_SOUND);
    }

    private static void clearLaid(ServerPlayer player, List<HeldEffects.Held> ended) {
        for (HeldEffects.Held effect : ended) {
            if (effect.lays().contains(LaidState.HEART_OVERLAY)) {
                player.setData(GooAttachments.HEART_OVERLAY, HeartOverlay.NONE);
            }
            if (effect.lays().contains(LaidState.NOURISH)) {
                player.setData(GooAttachments.NOURISH, Nourish.NONE);
            }
            if (effect.prepaid()) {
                // brew-runs-the-crawl-prepaid-on-a-shown-clock: the brew's effect icon ends with its effect
                player.removeEffect(GooMobEffects.BREW_EFFECTS.get(effect.gooType()));
            }
        }
    }
}
