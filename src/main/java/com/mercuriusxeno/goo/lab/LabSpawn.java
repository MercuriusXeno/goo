package com.mercuriusxeno.goo.lab;

/**
 * One mob the lab build spawns.
 *
 * @param offset   where the mob stands, relative to the lab origin
 * @param entityId the entity type id, such as {@code minecraft:cow}
 */
public record LabSpawn(LabOffset offset, String entityId) {
}
