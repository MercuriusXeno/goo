package com.mercuriusxeno.goo.ability.quantum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Quantum portals a server holds: every portal each player placed, as
 * many as they like, and the one pair each player has linked, the portal they
 * chose from the blue entry portal. A player choosing a partner for a portal
 * is held here too, unsaved. Saved in the overworld's data, so portals and
 * links outlive unloads and restarts.
 * portals-link-by-looking-through-portal-vision
 */
public class QuantumPortals extends SavedData {

    /** Saves the placed portals and the links. */
    public static final Codec<QuantumPortals> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Placed.CODEC.listOf().fieldOf("placed").forGetter(QuantumPortals::placedList),
            Linked.CODEC.listOf().fieldOf("links").forGetter(QuantumPortals::linkedList)
    ).apply(inst, QuantumPortals::new));

    /** Stored as goo/quantum_portals.dat in the overworld's data. */
    public static final SavedDataType<QuantumPortals> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "quantum_portals"), QuantumPortals::new, CODEC);

    private final Map<UUID, List<Portal>> placed = new HashMap<>();
    private final Map<UUID, Link> links = new HashMap<>();
    private final Map<UUID, Portal> choosing = new HashMap<>();
    /** The portal each player placed last, kept after it breaks so no older portal stands in for it. */
    private final Map<UUID, Portal> lastPlaced = new HashMap<>();

    /** Creates a server's portals with none placed. */
    public QuantumPortals() {
        this(List.of(), List.of());
    }

    /**
     * Creates a server's portals from what it saved.
     *
     * @param placed each player's placed portals
     * @param linked each player's linked pair
     */
    public QuantumPortals(List<Placed> placed, List<Linked> linked) {
        placed.forEach(entry -> {
            this.placed.put(entry.owner(), new ArrayList<>(entry.portals()));
            entry.last().ifPresent(last -> lastPlaced.put(entry.owner(), last));
        });
        linked.forEach(entry -> links.put(entry.owner(), entry.link()));
    }

    /**
     * Two portals linked, each carrying a traveller out of the other.
     *
     * @param entry the portal chosen from, the blue entry portal
     * @param exit  the portal linked to it
     */
    public record Link(Portal entry, Portal exit) {

        /** Saves a link. */
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Portal.CODEC.fieldOf("entry").forGetter(Link::entry),
                Portal.CODEC.fieldOf("exit").forGetter(Link::exit)
        ).apply(inst, Link::new));

        /** Sends a link. */
        public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                Portal.STREAM_CODEC, Link::entry,
                Portal.STREAM_CODEC, Link::exit,
                Link::new);

        /**
         * @param portal a portal
         * @return the other end of the link from it, empty for a portal the link does not hold
         */
        public Optional<Portal> otherEnd(Portal portal) {
            if (entry.equals(portal)) {
                return Optional.of(exit);
            }
            return exit.equals(portal) ? Optional.of(entry) : Optional.empty();
        }
    }

    /**
     * A player's placed portals, as saved.
     *
     * @param owner   the player
     * @param portals their portals
     * @param last    the portal they placed last, empty for none
     */
    public record Placed(UUID owner, List<Portal> portals, Optional<Portal> last) {

        static final Codec<Placed> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Placed::owner),
                Portal.CODEC.listOf().fieldOf("portals").forGetter(Placed::portals),
                Portal.CODEC.optionalFieldOf("last").forGetter(Placed::last)
        ).apply(inst, Placed::new));
    }

    /**
     * A player's linked pair, as saved.
     *
     * @param owner the player
     * @param link  the pair
     */
    public record Linked(UUID owner, Link link) {

        static final Codec<Linked> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Linked::owner),
                Link.CODEC.fieldOf("link").forGetter(Linked::link)
        ).apply(inst, Linked::new));
    }

    /**
     * The server's portals, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the portals
     */
    public static QuantumPortals get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Places a portal for a player; a portal already standing there changes nothing.
     *
     * @param owner  the player
     * @param portal the portal
     */
    public void place(UUID owner, Portal portal) {
        List<Portal> owned = placed.computeIfAbsent(owner, key -> new ArrayList<>());
        if (!owned.contains(portal)) {
            owned.add(portal);
            lastPlaced.put(owner, portal);
            setDirty();
        }
    }

    /**
     * The portal a player placed last, while it still stands.
     *
     * @param owner the player
     * @return the portal, empty where they placed none or it broke
     */
    public Optional<Portal> lastPlaced(UUID owner) {
        return Optional.ofNullable(lastPlaced.get(owner)).filter(portalsOf(owner)::contains);
    }

    /**
     * @param owner a player
     * @return every portal they placed
     */
    public List<Portal> portalsOf(UUID owner) {
        return List.copyOf(placed.getOrDefault(owner, List.of()));
    }

    /**
     * Holds a player choosing a partner for one of their portals, or ends
     * their choosing; a portal they did not place is refused.
     *
     * @param owner  the player
     * @param portal the portal they choose for, empty to end choosing
     * @return true where the choosing now stands as asked
     */
    public boolean choose(UUID owner, Optional<Portal> portal) {
        if (portal.isEmpty()) {
            choosing.remove(owner);
            return true;
        }
        if (!portalsOf(owner).contains(portal.get())) {
            return false;
        }
        choosing.put(owner, portal.get());
        return true;
    }

    /**
     * @param owner a player
     * @return the portal they are choosing a partner for, empty while they choose none
     */
    public Optional<Portal> choosing(UUID owner) {
        return Optional.ofNullable(choosing.get(owner));
    }

    /**
     * Links the portal a player is choosing for to another of theirs in the
     * same dimension: the chosen-from portal is the entry, and the pair the
     * player linked before is unlinked, so a player holds one pair at a time.
     * Choosing ends. A portal in another dimension is refused, and choosing holds.
     *
     * @param owner  the player
     * @param target the portal right clicked as the partner
     * @return the pair unlinked, and the pair now standing; empty where no link lands
     */
    public Optional<Relink> link(UUID owner, Portal target) {
        Portal from = choosing.get(owner);
        if (from == null || from.equals(target) || !portalsOf(owner).contains(target)
                || !from.block().dimension().equals(target.block().dimension())) {
            return Optional.empty();
        }
        choosing.remove(owner);
        Link now = new Link(from, target);
        Optional<Link> before = Optional.ofNullable(links.put(owner, now));
        setDirty();
        return Optional.of(new Relink(before, now));
    }

    /**
     * A link landing: the pair it unlinked and the pair standing.
     *
     * @param before the pair unlinked, empty for none
     * @param now    the pair now linked
     */
    public record Relink(Optional<Link> before, Link now) {
    }

    /**
     * @param owner a player
     * @return the pair they linked, empty for none
     */
    public Optional<Link> linkOf(UUID owner) {
        return Optional.ofNullable(links.get(owner));
    }

    /**
     * The portal a portal carries travellers out of.
     *
     * @param portal a portal
     * @return its partner, empty for a portal no link holds
     */
    public Optional<Portal> partnerOf(Portal portal) {
        return links.values().stream().flatMap(link -> link.otherEnd(portal).stream()).findFirst();
    }

    /**
     * The portal covering a block's face, whoever placed it.
     *
     * @param block the block
     * @param face  the face
     * @return the portal, empty where none covers it
     */
    public Optional<Portal> portalAt(GlobalPos block, Direction face) {
        return placed.values().stream().flatMap(List::stream)
                .filter(portal -> portal.face() == face && portal.block().dimension().equals(block.dimension())
                        && portal.covers(block.pos()))
                .findFirst();
    }

    /**
     * @return every link, keyed by the player who linked it
     */
    public Map<UUID, Link> links() {
        return Map.copyOf(links);
    }

    /**
     * Drops a portal that no longer stands, with the link holding it.
     *
     * @param portal the fallen portal
     * @return the owner whose link the portal took down, empty where it was in no link
     */
    public Optional<UUID> fall(Portal portal) {
        placed.values().forEach(owned -> owned.remove(portal));
        choosing.values().remove(portal);
        Optional<UUID> owner = links.entrySet().stream()
                .filter(entry -> entry.getValue().otherEnd(portal).isPresent())
                .map(Map.Entry::getKey).findFirst();
        owner.ifPresent(links::remove);
        setDirty();
        return owner;
    }

    /**
     * @return every placed portal's owner
     */
    public Collection<UUID> owners() {
        return List.copyOf(placed.keySet());
    }

    private List<Placed> placedList() {
        return placed.entrySet().stream().map(entry -> new Placed(entry.getKey(), entry.getValue(),
                Optional.ofNullable(lastPlaced.get(entry.getKey())))).toList();
    }

    private List<Linked> linkedList() {
        return links.entrySet().stream().map(entry -> new Linked(entry.getKey(), entry.getValue())).toList();
    }
}
