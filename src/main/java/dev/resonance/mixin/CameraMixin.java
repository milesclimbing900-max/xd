package dev.resonance.mixin;

import dev.resonance.CameraAccess;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Camera.class)
public abstract class CameraMixin implements CameraAccess {
    @Shadow private Vec3 position;
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Override public Vec3 resonance$pos() { return position; }
    @Override public float resonance$yaw() { return yRot; }
    @Override public float resonance$pitch() { return xRot; }
}
