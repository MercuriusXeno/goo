package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The lang key a goo type's held effect reads its name from: the displayName
 * of the durative ability the type grants, read from the running server's
 * abilities, else the client connection's synced ones. A type granting none
 * reads its own effect key, which en_us values.
 * decision brew-effect-reads-the-abilitys-own-name
 */
public final class HeldEffectName {

    private static final String EFFECT_PREFIX = "effect." + Goo.MODID + ".";

    /**
     * Reads the displayName key of the durative ability a type grants on the client
     * connection; the client setup installs it once and a dedicated server
     * leaves it answering none.
     */
    private static final AtomicReference<Function<ResourceKey<GooTypeDefinition>, @Nullable String>>
            CONNECTION_NAMES = new AtomicReference<>(type -> null);

    private HeldEffectName() {
    }

    /**
     * The name key a type's held effect reads on this side.
     *
     * @param type the goo type
     * @return the lang key
     */
    public static String of(ResourceKey<GooTypeDefinition> type) {
        return nameKeyOf(type, grantedAbilityNameKey(type));
    }

    /**
     * The name key of a type's held effect.
     *
     * @param type           the goo type
     * @param abilityNameKey the displayName key of the durative ability the type grants, null for none
     * @return the ability's key, or the type's own effect key for a type granting none
     */
    public static String nameKeyOf(ResourceKey<GooTypeDefinition> type, @Nullable String abilityNameKey) {
        return abilityNameKey != null ? abilityNameKey : EFFECT_PREFIX + GooMobEffects.heldEffectName(type);
    }

    /**
     * Installs the reader of the client connection's durative ability names, from the client setup.
     *
     * @param connectionNames answers a type's durative ability displayName key, or null for none synced
     */
    public static void readConnectionNamesFrom(
            Function<ResourceKey<GooTypeDefinition>, @Nullable String> connectionNames) {
        CONNECTION_NAMES.set(connectionNames);
    }

    private static @Nullable String grantedAbilityNameKey(ResourceKey<GooTypeDefinition> type) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            AbilityDefinition granted = AbilityRegistry.of(server).durativeAbilityFor(type);
            return granted != null ? granted.displayName() : null;
        }
        return CONNECTION_NAMES.get().apply(type);
    }
}
