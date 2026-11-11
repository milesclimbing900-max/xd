package dev.resonance;

import net.minecraft.world.phys.Vec3;

/** Lets the overlay read the real render camera (works with freecam and third person). */
public interface CameraAccess {
    Vec3 resonance$pos();
    float resonance$yaw();
    float resonance$pitch();
}
