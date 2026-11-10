package com.aquariverr.fluxod.integration.gtceu;

final class GtceuEnergyMath {

    static long euToExtract(long requestedFe, long storedEu, long outputVoltage, long outputAmperage) {
        if (requestedFe < 4 || storedEu <= 0 || outputVoltage <= 0 || outputAmperage <= 0) return 0;
        long outputLimit = saturatedMultiply(outputVoltage, outputAmperage);
        return Math.min(requestedFe / 4, Math.min(storedEu, outputLimit));
    }

    static long toFe(long eu) {
        if (eu <= 0) return 0;
        return eu > Long.MAX_VALUE / 4 ? Long.MAX_VALUE : eu * 4;
    }

    private static long saturatedMultiply(long first, long second) {
        if (first > Long.MAX_VALUE / second) return Long.MAX_VALUE;
        return first * second;
    }

    private GtceuEnergyMath() {
    }
}
