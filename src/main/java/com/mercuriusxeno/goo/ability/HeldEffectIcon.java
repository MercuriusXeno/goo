package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The texture a goo type's held effect draws on the HUD and in the inventory: the icon of
 * the durative ability the type grants, the same ability its name reads, picked from the
 * running server's abilities, else the client connection's synced ones. A type granting
 * none answers no texture, so the effect draws its own tile.
 * decision every-brew-effect-draws-its-abilitys-icon
 */
public final class HeldEffectIcon {

    /**
     * Reads the icon texture of the durative ability a type grants on the client connection;
     * the client setup installs it once and a dedicated server leaves it answering none.
     */
    private static final AtomicReference<Function<ResourceKey<GooTypeDefinition>, @Nullable Identifier>>
            CONNECTION_ICONS = new AtomicReference<>(type -> null);

    private HeldEffectIcon() {
    }

    /**
     * The icon texture a type's held effect draws on this side.
     *
     * @param type the goo type
     * @return the granted ability's icon texture, or null for a type granting none
     */
    public static @Nullable Identifier of(ResourceKey<GooTypeDefinition> type) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return textureOf(AbilityRegistry.of(server).durativeAbilityFor(type));
        }
        return CONNECTION_ICONS.get().apply(type);
    }

    /**
     * The icon texture of a granted durative ability, as the radial resolves it.
     *
     * @param granted the durative ability a type grants, null for none
     * @return the ability's icon texture, or null for none
     */
    public static @Nullable Identifier textureOf(@Nullable AbilityDefinition granted) {
        return granted != null ? AbilityIconTexture.of(granted.id(), granted.icon()) : null;
    }

    /**
     * Installs the reader of the client connection's durative ability icons, from the client setup.
     *
     * @param connectionIcons answers a type's durative ability icon texture, or null for none synced
     */
    public static void readConnectionIconsFrom(
            Function<ResourceKey<GooTypeDefinition>, @Nullable Identifier> connectionIcons) {
        CONNECTION_ICONS.set(connectionIcons);
    }
}
