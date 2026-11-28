package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ShieldStep;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import java.util.Optional;

/**
 * Charges Durable's Shield for what it does and bashes with it: each point
 * of damage it blocks costs durable goo, a block the goo cannot pay lands
 * whole, and a left click on the mob in front while it stands is a bash, paid
 * for, in place of the attack.
 * durable-goo-forms-a-shield-at-will-and-shield-bashes
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ShieldEvents {

    private ShieldEvents() {
    }

    /**
     * Charges a block the goo shield made, or lets the hit through where the goo falls short.
     *
     * @param event vanilla's block, as NeoForge posts it
     */
    @SubscribeEvent
    public static void chargeTheBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getBlocked()
                || ShieldHolds.blocksWithItsOwn(player)) {
            return;
        }
        ShieldHolds.raisedBy(player).ifPresent(shield -> {
            int cost = shield.blockCost(event.getBlockedDamage());
            if (pays(player, cost)) {
                event.setShieldDamage(0);
            } else {
                event.setBlocked(false);
            }
        });
    }

    /**
     * Bashes the mob a left click strikes while the goo shield stands, in place of the attack.
     *
     * @param event the attack
     */
    @SubscribeEvent
    public static void bashWithTheShield(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        Optional<ShieldStep> raised = ShieldHolds.raisedBy(player);
        if (raised.isPresent() && inFront(player, target) && pays(player, raised.get().bashCost())) {
            event.setCanceled(true);
            bash(player, target, raised.get());
        }
    }

    private static void bash(ServerPlayer player, LivingEntity target, ShieldStep shield) {
        ServerLevel level = player.level();
        DamageSource bash = level.damageSources().playerAttack(player);
        target.hurtServer(level, bash, shield.bashDamage());
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        target.knockback(shield.bashKnockback(), Mth.sin(yaw), -Mth.cos(yaw), bash, shield.bashDamage());
    }

    /**
     * Whether a target stands in front of the player, on the side the player faces.
     *
     * @param player the player
     * @param target the target
     * @return true where the target lies ahead
     */
    static boolean inFront(ServerPlayer player, LivingEntity target) {
        Vec3 toward = target.position().subtract(player.position());
        return toward.x * player.getLookAngle().x + toward.z * player.getLookAngle().z > 0;
    }

    private static boolean pays(ServerPlayer player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (!GooSourceScanner.hasEnough(player, GooTypes.DURABLE, cost)) {
            return false;
        }
        GooSourceScanner.deplete(player, GooTypes.DURABLE, cost);
        return true;
    }
}
