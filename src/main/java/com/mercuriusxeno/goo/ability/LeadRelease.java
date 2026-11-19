package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.LeadStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Which casts release the caster's own led mob, and so cost nothing: a
 * cast of an ability whose program leads, at a mob whose standing lead
 * names the caster. Read alike by the server's throw pricing and the client's
 * send gate, as a function of the ability, the caster and the target, so the
 * pricing of any delivery can call it.
 * rally-release-cast-costs-nothing
 */
public final class LeadRelease {

    private LeadRelease() {
    }

    /**
     * Whether a cast is a free release of the caster's own led mob.
     *
     * @param program  the cast ability's steps
     * @param caster   the caster's UUID
     * @param standing the lead the target holds, {@link Led#NONE} for none
     * @return true for a leading cast at a mob the caster led
     */
    public static boolean releasesOwnLead(List<Step> program, UUID caster, Led standing) {
        return leads(program) && standing.releasedBy(caster);
    }

    /**
     * Whether a cast is a free release of the caster's own led mob, read
     * off the target's synced lead.
     *
     * @param program the cast ability's steps
     * @param caster  the caster
     * @param target  the entity the cast is aimed at, null for none
     * @return true for a leading cast at a mob the caster led
     */
    public static boolean releasesOwnLead(List<Step> program, Entity caster, @Nullable Entity target) {
        Led standing = target != null && target.hasData(GooAttachments.LED)
                ? target.getData(GooAttachments.LED) : Led.NONE;
        return releasesOwnLead(program, caster.getUUID(), standing);
    }

    /**
     * Whether a program holds a lead step at any depth, Lead's own sitting
     * inside a target behavior.
     *
     * @param program the steps
     * @return true where a step leads
     */
    static boolean leads(List<Step> program) {
        return program.stream().anyMatch(step -> step instanceof LeadStep || leads(step.children().toList()));
    }
}
