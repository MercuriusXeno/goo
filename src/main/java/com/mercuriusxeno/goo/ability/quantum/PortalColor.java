package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.ability.GloveButton;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The color a Quantum portal opens in: the blue one or the orange one.
 * portal-takes-over-left-and-right-click-for-blue-and-orange
 */
public enum PortalColor implements StringRepresentable {
    /** The blue portal, placed by left click. */
    BLUE("blue"),
    /** The orange portal, placed by right click. */
    ORANGE("orange");

    /** Saves a color. */
    public static final Codec<PortalColor> CODEC = StringRepresentable.fromEnum(PortalColor::values);

    /** Sends a color. */
    public static final StreamCodec<ByteBuf, PortalColor> STREAM_CODEC = ByteBufCodecs.BOOL.map(
            orange -> orange ? ORANGE : BLUE, color -> color == ORANGE);

    /** The start of every shot visual's name, its color's name following. */
    private static final String SHOT_VISUAL_PREFIX = "quantum_blob_";

    private final String serializedName;

    PortalColor(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * The color a press of a button places, mirroring the portal gun's
     * controls: left click, the hit, places blue, and right click, the use,
     * places orange.
     * portal-takes-over-left-and-right-click-for-blue-and-orange
     *
     * @param button the button pressed
     * @return the color it places
     */
    public static PortalColor pressedBy(GloveButton button) {
        return button == GloveButton.ATTACK ? BLUE : ORANGE;
    }

    /**
     * The visual a shot of this color darts from the palm blob: a small fast
     * Quantum blob in this portal's color.
     * operator UAT 2026-11-18: quantum goo's color is orange and blue; the shots fire orange on right
     *
     * @return the visual's name
     */
    public String shotVisual() {
        return SHOT_VISUAL_PREFIX + serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
