package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;

/**
 * The burnout explosions playing on this client, each held from the game
 * time it began until its goo type's explosion has run its duration
 * (decision elemental-explosion-per-type).
 */
public final class ChainBurnouts {

    /** The list the client's burnout handler and renderer share. */
    public static final ChainBurnouts CLIENT = new ChainBurnouts();

    /**
     * One burnout explosion playing.
     *
     * @param pos        the marker's block position
     * @param placedFace the face the marker was placed on
     * @param abilityId  the id of the ability the marker ran
     * @param size       the size the cast was dragged to, in blocks, zero for a throw
     *                   (decision blast-is-drag-sized-like-the-black-hole)
     * @param startTick  the game time the explosion began
     * @param visual     the explosion its goo type draws
     */
    public record Burnout(BlockPos pos, Direction placedFace, String abilityId, double size,
                          long startTick, BurnoutVisual visual) {

        /**
         * @param gameTime the game time including the partial tick
         * @return the explosion's progress in [0, 1]
         */
        public float progress(float gameTime) {
            int duration = visual.durationTicks();
            if (duration <= 0) {
                return 1f;
            }
            return Math.min(1f, Math.max(0f, (gameTime - startTick) / duration));
        }

        /**
         * @param now the game time
         * @return true once the explosion has run its duration
         */
        boolean isOver(long now) {
            return now - startTick >= visual.durationTicks();
        }
    }

    private final List<Burnout> live = new ArrayList<>();

    /**
     * Adds a burnout, resolving the explosion its goo type draws.
     *
     * @param pos        the marker's block position
     * @param placedFace the face the marker was placed on
     * @param gooType    the marker's goo type
     * @param abilityId  the id of the ability the marker ran
     * @param size       the size the cast was dragged to, zero for a throw
     * @param now        the game time the burnout arrived
     * @return the burnout added
     */
    public Burnout add(BlockPos pos, Direction placedFace, ResourceKey<GooTypeDefinition> gooType,
                       String abilityId, double size, long now) {
        Burnout burnout = new Burnout(pos, placedFace, abilityId, size, now,
                BurnoutVisuals.forType(gooType));
        live.add(burnout);
        return burnout;
    }

    /**
     * Drops every burnout whose explosion has run its duration and answers the rest.
     *
     * @param now the game time
     * @return the burnouts still playing, in the order they arrived
     */
    public List<Burnout> live(long now) {
        live.removeIf(burnout -> burnout.isOver(now));
        return List.copyOf(live);
    }

    /** Drops every burnout, as the client leaves a level. */
    public void clear() {
        live.clear();
    }
}
