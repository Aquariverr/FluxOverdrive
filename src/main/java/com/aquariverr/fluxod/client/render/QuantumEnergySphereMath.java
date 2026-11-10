package com.aquariverr.fluxod.client.render;

final class QuantumEnergySphereMath {

    static double smoothstep(double value) {
        double x = Math.clamp(value, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    static long mix(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    static double unit(long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    static double signed(long value) {
        return unit(value) * 2.0 - 1.0;
    }

    private QuantumEnergySphereMath() {}
}
