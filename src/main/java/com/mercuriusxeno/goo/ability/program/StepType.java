package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;

/**
 * A registered step kind: the name the JSON {@code "type"} field carries
 * and the MapCodec that reads the step's own params (decision
 * ability-params-in-datapack: each step's params typed by its own codec).
 *
 * @param name  the type name as written in the JSON
 * @param codec the codec for the step's params
 * @param <T>   the step class
 */
public record StepType<T extends Step>(String name, MapCodec<T> codec) {
}
