package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The hums the client plays: one loop at every portal and wormhole mouth it
 * draws in the local player's level, started as the gate stands and stopped
 * the tick it falls.
 * standing-portals-loop-a-soft-ambient-hum
 * wormhole-mouths-take-the-whoosh-and-the-hum
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalHums {

    /** The hum playing at each gate. */
    private static final Map<Gate, PortalHum> PLAYING = new HashMap<>();
    /** The level the hums play in, so joining another drops every hum the last one held. */
    private static ClientLevel playingIn;

    private PortalHums() {
    }

    /**
     * The hums to start and to stop.
     *
     * @param starts the gates newly standing, each to start humming at its centre
     * @param stops  the gates fallen, each to stop humming
     */
    public record Changes(Set<Gate> starts, Set<Gate> stops) {
    }

    /**
     * Each client tick, starts a hum at each gate newly standing and stops
     * the hum of each that fell.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != playingIn) {
            // The sound manager drops every sound as a level is left, so no hum carries over.
            PLAYING.values().forEach(PortalHum::fall);
            PLAYING.clear();
            playingIn = mc.level;
        }
        Set<Gate> standing = mc.level == null ? Set.of() : standing(ClientPortals.portals(), ClientPortals.links(),
                ClientHoles.holes(), mc.level.dimension());
        Changes changes = changes(standing, PLAYING.keySet());
        changes.stops().forEach(gate -> PLAYING.remove(gate).fall());
        changes.starts().forEach(gate -> {
            PortalHum hum = new PortalHum(gate.center());
            PLAYING.put(gate, hum);
            mc.getSoundManager().play(hum);
        });
    }

    /**
     * Which hums change: a start for each gate standing that plays no hum, a
     * stop for each hum playing whose gate no longer stands.
     *
     * @param standing the gates standing
     * @param playing  the gates humming
     * @return the changes
     */
    static Changes changes(Set<Gate> standing, Set<Gate> playing) {
        return new Changes(without(standing, playing), without(playing, standing));
    }

    private static Set<Gate> without(Set<Gate> gates, Set<Gate> taken) {
        return gates.stream().filter(gate -> !taken.contains(gate)).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The gates the client draws in a level, each of which hums: every portal
     * the local player placed, linked or inert, every pair any player linked,
     * and each open hole's two mouths, the set PortalFaces draws.
     *
     * @param portals   the local player's portals
     * @param links     every linked pair the client is told of
     * @param holes     every open hole the client is told of
     * @param dimension the local player's level
     * @return the gates standing there
     */
    static Set<Gate> standing(List<Portal> portals, List<QuantumPortals.Link> links,
            List<HolesSyncPayload.Pair> holes, ResourceKey<Level> dimension) {
        Stream<Portal> placed = Stream.concat(portals.stream(),
                links.stream().flatMap(link -> Stream.of(link.entry(), link.exit())));
        Stream<Gate> mouths = holes.stream().flatMap(hole -> Stream.of(hole.entry(), hole.exit()));
        return Stream.concat(placed.map(Portal::gate), mouths)
                .filter(gate -> gate.dimension().equals(dimension))
                .collect(Collectors.toUnmodifiableSet());
    }
}
