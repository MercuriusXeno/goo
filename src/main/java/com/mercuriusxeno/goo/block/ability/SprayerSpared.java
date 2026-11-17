package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.entity.Entity;
import java.util.Optional;
import java.util.UUID;

/**
 * Who a standing goo's hit spares: the player who sprayed the cell, since
 * an ability never attacks its caster (operator ruling 2026-11-17). A cell
 * no player sprayed spares nobody.
 * standing-goo-thing-credits-whoever-placed-it
 */
final class SprayerSpared {

    private SprayerSpared() {
    }

    /**
     * Whether a cell's hit spares a thing: true for the player who sprayed the cell.
     *
     * @param entity  the thing the hit would land on
     * @param sprayer the player who sprayed the cell, empty where none did
     * @return true where the hit spares the thing
     */
    static boolean spares(Entity entity, Optional<UUID> sprayer) {
        return sprayer.filter(entity.getUUID()::equals).isPresent();
    }
}
