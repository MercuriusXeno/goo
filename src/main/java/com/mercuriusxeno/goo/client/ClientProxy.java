package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
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
    public void sendGloveThrow(Player player, ResourceKey<GooTypeDefinition> gooType) {
        GloveThrowSender.sendThrow(player, gooType);
    }
}
