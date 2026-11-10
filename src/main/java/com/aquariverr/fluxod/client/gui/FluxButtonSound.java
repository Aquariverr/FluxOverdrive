package com.aquariverr.fluxod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import sonar.fluxnetworks.register.RegistrySounds;

public final class FluxButtonSound {

    public static void play(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(RegistrySounds.BUTTON_CLICK.get(), 1.0F));
    }

    public static void play() {
        Minecraft minecraft = Minecraft.getInstance();
        play(minecraft.getSoundManager());
    }

    private FluxButtonSound() {}
}
