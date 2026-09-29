package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.Variables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * The burnout explosions playing on this client, each held from the game
 * time it began until its goo type's explosion has run its duration
 * (decision elemental-explosion-per-type). A marker that mines a tunnel
 * plays no burnout explosion, as the operator settled: its per-layer
 * effects carry the moment, so its burnout is never added.
 */
public final class ChainBurnouts {

    /** The list the client's burnout handler and renderer share. */
    public static final ChainBurnouts CLIENT = new ChainBurnouts(SyncedSteps::minesTunnel);

    /**
     * One burnout explosion playing.
     *
     * @param pos        the marker's block position
     * @param placedFace the face the marker was placed on
     * @param abilityId  the id of the ability the marker ran
     * @param stackCount the marker's stack count at burnout
     * @param startTick  the game time the explosion began
     * @param visual     the explosion its goo type draws
     */
    public record Burnout(BlockPos pos, Direction placedFace, String abilityId, int stackCount,
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
         * The variables a step param reads for this burnout: its stack count.
         *
         * @return the burnout's variables
         */
        public Variables variables() {
            return name -> HostVariables.STACKS.equals(name)
                    ? OptionalDouble.of(stackCount) : OptionalDouble.empty();
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
    private final Predicate<String> playsNoBurnout;

    /**
     * @param playsNoBurnout answers, for an ability id, whether its marker plays no burnout explosion
     */
    public ChainBurnouts(Predicate<String> playsNoBurnout) {
        this.playsNoBurnout = playsNoBurnout;
    }

    /**
     * Adds a burnout, resolving the explosion its goo type draws, unless its
     * ability plays none.
     *
     * @param pos        the marker's block position
     * @param placedFace the face the marker was placed on
     * @param gooType    the marker's goo type
     * @param abilityId  the id of the ability the marker ran
     * @param stackCount the marker's stack count at burnout
     * @param now        the game time the burnout arrived
     * @return the burnout added, or null when its ability plays no burnout explosion
     */
    public @Nullable Burnout add(BlockPos pos, Direction placedFace, ResourceKey<GooTypeDefinition> gooType,
                       String abilityId, int stackCount, long now) {
        if (playsNoBurnout.test(abilityId)) {
            return null;
        }
        Burnout burnout = new Burnout(pos, placedFace, abilityId, stackCount, now,
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
