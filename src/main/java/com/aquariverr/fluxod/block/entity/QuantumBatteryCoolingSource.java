package com.aquariverr.fluxod.block.entity;

import java.util.List;

public interface QuantumBatteryCoolingSource {

    List<CoolingMedium> getCoolingMedia();

    long getMaxCoolingPerTickMilliKelvin();

    long consumeCooling(String channelId, long requestedMilliKelvin);

    record CoolingMedium(String channelId, long targetMilliKelvin, long availableCoolingMilliKelvin) {
        public CoolingMedium {
            if (channelId == null || channelId.isBlank()) {
                throw new IllegalArgumentException("Cooling channel ID must not be blank");
            }
            if (targetMilliKelvin < 0L) {
                throw new IllegalArgumentException("Cooling target must not be negative");
            }
            if (availableCoolingMilliKelvin < 0L) {
                throw new IllegalArgumentException("Available cooling must not be negative");
            }
        }
    }
}
