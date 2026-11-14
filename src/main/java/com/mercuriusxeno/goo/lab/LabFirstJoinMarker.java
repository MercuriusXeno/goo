package com.mercuriusxeno.goo.lab;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Whether the Goo Lab save has had its first join: saved with the overworld,
 * so the lab is built and the kit handed once per save.
 * decision goo-lab-save-builds-and-kits-on-first-join
 */
public final class LabFirstJoinMarker extends SavedData {

    /**
     * Codec for persistent serialization of the marker.
     */
    public static final Codec<LabFirstJoinMarker> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.BOOL.fieldOf("built").forGetter(LabFirstJoinMarker::isBuilt))
                    .apply(instance, LabFirstJoinMarker::new));
    /**
     * SavedData type registration. Stored as goo/lab_first_join.dat in the server data storage.
     */
    public static final SavedDataType<LabFirstJoinMarker> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "lab_first_join"), LabFirstJoinMarker::new, CODEC);

    /**
     * Whether the save's first join has run.
     */
    private boolean built;

    /**
     * Creates a marker reading unbuilt, the state of a save no join has fitted out.
     */
    public LabFirstJoinMarker() {
        this(false);
    }

    /**
     * Creates a marker from deserialized data.
     *
     * @param built whether the save's first join has run
     */
    public LabFirstJoinMarker(boolean built) {
        this.built = built;
    }

    /**
     * Answers the save's marker, kept in the overworld's data storage.
     *
     * @param level any server level (the overworld is used internally)
     * @return the save's marker
     */
    public static LabFirstJoinMarker of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Answers whether the save's first join has run.
     *
     * @return true once the marker is written built
     */
    public boolean isBuilt() {
        return built;
    }

    /**
     * Writes the marker built, so later joins change nothing.
     */
    public void markBuilt() {
        built = true;
        setDirty();
    }
}
