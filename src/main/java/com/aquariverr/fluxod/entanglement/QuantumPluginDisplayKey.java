package com.aquariverr.fluxod.entanglement;

public record QuantumPluginDisplayKey(int ringTier, QuantumPluginLocation location,
                                      String localEndpointId) implements Comparable<QuantumPluginDisplayKey> {

    @Override
    public int compareTo(QuantumPluginDisplayKey other) {
        int ringComparison = Integer.compare(ringTier, other.ringTier);
        if (ringComparison != 0) return ringComparison;
        int locationComparison = Integer.compare(location.ordinal(), other.location.ordinal());
        if (locationComparison != 0) return locationComparison;
        return localEndpointId.compareTo(other.localEndpointId);
    }
}
