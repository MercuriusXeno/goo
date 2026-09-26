package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds a step in the program of the ability a marker runs, read from the
 * ability definitions the server synced, so a renderer reads the step's
 * params rather than a copy the marker syncs every tick (decision
 * capability-interfaces-derive-host-kind).
 */
public final class SyncedSteps {

    private SyncedSteps() {
    }

    /**
     * Finds the first step of a type in the marker's ability program,
     * searching container steps' children after the step itself.
     *
     * @param be   the chain marker block entity
     * @param type the step type
     * @param <S>  the step class
     * @return the step, or empty when no ability is synced under the
     *         marker's id or its program holds no such step
     */
    public static <S extends Step> Optional<S> first(ChainMarkerBlockEntity be, Class<S> type) {
        ClientAbility ability = AbilitySyncHandler.findAbility(be.getAbilityId());
        if (ability == null) {
            return Optional.empty();
        }
        return ability.behaviors().stream()
                .flatMap(SyncedSteps::withDescendants)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    /**
     * Streams a step and every step beneath it, depth first.
     *
     * @param step the step
     * @return the step and its descendants
     */
    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(SyncedSteps::withDescendants));
    }
}
