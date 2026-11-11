package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * A projectile falling onto a slime coat's top bounces off it as any lander
 * does, where vanilla would stick an arrow in it or break a thrown item on it.
 * neutral-bounce-is-near-lossless-and-bounces-every-entity
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SlimeCoatProjectileBounce {

    private SlimeCoatProjectileBounce() {
    }

    /**
     * Cancels a falling projectile's impact on a slime coat's top and bounces it.
     *
     * @param event the impact
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (event.getRayTraceResult() instanceof BlockHitResult hit && hit.getDirection() == Direction.UP
                && projectile.getDeltaMovement().y < 0
                && projectile.level().getBlockState(hit.getBlockPos()).getBlock() instanceof SlimeCoatBlock) {
            event.setCanceled(true);
            SlimeCoatBounce.bounce(projectile);
        }
    }
}
