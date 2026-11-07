package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The mobs a blob births, held back while the blob morphs into them on the
 * client and added to their level once the morph ends, so no mob stands
 * before the goo has become it. Each server holds its own.
 * slime-blob-morphs-into-the-slime-it-becomes
 */
public final class MorphBirths {

    private record Held(ServerLevel level, Entity born, int dueTick) {
    }

    private final List<Held> held = new ArrayList<>();

    /**
     * Holds a newborn back until a server tick.
     *
     * @param level   the level it joins
     * @param born    the newborn, its id and position set
     * @param dueTick the server tick its morph ends on
     */
    public void holdUntil(ServerLevel level, Entity born, int dueTick) {
        held.add(new Held(level, born, dueTick));
    }

    /**
     * Adds every newborn whose morph has ended to its level.
     *
     * @param tick the server tick
     */
    public void bearArrived(int tick) {
        Iterator<Held> births = held.iterator();
        while (births.hasNext()) {
            Held birth = births.next();
            if (birth.dueTick() <= tick) {
                births.remove();
                birth.level().addFreshEntity(birth.born());
            }
        }
    }

    /** Forgets every held newborn, as a server stop does. */
    public void clear() {
        held.clear();
    }
}
