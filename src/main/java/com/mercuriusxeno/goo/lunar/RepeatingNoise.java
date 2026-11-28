package com.mercuriusxeno.goo.lunar;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NoiseStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A noise that repeats on x and z every period of its input, so the ground it shapes meets itself at the seam with no
 * cliff. It keeps each of a noise's layers, the gradient lattice and its amplitude, and reads each layer at a
 * frequency nudged so the lattice fits a whole number of its 256-cell turns into the period: the lattice's gradients
 * repeat every 256 cells, so a layer at that frequency repeats every period exactly, and the noise stays as smooth
 * across the seam as anywhere. Height is read as the noise reads it, unwrapped.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class RepeatingNoise implements Noise {

    /** The lattice's gradients repeat every this many cells. */
    static final int LATTICE_TURN = 256;
    /** A coordinate wraps to within half a period either side of zero. */
    private static final double HALF = 0.5;

    private final NoiseStack source;
    private final double period;
    private final Layer[] layers;
    private final Map<Double, RepeatingNoise> otherPeriods = new ConcurrentHashMap<>();

    private RepeatingNoise(NoiseStack source, double period) {
        this.source = source;
        this.period = period;
        NoiseStack.Layer[] sourceLayers = source.layers;
        this.layers = new Layer[sourceLayers.length];
        for (int i = 0; i < sourceLayers.length; i++) {
            NoiseStack.Layer layer = sourceLayers[i];
            layers[i] = new Layer(layer.noise(), repeatingFrequency(layer.frequency(), period), layer.frequency(),
                    layer.amplitude());
        }
    }

    /**
     * One of the noise's layers as it repeats.
     *
     * @param noise              the layer's gradient lattice
     * @param horizontalFrequency the frequency x and z read the lattice at, fitted to the period
     * @param verticalFrequency  the frequency y reads it at, the layer's own
     * @param amplitude          the layer's amplitude
     */
    private record Layer(Noise noise, double horizontalFrequency, double verticalFrequency, float amplitude) {
    }

    /**
     * A noise repeating every period of its input on x and z.
     *
     * @param noise  the noise, as the world's random state seeds it
     * @param period the period, in the units the noise is read in
     * @return the noise repeating, or the noise as it stands where it holds no layers to fit
     */
    public static Noise repeating(Noise noise, double period) {
        if (noise instanceof RepeatingNoise repeating) {
            return repeating.everyPeriod(period);
        }
        return noise instanceof NoiseStack stack ? new RepeatingNoise(stack, period) : noise;
    }

    /**
     * The frequency nearest a layer's own at which its lattice repeats every period: a whole number of the lattice's
     * turns, at least one, fit into the period.
     *
     * @param frequency the layer's own frequency
     * @param period    the period, in the units the noise is read in
     * @return the fitted frequency
     */
    static double repeatingFrequency(double frequency, double period) {
        long turns = Math.max(1L, Math.round(frequency * period / LATTICE_TURN));
        return turns * LATTICE_TURN / period;
    }

    private RepeatingNoise everyPeriod(double otherPeriod) {
        if (otherPeriod == period) {
            return this;
        }
        return otherPeriods.computeIfAbsent(otherPeriod, key -> new RepeatingNoise(source, key));
    }

    private double wrap(double coordinate) {
        return coordinate - period * Math.floor(coordinate / period + HALF);
    }

    @Override
    public Interval range() {
        return source.range();
    }

    @Override
    public float get(double x, double y, double z) {
        double wrappedX = wrap(x);
        double wrappedZ = wrap(z);
        float value = 0.0F;
        for (Layer layer : layers) {
            value += layer.amplitude * layer.noise.get(wrappedX * layer.horizontalFrequency,
                    y * layer.verticalFrequency, wrappedZ * layer.horizontalFrequency);
        }
        return value;
    }

    @Override
    public float get(double x, double z) {
        double wrappedX = wrap(x);
        double wrappedZ = wrap(z);
        float value = 0.0F;
        for (Layer layer : layers) {
            value += layer.amplitude * layer.noise.get(wrappedX * layer.horizontalFrequency,
                    wrappedZ * layer.horizontalFrequency);
        }
        return value;
    }

    /**
     * Adds the noise over a volume: each layer's own fast pass where the volume lies inside one period and needs no
     * wrap, and column by column where it reaches across the seam.
     */
    @Override
    public void addToVolume(DensityBuffer buffer, DensityVolume volume, double xzScale, double yScale,
                            float amplitude) {
        if (!liesInsideOnePeriod(volume, xzScale)) {
            Noise.super.addToVolume(buffer, volume, xzScale, yScale, amplitude);
            return;
        }
        for (Layer layer : layers) {
            layer.noise.addToVolume(buffer, volume, xzScale * layer.horizontalFrequency,
                    yScale * layer.verticalFrequency, amplitude * layer.amplitude);
        }
    }

    private boolean liesInsideOnePeriod(DensityVolume volume, double xzScale) {
        return readsUnwrapped(volume.minBlockX() * xzScale) && readsUnwrapped(volume.maxBlockX() * xzScale)
                && readsUnwrapped(volume.minBlockZ() * xzScale) && readsUnwrapped(volume.maxBlockZ() * xzScale);
    }

    private boolean readsUnwrapped(double coordinate) {
        return wrap(coordinate) == coordinate;
    }
}
