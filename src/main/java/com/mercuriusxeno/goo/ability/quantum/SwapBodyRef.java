package com.mercuriusxeno.goo.ability.quantum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

/**
 * The swap body a player has standing: the body's id and where it stands.
 * swap-body-stands-until-reverted
 *
 * @param body the body's entity id
 * @param at   where it stands
 */
public record SwapBodyRef(UUID body, GlobalPos at) {

    /** Saves the reference with the player. */
    public static final Codec<SwapBodyRef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("body").forGetter(SwapBodyRef::body),
            GlobalPos.CODEC.fieldOf("at").forGetter(SwapBodyRef::at)
    ).apply(inst, SwapBodyRef::new));
}
