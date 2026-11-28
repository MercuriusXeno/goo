package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BladeStep;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Osseous's bone shotel on the server: it latches in the casting hand with the bone block's place sound and every
 * watcher draws it; a cast from the hand while it stands is refused, the press dissolving the shotel with the bone
 * block's break sound; each strike the caster lands with it drains the shotel's Osseous and deals its damage in
 * place of the bare hit, and a strike finding too little Osseous dissolves it, landing as the bare hit.
 * decision blade-forms-a-bone-shotel-in-hand
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class BoneBladeEvents {

    private static final float SOUND_VOLUME = 0.8f;
    private static final float FORM_PITCH = 1.0f;
    private static final float DISSOLVE_PITCH = 0.8f;

    private BoneBladeEvents() {
    }

    /**
     * Latches a shotel in the player's casting hand and tells the players watching.
     *
     * @param player the casting player
     * @param step   the Blade step laying it
     */
    public static void latchOn(ServerPlayer player, BladeStep step) {
        boolean rightHand = GooCasters.casterOnTheRight(player);
        bladesOf(player).latch(player.getUUID(), BoneBlades.Blade.of(step, rightHand));
        EntityVisuals.sendToWatchers(player, new BoneBladePayload(player.getId(), true, rightHand));
        sound(player, SoundEvents.BONE_BLOCK_PLACE, FORM_PITCH);
    }

    /**
     * Refuses a cast from a hand holding a shotel, the press dissolving it.
     *
     * @param player the casting player
     * @return true where the cast is refused
     */
    static boolean refusesCast(ServerPlayer player) {
        if (!bladesOf(player).refusesCast(player.getUUID())) {
            return false;
        }
        dissolveFrom(player);
        return true;
    }

    /**
     * Lands a strike made with the shotel: its drain is charged and its damage stands for the hit's, or, short of
     * the drain, the shotel dissolves and the hit lands bare.
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
                || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        bladesOf(player).bladeOf(player.getUUID()).ifPresent(blade -> {
            if (AbilityCharges.charge(player, GooTypes.OSSEOUS, GooFraction.of(blade.drain()))) {
                event.setAmount(blade.damage());
            } else {
                dissolveFrom(player);
            }
        });
    }

    /**
     * Dissolves the shotel of a player logging out.
     *
     * @param event the logout
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().getServer() != null) {
            bladesOf(player).dissolve(player.getUUID());
        }
    }

    // Dissolves the player's shotel, telling the players watching.
    private static void dissolveFrom(ServerPlayer player) {
        if (bladesOf(player).dissolve(player.getUUID())) {
            EntityVisuals.sendToWatchers(player, new BoneBladePayload(player.getId(), false,
                    GooCasters.casterOnTheRight(player)));
            sound(player, SoundEvents.BONE_BLOCK_BREAK, DISSOLVE_PITCH);
        }
    }

    private static void sound(ServerPlayer player, SoundEvent event, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS,
                SOUND_VOLUME, pitch);
    }

    private static BoneBlades bladesOf(ServerPlayer player) {
        return GooServerState.of(player.level().getServer()).boneBlades();
    }
}
