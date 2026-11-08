package com.mercuriusxeno.goo.item;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * What a held stack used on a plexer's cutaway targets, and what becomes of a ghost in hand.
 * decision ghost-item-targets-the-plexer-sneak-keeps-throw-deletes
 */
public final class GhostItemTargeting {

    private GhostItemTargeting() {
    }

    /**
     * Resolves a cutaway use: a ghost targets the item it stands for, any other stack targets itself.
     *
     * @param held        the id of the held item
     * @param represented the id the held ghost stands for, or null for a stack that is no ghost
     * @param sneaking    whether the player sneak-uses
     * @param refuses     whether the plexer refuses a target id
     * @return the target, whether it is refused and whether the hand empties
     */
    public static GhostUse resolve(Identifier held, @Nullable Identifier represented, boolean sneaking,
                                   Predicate<Identifier> refuses) {
        Identifier target = represented != null ? represented : held;
        boolean refused = refuses.test(target);
        // A refused target keeps the ghost, an assumption: the idea names no outcome for a fizzle.
        return new GhostUse(target, refused, represented != null && !refused && !sneaking);
    }

    /**
     * The outcome of a cutaway use.
     *
     * @param target      the id of the item the plexer targets
     * @param refused     whether the plexer refuses the target and fizzles
     * @param emptiesHand whether the held ghost is discarded
     */
    public record GhostUse(Identifier target, boolean refused, boolean emptiesHand) {
    }
}
