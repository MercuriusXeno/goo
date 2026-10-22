package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Set;

/**
 * What every audit rule reads. The registered item ids are handed in, so a rule reads no registry and runs in
 * a unit test.
 *
 * @param values          the server's goo value registry
 * @param reactions       the reactions the server loaded
 * @param registeredItems every registered item's id
 */
public record GooAuditInputs(GooValueRegistry values, List<GooReaction> reactions, Set<Identifier> registeredItems) {
}
