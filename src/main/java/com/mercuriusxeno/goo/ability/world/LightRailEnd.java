package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A block entity a light rail joins: it says which of its lenses are open, remembers the reflectors it links to
 * and the light its network carries, and names the player a rail it lays credits.
 * decision reflector-rails-carry-the-brightest-light
 */
public interface LightRailEnd {

    /**
     * Whether a rail may leave this end toward a side.
     *
     * @param toward the side
     * @return true where the lens on that side is open
     */
    boolean lensOpen(Direction toward);

    /**
     * Records the reflectors this end links to and the light its network carries.
     *
     * @param partners the reflectors linked by rail
     * @param light    the network's light
     */
    void setLinks(List<BlockPos> partners, int light);

    /**
     * @return the reflectors this end links to by light rail
     */
    List<BlockPos> getLinks();

    /**
     * @return the player a rail this end newly lays credits, or null for none
     */
    @Nullable UUID railPlacer();
}
