package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.frost.Frozen;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Answers whether a goo hold holds a mob, and gates the actions a mob takes
 * on its own outside the AI that turning its AI off stops, such as a
 * witch's potion drinking.
 * diagnose-and-correct-held-mobs-still-acting
 */
public final class HeldMobs {

    private HeldMobs() {
    }

    /**
     * Whether a goo hold holds the mob: frozen full, stunned, grabbed,
     * rewinding, or its AI set off. Each hold's release puts back the AI it
     * found, so where two holds overlap the AI can read on while one still
     * stands, and each hold is read by its own mark.
     *
     * @param mob the mob
     * @return true while any goo hold holds it
     */
    public static boolean isHeld(Mob mob) {
        Frozen frozen = mob.hasData(GooAttachments.FROZEN) ? mob.getData(GooAttachments.FROZEN) : null;
        return held(mob.isNoAi(), frozen, mob.hasData(GooAttachments.STUNNED), mob.hasData(GooAttachments.GRABBED),
                mob.hasData(GooAttachments.REWINDING));
    }

    /**
     * Whether a mob carrying these marks is held.
     *
     * @param aiOff     the mob's AI is off, as set_ai and every hold leave it
     * @param frozen    its frozen gauge, or null where frost never reached it
     * @param stunned   it carries a stun
     * @param grabbed   a Grab holds it
     * @param rewinding a Rewind holds it
     * @return true where any hold stands
     */
    static boolean held(boolean aiOff, @Nullable Frozen frozen, boolean stunned, boolean grabbed, boolean rewinding) {
        boolean frozenFull = frozen != null && frozen.full();
        return aiOff || frozenFull || anyMarked(stunned, grabbed, rewinding);
    }

    private static boolean anyMarked(boolean stunned, boolean grabbed, boolean rewinding) {
        return stunned || grabbed || rewinding;
    }

    /**
     * Whether a mob's own action runs this tick: what vanilla answers, unless
     * a goo hold holds the mob.
     *
     * @param vanillaRuns whether vanilla would run the action
     * @param held        whether a goo hold holds the mob
     * @return true where the action runs
     */
    public static boolean ownActionRuns(boolean vanillaRuns, boolean held) {
        return vanillaRuns && !held;
    }
}
