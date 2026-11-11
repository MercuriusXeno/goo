package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One standing portal's or wormhole mouth's soft ambient hum: a loop held at
 * the gate's centre, falling off linearly with distance, until the gate falls.
 * Its softness and its short reach stand in sounds.json.
 * standing-portals-loop-a-soft-ambient-hum
 * wormhole-mouths-take-the-whoosh-and-the-hum
 */
public final class PortalHum extends AbstractTickableSoundInstance {

    /**
     * Creates the hum at a gate's centre.
     *
     * @param at the gate's centre
     */
    public PortalHum(Vec3 at) {
        // The sound manager reads an instance's sound by its id alone, so the hum names its id rather than
        // binding the sound registry.
        super(SoundEvent.createVariableRangeEvent(GooSoundIds.PORTAL_HUM), SoundSource.BLOCKS,
                SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        this.x = at.x();
        this.y = at.y();
        this.z = at.z();
    }

    @Override
    public void tick() {
        // The hum holds still at its gate; PortalHums stops it the tick the gate falls.
    }

    /**
     * Stops the loop where it stands in its playing, the tick its gate falls,
     * however far into the loop it is.
     */
    void fall() {
        stop();
    }
}
