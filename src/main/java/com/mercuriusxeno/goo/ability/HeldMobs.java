package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.frost.Frozen;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Answers whether a goo hold holds a mob, gates the actions a mob takes on
 * its own outside the AI that turning its AI off stops, such as a witch's
 * potion drinking, and keeps a held mob falling: vanilla moves a mob only
 * while its AI is on, and every hold turns it off.
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
        return mob.isNoAi() || isHeldByAHold(mob);
    }

    /**
     * Whether a hold's own mark holds the mob: frozen full, stunned, grabbed
     * or rewinding. A mob whose AI is set off and nothing more, by set_ai or
     * a vanilla NoAI tag, is left out, so it floats as vanilla has it.
     *
     * @param mob the mob
     * @return true while a hold's mark stands on it
     */
    public static boolean isHeldByAHold(Mob mob) {
        Frozen frozen = mob.hasData(GooAttachments.FROZEN) ? mob.getData(GooAttachments.FROZEN) : null;
        return heldByAHold(frozen, mob.hasData(GooAttachments.STUNNED), mob.hasData(GooAttachments.GRABBED),
                mob.hasData(GooAttachments.REWINDING))
                // rallied-mob-falls-downed-instead-of-dying: a downed mob's AI is off, and it still falls
                || mob.hasData(GooAttachments.DOWNED);
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
        return aiOff || heldByAHold(frozen, stunned, grabbed, rewinding);
    }

    /**
     * Whether a hold's mark stands among these.
     *
     * @param frozen    the mob's frozen gauge, or null where frost never reached it
     * @param stunned   it carries a stun
     * @param grabbed   a Grab holds it
     * @param rewinding a Rewind holds it
     * @return true where a hold's mark stands
     */
    static boolean heldByAHold(@Nullable Frozen frozen, boolean stunned, boolean grabbed, boolean rewinding) {
        boolean frozenFull = frozen != null && frozen.full();
        return frozenFull || stunned || grabbed || rewinding;
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

    /**
     * Whether a mob's travel, the step that moves it and pulls it down, runs
     * this tick: what vanilla answers, and for a mob a hold's mark holds as
     * well, so the hold leaves it falling.
     *
     * @param vanillaRuns  whether vanilla would run the travel
     * @param heldByAHold  whether a hold's mark holds the mob
     * @return true where the travel runs
     */
    public static boolean travelRuns(boolean vanillaRuns, boolean heldByAHold) {
        return vanillaRuns || heldByAHold;
    }

    /**
     * The input a mob travels by: none for a mob a hold's mark holds, so it
     * falls and no stale walk carries it sideways.
     *
     * @param input       the input vanilla hands the travel
     * @param heldByAHold whether a hold's mark holds the mob
     * @return the input the travel runs on
     */
    public static Vec3 travelInput(Vec3 input, boolean heldByAHold) {
        return heldByAHold ? Vec3.ZERO : input;
    }
}
