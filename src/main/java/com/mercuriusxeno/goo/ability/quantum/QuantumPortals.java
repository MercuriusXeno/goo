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
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Quantum portals a server holds: one pair for each player at most, a
 * blue portal and an orange one, a new portal of a color taking that
 * color's place. A player's two portals are linked whenever both stand in
 * one dimension. Saved in the overworld's data, so portals outlive unloads
 * and restarts.
 * player-holds-one-pair-of-portals
 * portals-link-by-looking-through-portal-vision
 */
public class QuantumPortals extends SavedData {

    /**
     * Saves each player's pair. A save from before one pair per player held
     * every portal placed and every pair linked; it loads as the newest
     * portal of each color for each player, an orange one being a portal
     * that was some link's exit and a blue one any other.
     */
    public static final Codec<QuantumPortals> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Pair.CODEC.listOf().optionalFieldOf("pairs", List.of()).forGetter(QuantumPortals::pairList),
            Placed.CODEC.listOf().optionalFieldOf("placed", List.of()).forGetter(portals -> List.of()),
            Linked.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(portals -> List.of())
    ).apply(inst, QuantumPortals::new));

    /** Stored as goo/quantum_portals.dat in the overworld's data. */
    public static final SavedDataType<QuantumPortals> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "quantum_portals"), QuantumPortals::new, CODEC);

    private final Map<UUID, Map<PortalColor, Portal>> pairs = new HashMap<>();

    /** Creates a server's portals with none placed. */
    public QuantumPortals() {
        this(List.of(), List.of(), List.of());
    }

    /**
     * Creates a server's portals from what it saved.
     *
     * @param saved        each player's pair
     * @param legacyPlaced each player's placed portals, from a save before one pair per player
     * @param legacyLinks  every pair a player linked, from a save before one pair per player
     */
    public QuantumPortals(List<Pair> saved, List<Placed> legacyPlaced, List<Linked> legacyLinks) {
        saved.forEach(pair -> {
            pair.blue().ifPresent(portal -> slotsOf(pair.owner()).put(PortalColor.BLUE, portal));
            pair.orange().ifPresent(portal -> slotsOf(pair.owner()).put(PortalColor.ORANGE, portal));
        });
        if (saved.isEmpty()) {
            keepTheNewestOfEachColor(legacyPlaced, legacyLinks);
        }
    }

    /**
     * Takes a save from before one pair per player: each player's portals in
     * the order placed, the newest of each color standing.
     * player-holds-one-pair-of-portals
     *
     * @param legacyPlaced each player's placed portals, oldest first
     * @param legacyLinks  every pair linked, whose exits were the orange portals
     */
    private void keepTheNewestOfEachColor(List<Placed> legacyPlaced, List<Linked> legacyLinks) {
        Set<Portal> exits = legacyLinks.stream().map(linked -> linked.link().exit()).collect(Collectors.toSet());
        legacyPlaced.forEach(entry -> entry.portals().forEach(portal -> {
            PortalColor color = exits.contains(portal) ? PortalColor.ORANGE : PortalColor.BLUE;
            slotsOf(entry.owner()).put(color, portal.withColor(color));
        }));
    }

    /**
     * Two portals linked, each carrying a traveller out of the other.
     *
     * @param entry the blue portal
     * @param exit  the orange portal
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
     * A player's pair, as saved.
     *
     * @param owner  the player
     * @param blue   their blue portal, empty while none stands
     * @param orange their orange portal, empty while none stands
     */
    public record Pair(UUID owner, Optional<Portal> blue, Optional<Portal> orange) {

        static final Codec<Pair> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Pair::owner),
                Portal.CODEC.optionalFieldOf("blue").forGetter(Pair::blue),
                Portal.CODEC.optionalFieldOf("orange").forGetter(Pair::orange)
        ).apply(inst, Pair::new));
    }

    /**
     * A player's placed portals, as a save from before one pair per player held them.
     *
     * @param owner   the player
     * @param portals their portals, oldest first
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
     * A player's linked pair.
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

    private Map<PortalColor, Portal> slotsOf(UUID owner) {
        return pairs.computeIfAbsent(owner, key -> new EnumMap<>(PortalColor.class));
    }

    /**
     * Places a portal for a player in its color's place, the portal of that
     * color they placed before going; a portal already standing there
     * changes nothing.
     * player-holds-one-pair-of-portals
     *
     * @param owner  the player
     * @param portal the portal
     * @return true where the portal is new
     */
    public boolean place(UUID owner, Portal portal) {
        Portal before = slotsOf(owner).put(portal.color(), portal);
        if (portal.equals(before)) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * @param owner a player
     * @return the portals standing for them, blue first
     */
    public List<Portal> portalsOf(UUID owner) {
        return List.copyOf(pairs.getOrDefault(owner, Map.of()).values());
    }

    /**
     * A player's two portals as a link, when both stand in one dimension.
     *
     * @param owner a player
     * @return the link, blue the entry and orange the exit, empty while it lacks a portal
     */
    public Optional<Link> pairOf(UUID owner) {
        Map<PortalColor, Portal> slots = pairs.getOrDefault(owner, Map.of());
        Portal blue = slots.get(PortalColor.BLUE);
        Portal orange = slots.get(PortalColor.ORANGE);
        return blue != null && orange != null && blue.block().dimension().equals(orange.block().dimension())
                ? Optional.of(new Link(blue, orange)) : Optional.empty();
    }

    /**
     * The portal a portal carries travellers out of.
     *
     * @param portal a portal
     * @return its partner, the other of its owner's pair, empty for a portal no link holds
     */
    public Optional<Portal> partnerOf(Portal portal) {
        return links().stream().flatMap(link -> link.otherEnd(portal).stream()).findFirst();
    }

    /**
     * The portal covering a block's face, whoever placed it.
     *
     * @param block the block
     * @param face  the face
     * @return the portal, empty where none covers it
     */
    public Optional<Portal> portalAt(GlobalPos block, Direction face) {
        return pairs.values().stream().flatMap(slots -> slots.values().stream())
                .filter(portal -> portal.face() == face && portal.block().dimension().equals(block.dimension())
                        && portal.covers(block.pos()))
                .findFirst();
    }

    /**
     * @return every linked pair, whoever placed it
     */
    public List<Link> links() {
        return pairs.keySet().stream().flatMap(owner -> pairOf(owner).stream()).toList();
    }

    /**
     * Drops a portal that no longer stands.
     *
     * @param portal the fallen portal
     * @return the pair the portal took down, with whose it was, empty where it was in no link
     */
    public Optional<Linked> fall(Portal portal) {
        Optional<UUID> owner = pairs.entrySet().stream()
                .filter(entry -> portal.equals(entry.getValue().get(portal.color())))
                .map(Map.Entry::getKey).findFirst();
        Optional<Linked> fallen = owner.flatMap(id -> pairOf(id).map(link -> new Linked(id, link)));
        owner.ifPresent(id -> {
            pairs.get(id).remove(portal.color());
            if (pairs.get(id).isEmpty()) {
                pairs.remove(id);
            }
            setDirty();
        });
        return fallen;
    }

    /**
     * @return every placed portal's owner
     */
    public Collection<UUID> owners() {
        return List.copyOf(pairs.keySet());
    }

    private List<Pair> pairList() {
        return pairs.entrySet().stream().map(entry -> new Pair(entry.getKey(),
                Optional.ofNullable(entry.getValue().get(PortalColor.BLUE)),
                Optional.ofNullable(entry.getValue().get(PortalColor.ORANGE)))).toList();
    }
}
