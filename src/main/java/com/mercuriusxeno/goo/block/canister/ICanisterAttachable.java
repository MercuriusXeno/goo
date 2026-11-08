package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Block entity that accepts canisters attached to its top face via copper
 * fittings. Canister placement validates the block below; blocks implementing
 * this interface are valid attachment targets.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // not a lambda target; sole abstract is a state query
public interface ICanisterAttachable {

    /** All slot indices in the 3x3 canister grid (0-8). */
    Set<Integer> ALL_SLOTS = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

    /** Returns the max number of canisters that can attach to the top face.
     * Default: 1 (single center slot).
     *
     * @return the integer value
     */
    default int maxTopAttachments() { return 1; }

    /** Returns the current number of canisters attached to the top face.
     *
     * @return the integer value
     */
    int currentTopAttachments();

    /** Returns true if another canister can be attached to the top face.
     *
     * @return true if attach on top
     */
    default boolean canAttachOnTop() {
        return currentTopAttachments() < maxTopAttachments();
    }

    /**
     * Returns the set of slot indices (0-8 in a 3x3 grid) that are allowed
     * for canister placement on this block's top face. Implementations may
     * constrain which positions are valid based on facing, upgrades, etc.
     *
     * <p>Default returns all 9 slots (no constraint).</p>
     *
     * @return the set
     */
    default Set<Integer> allowedSlots() {
        return ALL_SLOTS;
    }

    /**
     * Whether a slot of the canister block on top takes a goo, the machine's rule for
     * what may stand in each canister; any goo by default.
     *
     * @param slot     the canister block slot
     * @param incoming the goo type arriving, or null for a fluid that is not goo
     * @return true when the slot takes it
     */
    default boolean admitsGoo(int slot, @Nullable ResourceKey<GooTypeDefinition> incoming) {
        return true;
    }

    /**
     * The demand the machine states for a goo arriving in a slot of the canister block
     * on top, which that canister relays to its source (decision receivers-demand-and-links-relay);
     * none by default.
     *
     * @param slot     the canister block slot
     * @param incoming the goo type arriving, or null for a fluid that is not goo
     * @return the mB per tick the machine asks, or empty when it states none
     */
    default OptionalInt statedDemand(int slot, @Nullable ResourceKey<GooTypeDefinition> incoming) {
        return OptionalInt.empty();
    }
}
