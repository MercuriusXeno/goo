package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.RallyStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Which casts release the caster's own rallied mob, and so cost nothing: a
 * cast of an ability whose program rallies, at a mob whose standing rally
 * names the caster. Read alike by the server's throw pricing and the client's
 * send gate, as a function of the ability, the caster and the target, so the
 * pricing of any delivery can call it.
 * rally-release-cast-costs-nothing
 */
public final class RallyRelease {

    private RallyRelease() {
    }

    /**
     * Whether a cast is a free release of the caster's own rallied mob.
     *
     * @param program  the cast ability's steps
     * @param caster   the caster's UUID
     * @param standing the rally the target holds, {@link Rallied#NONE} for none
     * @return true for a rallying cast at a mob the caster rallied
     */
    public static boolean releasesOwnRally(List<Step> program, UUID caster, Rallied standing) {
        return rallies(program) && standing.releasedBy(caster);
    }

    /**
     * Whether a cast is a free release of the caster's own rallied mob, read
     * off the target's synced rally.
     *
     * @param program the cast ability's steps
     * @param caster  the caster
     * @param target  the entity the cast is aimed at, null for none
     * @return true for a rallying cast at a mob the caster rallied
     */
    public static boolean releasesOwnRally(List<Step> program, Entity caster, @Nullable Entity target) {
        Rallied standing = target != null && target.hasData(GooAttachments.RALLIED)
                ? target.getData(GooAttachments.RALLIED) : Rallied.NONE;
        return releasesOwnRally(program, caster.getUUID(), standing);
    }

    /**
     * Whether a program holds a rally step at any depth, Rally's own sitting
     * inside a target behavior.
     *
     * @param program the steps
     * @return true where a step rallies
     */
    static boolean rallies(List<Step> program) {
        return program.stream().anyMatch(step -> step instanceof RallyStep || rallies(step.children().toList()));
    }
}
