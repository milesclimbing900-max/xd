package dev.resonance;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class Resonance implements ClientModInitializer {
    public static KeyMapping OPEN, TOGGLE;
    private static boolean openNext;
    public static int hudFrames;

    @Override
    public void onInitializeClient() {
        Config.load();

        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("resonance", "main"));
        OPEN = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.resonance.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.resonance.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) mc.setScreen(new ResonanceScreen());
            while (TOGGLE.consumeClick()) toggle(mc);
            if (openNext) { openNext = false; mc.setScreen(new ResonanceScreen()); }
            SoundTracker.prune();
        });

        HudRenderCallback.EVENT.register((g, dt) -> {
            hudFrames++;
            EspRenderer.render(g, dt.getGameTimeDeltaPartialTick(true));
            Island.hud(g);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
                ClientCommandManager.literal("sounddetector")
                        .executes(x -> { openNext = true; return Command.SINGLE_SUCCESS; })
                        .then(ClientCommandManager.literal("toggle").executes(x -> { toggle(Minecraft.getInstance()); return Command.SINGLE_SUCCESS; }))
                        .then(ClientCommandManager.literal("clear").executes(x -> { SoundTracker.clear(); return Command.SINGLE_SUCCESS; }))));
    }

    private static void toggle(Minecraft mc) {
        Config c = Config.get();
        c.enabled = !c.enabled;
        Config.save();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("♪ Resonance " + (c.enabled ? "on" : "off")), true);
    }
}
