package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.hex.Lifetap;
import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
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
 * expiry. Ending an effect clears the state its program laid, and its goo
 * type's brew effect with it. While a glove effect stands, the type's brew
 * effect stands too, its time the ticks the player's goo pays for, so the
 * effect list shows the effect and how long it has left.
 * self-effects-trickle-until-ended
 * brew-runs-the-crawl-prepaid-on-a-shown-clock
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HeldEffectsEvents {

    /** A glove effect's time resyncs only when it drifts past a second from the goo's, as goo is spent or gained. */
    static final int RESYNC_TICKS = 20;
    /** The longest a glove effect shows its time for, 999 hours, before it shows as endless. */
    static final int LONGEST_SHOWN_TICKS = 999 * 60 * 60 * 20;
    private static final int NO_AMPLIFIER = 0;
    private static final boolean NOT_AMBIENT = false;
    private static final boolean NO_PARTICLES = false;
    private static final boolean SHOWS_ICON = true;
    /** True while a glove effect's time is being laid, so the brew it lays is not taken for a drink. */
    private static boolean mirroring;

    private HeldEffectsEvents() {
    }

    /**
     * Answers whether a brew effect landing now is a glove effect's time,
     * laid here, rather than a brew drunk.
     *
     * @return true while a glove effect's time is being laid
     */
    public static boolean mirroring() {
        return mirroring;
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
        showTimeLeft(player, player.getData(GooAttachments.HELD_EFFECTS));
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
        HeldEffects after = apply(player, new HeldEffects.Changed(ticked.after(), ticked.ended()));
        showTimeLeft(player, after);
    }

    /**
     * Lays each glove effect's goo type's brew effect for the ticks the
     * player's goo pays for, with its icon and no particles, so the effect
     * list shows it and its time left. The time counts down on its own as the
     * upkeep drains, and is laid again when spent or gained goo moves it more
     * than a second.
     * brew-runs-the-crawl-prepaid-on-a-shown-clock
     *
     * @param player the player
     * @param held   the effects standing
     */
    private static void showTimeLeft(ServerPlayer player, HeldEffects held) {
        Map<ResourceKey<GooTypeDefinition>, Integer> left = held.ticksLeft(type ->
                GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0));
        left.forEach((type, ticks) -> {
            Holder<MobEffect> brew = GooMobEffects.BREW_EFFECTS.get(type);
            MobEffectInstance standing = player.getEffect(brew);
            int shown = shownDuration(ticks);
            if (standing != null && (standing.getDuration() == shown
                    || shown != MobEffectInstance.INFINITE_DURATION
                    && Math.abs(standing.getDuration() - shown) <= RESYNC_TICKS)) {
                return;
            }
            mirroring = true;
            try {
                player.removeEffect(brew);
                player.addEffect(new MobEffectInstance(brew, shown, NO_AMPLIFIER, NOT_AMBIENT, NO_PARTICLES,
                        SHOWS_ICON));
            } finally {
                mirroring = false;
            }
        });
    }

    /**
     * The duration a glove effect shows for the ticks its goo pays for:
     * those ticks, or vanilla's infinite duration, shown as an infinity sign,
     * once the goo would last past 999 hours, so a vast store reads as
     * endless rather than an absurd figure. What drains is unchanged.
     *
     * @param ticks the ticks the player's goo pays for
     * @return the duration the effect is laid with
     */
    static int shownDuration(int ticks) {
        return ticks > LONGEST_SHOWN_TICKS ? MobEffectInstance.INFINITE_DURATION : ticks;
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
            clearAbilityLaid(player, effect);
            // brew-runs-the-crawl-prepaid-on-a-shown-clock: the effect list's entry ends with the effect
            player.removeEffect(GooMobEffects.BREW_EFFECTS.get(effect.gooType()));
        }
    }

    /**
     * Clears the state an ability's own step laid: sight, Lux, a lifetap.
     *
     * @param player the player whose held effect ended
     * @param effect the ended held effect
     */
    private static void clearAbilityLaid(ServerPlayer player, HeldEffects.Held effect) {
        if (effect.lays().contains(LaidState.SIGHT)) {
            // sight-lengthens-shift-and-outlines-fungus: the sight ends with its held effect
            player.setData(GooAttachments.SIGHT, Sight.NONE);
        }
        if (effect.lays().contains(LaidState.LUX)) {
            // lux-night-vision-without-particles: Lux and the night vision it kept up end with its held effect
            LuxEvents.end(player);
        }
        if (effect.lays().contains(LaidState.LIFETAP)) {
            // lifetap-trades-regen-for-leech: the leech ends with its held effect
            player.setData(GooAttachments.LIFETAP, Lifetap.NONE);
        }
    }
}
