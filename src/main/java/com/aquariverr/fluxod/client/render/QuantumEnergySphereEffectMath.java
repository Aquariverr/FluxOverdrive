package com.aquariverr.fluxod.client.render;

import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.mix;
import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.signed;
import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.smoothstep;
import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.unit;

public final class QuantumEnergySphereEffectMath {

    public static final int LEAK_PULSE_TICKS = 12;
    public static final double NO_LEAK_PULSE = -1.0;
    private static final double FULL_STABILITY_BASIS_POINTS = 10_000.0;
    private static final double DANGER_START = 0.70;
    private static final double CRITICAL_STABILITY = 0.10;

    public static Effects calculate(int stabilityBasisPoints, boolean quenchImminent,
                                    int leakEventSequence, double leakPulseAgeTicks,
                                    double gameTime) {
        double animationTime = Double.isFinite(gameTime) ? gameTime : 0.0;
        double stability = Math.clamp(stabilityBasisPoints / FULL_STABILITY_BASIS_POINTS, 0.0, 1.0);
        double instability = 1.0 - stability;
        double danger = smoothstep((instability - DANGER_START) / (1.0 - DANGER_START));
        double leakPulse = leakPulseEnvelope(leakPulseAgeTicks);

        double breathing = Math.sin(animationTime * (0.08 + instability * 0.12))
                * instability * (0.006 + danger * 0.012);
        double leakDistortion = leakRadialDistortion(leakEventSequence,
                leakPulseAgeTicks, leakPulse);
        double radiusScale = Math.clamp(1.0 + breathing + leakDistortion, 0.90, 1.10);

        double critical = quenchImminent
                ? smoothstep((CRITICAL_STABILITY - stability) / CRITICAL_STABILITY)
                : 0.0;
        double flash = critical * criticalFlicker(animationTime, leakEventSequence, critical);

        return new Effects(
                radiusScale,
                1.0 + instability * 0.22 + danger * 0.55 + leakPulse * 0.65,
                instability * 0.05 + danger * 0.15 + leakPulse * 0.10,
                Math.clamp((int) Math.round(instability * 2.0 + danger * 4.0 + leakPulse * 4.0), 0, 10),
                instability * 0.14 + danger * 0.25 + leakPulse * 0.32,
                1.0 + instability * 0.16 + danger * 0.50 + leakPulse * 0.55,
                1.0 + instability * 0.10 + danger * 0.40 + leakPulse * 0.65,
                instability * 0.20 + danger * 0.50 + leakPulse * 0.65,
                Math.clamp(flash, 0.0, 1.0));
    }

    private static double leakPulseEnvelope(double ageTicks) {
        if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= LEAK_PULSE_TICKS) return 0.0;
        double progress = ageTicks / LEAK_PULSE_TICKS;
        double attack = smoothstep(progress / 0.12);
        double decay = 1.0 - smoothstep((progress - 0.20) / 0.80);
        return attack * decay;
    }

    private static double leakRadialDistortion(int sequence, double ageTicks, double envelope) {
        if (envelope <= 0.0) return 0.0;
        double progress = Math.clamp(ageTicks / LEAK_PULSE_TICKS, 0.0, 1.0);
        long seed = mix(Integer.toUnsignedLong(sequence) * 0x9E3779B97F4A7C15L);
        double phase = unit(seed + 11) * Math.PI * 2.0;
        double oscillation = Math.sin(progress * Math.PI * 5.0 + phase);
        long step = (long) Math.floor(progress * 6.0);
        double jitter = signed(seed + step * 0xC2B2AE3D27D4EB4FL);
        return envelope * (oscillation * 0.045 + jitter * 0.018);
    }

    private static double criticalFlicker(double gameTime, int sequence, double critical) {
        double speed = 0.65 + critical * 0.95;
        long frame = (long) Math.floor(gameTime * speed);
        long seed = mix(frame * 0xD6E8FEB86659FD93L
                + Integer.toUnsignedLong(sequence) * 0x94D049BB133111EBL);
        double threshold = 0.72 - critical * 0.25;
        double randomFlash = smoothstep((unit(seed) - threshold) / 0.20);
        double phase = unit(seed + 19) * Math.PI * 2.0;
        double strobe = Math.pow(Math.max(0.0,
                Math.sin(gameTime * (0.82 + critical * 0.68) + phase)), 6.0);
        return 0.10 + Math.max(randomFlash, strobe * 0.82) * 0.90;
    }

    public record Effects(double radiusScale, double surfaceDisplacementScale,
                          double surfaceActivityBoost, int dischargeSlotBonus,
                          double dischargeActivityBoost, double dischargeLengthScale,
                          double dischargeWidthScale, double dischargeBranchBoost,
                          double flashIntensity) {}

    private QuantumEnergySphereEffectMath() {}
}
