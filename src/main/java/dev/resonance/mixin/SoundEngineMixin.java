package dev.resonance.mixin;

import dev.resonance.SoundTracker;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sees every sound at the very top of SoundEngine.play, before the engine checks
 * volume or distance, so muted and out-of-earshot sounds are still captured.
 * Pure read-only: the sound is passed through untouched, and nothing touches networking.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true, require = 0)
    private SoundInstance resonance$capture(SoundInstance sound) {
        try { SoundTracker.onSound(sound); } catch (Throwable t) { SoundTracker.errors++; SoundTracker.lastError = t.toString(); }
        return sound;
    }
}
