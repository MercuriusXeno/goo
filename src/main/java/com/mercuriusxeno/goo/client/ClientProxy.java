package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Client-side proxy implementation. References {@link Minecraft} directly;
 * only classloaded on the client dist.
 */
public final class ClientProxy implements ISidedProxy {

    @Override
    public @Nullable HitResult getCrosshairHit() {
        return Minecraft.getInstance().hitResult;
    }

    @Override
    public void pressGlove(InteractionHand hand) {
        GloveUseTracker.pressGlove(hand);
    }

    @Override
    public boolean syncedAbilityEats(String abilityId) {
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability != null && SelfEatRoute.eats(ability.delivery(), ability.badge());
    }
}
