package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.StitchStep;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Runs Stitch on the mobs in it: a passive passes through the goo as if it
 * were not there; a hostile is tangled in its strands, slowed as a cobweb
 * slows, fire on it lands at the multiple Vines' root gives unless it is
 * fire immune, and fire ends the tangle.
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class StitchEvents {

    /** The share of its motion a tangled mob keeps each tick, a cobweb's. */
    private static final Vec3 COBWEB_SLOW = new Vec3(0.25, 0.05F, 0.25);

    private StitchEvents() {
    }

    /**
     * Tangles a hostile inside a Stitch cell, on the server, in strands that
     * slow it as a cobweb does; a passive, and a hostile whose strands fire
     * has ended, moves on untouched.
     *
     * @param entity the thing inside the cell
     * @param cell   the cell's state
     * @param stitch the cell's laying step
     */
    public static void tangle(Entity entity, BlockState cell, StitchStep stitch) {
        if (entity.level().isClientSide() || !(entity instanceof LivingEntity living)
                || StitchPassage.of(living.getType().getCategory(), living instanceof Enemy) == StitchPassage.PASS) {
            return;
        }
        long now = living.level().getGameTime();
        Tangled standing = living.getData(GooAttachments.TANGLED);
        Tangled after = standing.touched(stitch.fireFactor(), now);
        if (after != standing) {
            living.setData(GooAttachments.TANGLED, after);
        }
        if (after.holds(now)) {
            living.makeStuckInBlock(cell, COBWEB_SLOW);
        }
    }

    /**
     * Multiplies fire on a tangled mob that is not fire immune, and lets the
     * fire end its tangle.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide() || !living.hasData(GooAttachments.TANGLED)) {
            return;
        }
        Tangled tangled = living.getData(GooAttachments.TANGLED);
        long now = living.level().getGameTime();
        boolean fire = event.getSource().is(DamageTypeTags.IS_FIRE);
        if (!tangled.holds(now)) {
            return;
        }
        event.setAmount(tangled.damageAt(now, event.getAmount(), fire, living.fireImmune()));
        if (fire) {
            living.setData(GooAttachments.TANGLED, tangled.burn());
        }
    }
}
