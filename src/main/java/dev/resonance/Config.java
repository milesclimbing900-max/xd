package dev.resonance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** All saved settings. Percent values are 0-100. Colours are 0xRRGGBB. */
public final class Config {
    public static final String[] CATS = {"Blocks", "Mobs", "Players", "Items", "Ambient", "Weather", "Music discs", "Other"};
    public static final String[] GLYPH = {"▣", "☻", "☺", "✦", "☁", "☂", "♫", "♪"};

    // detection
    public boolean enabled = true;
    public int range = 48, maxMarkers = 14;
    public double lifetime = 4.0, ignoreSelf = 2.0;
    public boolean[] catOn = {true, true, true, true, true, true, true, true};

    // esp
    public boolean fillOn = true, outlineOn = true, colorByCategory = true, shrink = true, labels = true, showDistance = true, softEdges = true;
    public int fillColor = 0x0A84FF, fillAlpha = 26, outlineColor = 0xFFFFFF, outlineAlpha = 95, lineWidth = 1;
    public int outlineMode = 0, fadeMode = 0, detail = 2, labelMode = 0, textPx = 0;
    public int[] catColor = {0xFFB020, 0x34C759, 0x0A84FF, 0xAF52DE, 0x5AC8FA, 0x8E8E93, 0xFF2D55, 0xFF9F0A};

    // island
    public boolean islandOn = true, islandWave = true, islandExpand = true, islandShadow = true;
    public int islandStyle = 0, islandW = 210, islandH = 40, islandRadius = 20, islandBevel = 1;
    public int islandBg = 0xF5F5F7, islandBgAlpha = 92, islandAccent = 0x0A84FF, islandText = 0x1D1D1F;
    public int islandArt = 0, islandPos = 0, islandX = 0, islandY = 8;

    // menu
    public int uiTheme = 0, uiAccent = 0x0A84FF;

    private static Config I = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config get() { return I; }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("resonance.json"); }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Config c = GSON.fromJson(Files.readString(f), Config.class);
                if (c != null) I = c;
            }
        } catch (Throwable ignored) { }
        I.fix();
        save();
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(I)); } catch (Throwable ignored) { }
    }

    private void fix() {
        Config d = new Config();
        if (catOn == null || catOn.length != 8) catOn = d.catOn;
        if (catColor == null || catColor.length != 8) catColor = d.catColor;
        detail = Math.max(1, Math.min(3, detail));
    }
}
