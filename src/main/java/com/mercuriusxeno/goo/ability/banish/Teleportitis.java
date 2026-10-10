package com.mercuriusxeno.goo.ability.banish;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * The teleportitis a player holds: each hit they would take blinks them the
 * distance along their look instead, and a fall out of the world returns
 * them to the last solid ground they stood on, until the game time it fades
 * at. Saved with the player.
 * Decision teleportitis-blinks-along-the-cursor-on-hit.
 *
 * @param distance   how far each hit blinks the player along their look
 * @param expiresAt  the game time it fades at; zero when none stands
 * @param safeGround the last solid ground the player stood on while it stood, empty before any
 */
public record Teleportitis(float distance, long expiresAt, Optional<Vec3> safeGround) {

    /** No teleportitis. */
    public static final Teleportitis NONE = new Teleportitis(0f, 0L, Optional.empty());
    /** The fade time of teleportitis held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_DISTANCE = "distance";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_SAFE_GROUND = "safe_ground";

    /** Saves the teleportitis with the player. */
    public static final MapCodec<Teleportitis> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_DISTANCE).forGetter(Teleportitis::distance),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Teleportitis::expiresAt),
            Vec3.CODEC.optionalFieldOf(FIELD_SAFE_GROUND).forGetter(Teleportitis::safeGround)
    ).apply(inst, Teleportitis::new));

    /**
     * Whether teleportitis stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: teleportitis stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @param castDistance the cast's blink distance
     * @return the teleportitis after the cast
     */
    public Teleportitis hold(float castDistance) {
        return new Teleportitis(castDistance, NEVER_EXPIRES, safeGround);
    }

    /**
     * A drunk brew: teleportitis stands for the brew's duration, or as long
     * as it already stood where that is longer.
     *
     * @param brewDistance the brew's blink distance
     * @param ticks        the brew's duration
     * @param gameTime     the game time of the drink
     * @return the teleportitis after the drink
     */
    public Teleportitis brew(float brewDistance, int ticks, long gameTime) {
        return new Teleportitis(brewDistance, Math.max(expiresAt, gameTime + ticks), safeGround);
    }

    /**
     * Teleportitis remembering where the player last stood on solid ground.
     *
     * @param ground the player's feet on that ground
     * @return the teleportitis with the ground remembered
     */
    public Teleportitis standingOn(Vec3 ground) {
        return new Teleportitis(distance, expiresAt, Optional.of(ground));
    }
}
