package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Runs a frozen mob against time: its gauge thaws each tick once any hold at
 * full runs out, its speed follows the gauge, a physical hit lands harder in
 * proportion to it, and while the gauge stands full the mob has no AI and
 * no motion of its own.
 * frozen-gauge-per-mob-encases-when-full
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class FrozenEvents {

    /** The movement modifier a frozen mob wears, scaled by its gauge. */
    static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "frozen_slow");

    private FrozenEvents() {
    }

    /**
     * Thaws each frozen mob's gauge and keeps its slow and its stillness in step.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob) || !mob.hasData(GooAttachments.FROZEN)) {
            return;
        }
        Frozen before = mob.getData(GooAttachments.FROZEN);
        Frozen after = heldByGlacial(mob) ? before : before.thawed(mob.level().getGameTime());
        if (after != before) {
            settle(mob, before, after);
        }
        holdStill(mob, after);
    }

    /**
     * Stops a fully frozen mob's own motion, leaving it only to fall.
     *
     * @param mob    the mob
     * @param frozen its gauge this tick
     */
    private static void holdStill(Mob mob, Frozen frozen) {
        if (frozen.full()) {
            Vec3 motion = mob.getDeltaMovement();
            mob.setDeltaMovement(0, Math.min(0, motion.y), 0);
        }
    }

    /**
     * Whether a standing glacial prism holds the mob's gauge from thawing
     * (decision glacial-prism-holds-the-area-frozen).
     *
     * @param mob the frozen mob
     * @return true inside a glacial prism's field
     */
    private static boolean heldByGlacial(Mob mob) {
        return mob.level() instanceof ServerLevel level && GooServerState.of(level.getServer()).glacialFields()
                .holds(level.dimension(), mob.position(), level.getGameTime());
    }

    /**
     * Raises a physical hit on a frozen mob in proportion to its gauge.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !mob.hasData(GooAttachments.FROZEN)
                || !isPhysical(event.getSource())) {
            return;
        }
        event.setAmount(event.getAmount() * mob.getData(GooAttachments.FROZEN).physicalDamageMultiplier());
    }

    /**
     * Adds the flat physical bonus to a hit on a frozen mob once its armor
     * has taken its share: the bonus lands whole from the first frost, so a
     * punch on a frozen mob compares with a weapon.
     * nova-ring-grows-with-the-hold
     *
     * @param event the damage event, after armor
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && mob.hasData(GooAttachments.FROZEN)) {
            event.setNewDamage(withPhysicalBonus(mob.getData(GooAttachments.FROZEN), event.getSource(),
                    event.getNewDamage()));
        }
    }

    /**
     * A hit's damage after armor with the frozen mob's flat physical bonus
     * added: whole while any frost stands, nothing on a thawed mob or a hit
     * that is not physical.
     *
     * @param frozen the mob's gauge
     * @param source the damage source
     * @param damage the damage after armor
     * @return the damage the mob takes
     */
    static float withPhysicalBonus(Frozen frozen, DamageSource source, float damage) {
        return frozen.started() && isPhysical(source) ? damage + frozen.curve().physicalBonus() : damage;
    }

    /**
     * Freezes a mob by a frost hit: its gauge rises by the amount over its
     * max health, and its slow and stillness follow.
     *
     * @param mob    the struck mob
     * @param amount the hit's amount, in health points
     * @param curve  the hit's curve
     */
    public static void freeze(Mob mob, float amount, FrostCurve curve) {
        Frozen before = mob.getData(GooAttachments.FROZEN);
        float share = Frozen.shareOf(amount, mob.getMaxHealth());
        settle(mob, before, before.add(share, curve, mob.level().getGameTime()));
    }

    /**
     * Answers whether a hit is physical: one armor checks, as Stoneskin reads it.
     *
     * @param source the damage source
     * @return true unless the hit bypasses armor
     */
    static boolean isPhysical(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_ARMOR);
    }

    /**
     * Stores a mob's new gauge and brings its slow and its AI in line: the AI
     * goes off as the gauge fills and comes back as it thaws below full.
     *
     * @param mob    the frozen mob
     * @param before its gauge before the change
     * @param after  its gauge after
     */
    public static void settle(Mob mob, Frozen before, Frozen after) {
        mob.setData(GooAttachments.FROZEN, after);
        slowBy(mob, after);
        if (after.full() != before.full()) {
            mob.setNoAi(after.full());
        }
    }

    private static void slowBy(Mob mob, Frozen frozen) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOW_ID);
        if (frozen.started()) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, -frozen.gauge(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
