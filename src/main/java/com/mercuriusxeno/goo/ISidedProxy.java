package com.mercuriusxeno.goo;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Dist-safe proxy for operations that differ between client and server.
 * The client implementation is installed by {@link com.mercuriusxeno.goo.client.GooClientSetup}
 * (Dist.CLIENT only). Common-side code calls {@link #get()} and never
 * references client classes directly.
 */
public interface ISidedProxy {

    /** No-op server proxy. All methods return safe defaults. */
    ISidedProxy SERVER = new ISidedProxy() {};

    /** The active proxy instance. Client on client dist, SERVER on server. */
    ISidedProxy[] INSTANCE = { SERVER };

    /**
     * Returns the active proxy.
     *
     * @return the active proxy instance
     */
    static ISidedProxy get() {
        return INSTANCE[0];
    }

    /**
     * Returns the current crosshair hit result, or null.
     * Server: always null.
     *
     * @return the crosshair hit result, or null on server
     */
    default @Nullable HitResult getCrosshairHit() {
        return null;
    }

    /**
     * Starts a glove press on the client's input gate, which throws on a
     * short release and opens the radial on a hold. Server: does nothing,
     * since only the client aims (decision client-handlers-under-client-network).
     *
     * @param hand the hand holding the glove
     */
    default void pressGlove(InteractionHand hand) {
    }

    /**
     * Whether an ability the client holds synced takes the eat route, which
     * the glove reads for its use animation where no level is at hand.
     * Server: false, since the server reads the ability registry through
     * its level.
     * decision self-brew-goos-eat-before-the-effect
     *
     * @param abilityId the ability resource id string
     * @return true for a synced ability wearing the brew badge on a self delivery
     */
    default boolean syncedAbilityEats(String abilityId) {
        return false;
    }
}
