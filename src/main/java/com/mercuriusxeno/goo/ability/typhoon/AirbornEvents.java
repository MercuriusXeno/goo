package com.mercuriusxeno.goo.ability.typhoon;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The server's half of Airborn, each player tick: while it stands the player
 * jumps higher and no fall it builds up hurts it; once it ends the jump falls
 * back. The steering and the capped fall run on the player's own client,
 * which moves the player.
 * airborn-steerable-levitation-and-soft-falls
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class AirbornEvents {

    /** The jump strength modifier Airborn adds. */
    public static final Identifier JUMP_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "airborn_jump");

    private AirbornEvents() {
    }

    /**
     * Holds Airborn's jump and soft falls on a server player while it stands.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Airborn airborn = player.getData(GooAttachments.AIRBORN);
            boolean stands = airborn.standsAt(player.level().getGameTime());
            holdJump(player, stands ? airborn.jumpBoost() : 0f);
            if (stands) {
                player.resetFallDistance();
            }
        }
    }

    private static void holdJump(ServerPlayer player, float boost) {
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null) {
            return;
        }
        AttributeModifier standing = jump.getModifier(JUMP_ID);
        if (boost <= 0f) {
            jump.removeModifier(JUMP_ID);
        } else if (standing == null || standing.amount() != boost) {
            jump.removeModifier(JUMP_ID);
            jump.addTransientModifier(new AttributeModifier(JUMP_ID, boost,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
