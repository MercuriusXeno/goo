package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * gauge, slowing as it fills and recovering as it drains; while the fog
 * fills it, the stone crackles as it spreads
 * (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PetrifyEvents {

    /** The movement modifier a petrifying mob wears, scaled by its gauge. */
    static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "petrify_slow");
    /** Ticks between one crackle and the next while the fog fills a mob. */
    static final long CRACKLE_INTERVAL_TICKS = 6L;
    private static final float CRACKLE_VOLUME = 0.7f;
    /** The crackle's pitch as the stone starts, and how far it rises by the time the stone closes over. */
    private static final float CRACKLE_LOW_PITCH = 0.5f;
    private static final float CRACKLE_PITCH_RISE = 0.6f;

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
     * Crackles as stone spreads over a petrifying mob: a low crack every few
     * ticks the fog fills it, rising in pitch as the stone closes over it.
     *
     * @param level         the server level
     * @param mob           the petrifying mob
     * @param petrification its gauge after this tick's fill
     */
    public static void crackle(ServerLevel level, Mob mob, Petrification petrification) {
        if (cracklesOn(level.getGameTime())) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TURTLE_EGG_CRACK,
                    SoundSource.HOSTILE, CRACKLE_VOLUME, CRACKLE_LOW_PITCH + petrification.share() * CRACKLE_PITCH_RISE);
        }
    }

    /**
     * Whether a tick of fill crackles.
     *
     * @param gameTime the game time
     * @return true once every crackle interval
     */
    static boolean cracklesOn(long gameTime) {
        return gameTime % CRACKLE_INTERVAL_TICKS == 0;
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
