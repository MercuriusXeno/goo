package com.mercuriusxeno.goo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The dimension the local player stands in, which an ability's shown name and icon read.
 * decision lunar-reads-as-terran-while-you-are-on-the-moon
 */
public final class ClientDimension {

    private ClientDimension() {
    }

    /**
     * The dimension the local player stands in.
     *
     * @return the client level's dimension, the overworld before the client or a level loads
     */
    public static ResourceKey<Level> standingIn() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client != null ? client.level : null;
        return level != null ? level.dimension() : Level.OVERWORLD;
    }
}
