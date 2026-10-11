package com.mercuriusxeno.goo.ability.quantum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The quantum anchors a server holds: each caster's anchor still waiting for
 * its pair, and the pairs linked, each anchor carrying a traveller to its
 * partner. A caster's second anchor pairs with their first. Saved in the
 * overworld's data, so a pair outlives unloads and restarts.
 * quantum-anchors-link-two-points
 */
public class QuantumAnchors extends SavedData {

    /** How far around an anchor's cell, in blocks, a player stands at it. */
    static final double REACH = 0.5;

    /** Saves the waiting anchors and the pairs. */
    public static final Codec<QuantumAnchors> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Waiting.CODEC.listOf().fieldOf("waiting").forGetter(QuantumAnchors::waitingList),
            Link.CODEC.listOf().fieldOf("links").forGetter(anchors -> anchors.links)
    ).apply(inst, QuantumAnchors::new));

    /** Stored as goo/quantum_anchors.dat in the overworld's data. */
    public static final SavedDataType<QuantumAnchors> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "quantum_anchors"), QuantumAnchors::new, CODEC);

    private final Map<UUID, GlobalPos> waiting;
    private final List<Link> links;

    /** Creates a server's anchors with none standing. */
    public QuantumAnchors() {
        this(List.of(), List.of());
    }

    /**
     * Creates a server's anchors from what it saved.
     *
     * @param waiting the anchors waiting for their pair
     * @param links   the linked pairs
     */
    public QuantumAnchors(List<Waiting> waiting, List<Link> links) {
        this.waiting = new HashMap<>();
        waiting.forEach(entry -> this.waiting.put(entry.caster(), entry.anchor()));
        this.links = new ArrayList<>(links);
    }

    /**
     * A caster's anchor waiting for its pair.
     *
     * @param caster the player who cast it
     * @param anchor the anchor
     */
    public record Waiting(UUID caster, GlobalPos anchor) {

        static final Codec<Waiting> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("caster").forGetter(Waiting::caster),
                GlobalPos.CODEC.fieldOf("anchor").forGetter(Waiting::anchor)
        ).apply(inst, Waiting::new));
    }

    /**
     * Two anchors linked, each carrying a traveller to the other.
     *
     * @param first  the anchor cast first
     * @param second the anchor that named it as its pair
     */
    public record Link(GlobalPos first, GlobalPos second) {

        static final Codec<Link> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                GlobalPos.CODEC.fieldOf("first").forGetter(Link::first),
                GlobalPos.CODEC.fieldOf("second").forGetter(Link::second)
        ).apply(inst, Link::new));

        boolean holds(GlobalPos anchor) {
            return first.equals(anchor) || second.equals(anchor);
        }
    }

    /**
     * The server's anchors, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the anchors
     */
    public static QuantumAnchors get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The box a player stands in to be at an anchor: its cell and a half
     * block around it.
     *
     * @param anchor the anchor's cell
     * @return the box
     */
    public static AABB reachOf(BlockPos anchor) {
        return new AABB(anchor).inflate(REACH);
    }

    /**
     * Stands an anchor for its caster: it pairs with the caster's anchor
     * waiting for one, or waits for the caster's next. An anchor already
     * standing changes nothing.
     *
     * @param caster the player who cast it
     * @param anchor the anchor
     */
    public void stand(UUID caster, GlobalPos anchor) {
        if (partnerOf(anchor).isPresent() || anchor.equals(waiting.get(caster))) {
            return;
        }
        GlobalPos first = waiting.remove(caster);
        if (first != null) {
            links.add(new Link(first, anchor));
        } else {
            waiting.put(caster, anchor);
        }
        setDirty();
    }

    /**
     * The anchor an anchor carries travellers to.
     *
     * @param anchor the anchor
     * @return its partner, empty for an anchor with none
     */
    public Optional<GlobalPos> partnerOf(GlobalPos anchor) {
        for (Link link : links) {
            if (link.first().equals(anchor)) {
                return Optional.of(link.second());
            }
            if (link.second().equals(anchor)) {
                return Optional.of(link.first());
            }
        }
        return Optional.empty();
    }

    /**
     * Drops an anchor that no longer stands, with its link and its wait.
     *
     * @param anchor the fallen anchor
     */
    public void fall(GlobalPos anchor) {
        boolean dropped = links.removeIf(link -> link.holds(anchor)) | waiting.values().remove(anchor);
        if (dropped) {
            setDirty();
        }
    }

    private List<Waiting> waitingList() {
        return waiting.entrySet().stream().map(entry -> new Waiting(entry.getKey(), entry.getValue())).toList();
    }
}
