package com.mercuriusxeno.goo.block.ability;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Lands the server's falling layers in their levels, through the goo cell each joins.
 * falling-layer-lands-when-its-drip-arrives
 *
 * @param server the server the layers fall on
 */
public record ServerFallLanding(MinecraftServer server) implements LayerFalls.Landing {

    @Override
    public boolean land(LayerFalls.Fall fall) {
        ServerLevel level = server.getLevel(fall.dimension());
        return level != null && fall.cell().landFall(level, fall.start());
    }

    @Override
    public void landNow(LayerFalls.Fall fall) {
        ServerLevel level = server.getLevel(fall.dimension());
        if (level != null) {
            fall.cell().landFallNow(level, fall.start());
        }
    }
}
