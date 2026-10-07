package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Runs a petrifying mob's gauge against time: each tick the fog no longer
 * fills it, the gauge drains back slowly, and the mob's speed follows the
 * gauge, slowing as it fills and recovering as it drains
 * (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PetrifyEvents {

    /** The movement modifier a petrifying mob wears, scaled by its gauge. */
    static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "petrify_slow");

    private PetrifyEvents() {
    }

    /**
     * Drains each petrifying mob's gauge and keeps its slow in step.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob)
                || !mob.hasData(GooAttachments.PETRIFICATION)) {
            return;
        }
        Petrification before = mob.getData(GooAttachments.PETRIFICATION);
        Petrification after = before.drained(mob.level().getGameTime());
        if (after != before) {
            mob.setData(GooAttachments.PETRIFICATION, after);
            slowBy(mob, after);
        }
    }

    /**
     * Slows a mob by its gauge's share: no slow at an empty gauge, a dead
     * stop at a full one.
     *
     * @param mob          the petrifying mob
     * @param petrification its gauge
     */
    public static void slowBy(Mob mob, Petrification petrification) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOW_ID);
        if (petrification.started()) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, -petrification.share(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
