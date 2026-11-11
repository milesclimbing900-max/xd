package dev.resonance.mixin;

import dev.resonance.SoundTracker;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Second, independent hook so detection never depends on a single method. Duplicates merge into one marker. */
@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true, require = 0)
    private SoundInstance resonance$capture2(SoundInstance sound) {
        try { SoundTracker.onSound(sound); } catch (Throwable t) { SoundTracker.errors++; SoundTracker.lastError = t.toString(); }
        return sound;
    }
}
