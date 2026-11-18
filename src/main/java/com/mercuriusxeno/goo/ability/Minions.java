package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Which entities are minions at all: a mob holding any charm or any lead,
 * whoever charmed or led it. {@link OwnMinions} asks whose minion a mob
 * is; this asks whether it is anyone's.
 * diagnose-and-correct-minions-killing-each-other
 */
public final class Minions {

    private Minions() {
    }

    /**
     * Whether an entity holds a charm or a lead.
     *
     * @param entity the entity asked about, or null for none
     * @return true for a charmed or led entity
     */
    public static boolean isMinion(@Nullable Entity entity) {
        return entity != null && (entity.hasData(GooAttachments.CHARMED) || entity.hasData(GooAttachments.LED));
    }
}
