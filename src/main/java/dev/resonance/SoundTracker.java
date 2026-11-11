package dev.resonance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class SoundTracker {
    public static final class Marker {
        public double x, y, z;
        public int bx, by, bz, cat, count = 1;
        public String id;
        public boolean muted;
        public long first, born;
    }

    private static final List<Marker> LIST = new ArrayList<>();
    public static int seen, accepted, errors;
    public static String lastReject = "-", lastError = "none";
    public static String lastId = "";
    public static long lastTime = 0;
    public static boolean lastMuted;
    public static double lastDist;
    public static int lastCat = 7;

    public static int category(String id) {
        if (id.startsWith("block.")) return 0;
        if (id.startsWith("entity.player.")) return 2;
        if (id.startsWith("entity.")) return 1;
        if (id.startsWith("item.")) return 3;
        if (id.startsWith("ambient.")) return 4;
        if (id.startsWith("weather.")) return 5;
        if (id.startsWith("music_disc.") || id.startsWith("record.")) return 6;
        return 7;
    }

    public static String friendly(String id) {
        String[] p = id.split("\\.");
        if (p.length <= 1) return title(id);
        if (p.length == 2) return title(p[1]);
        StringBuilder mid = new StringBuilder();
        for (int i = 1; i < p.length - 1; i++) mid.append(i > 1 ? " " : "").append(p[i]);
        return title(mid.toString()) + " · " + title(p[p.length - 1]);
    }

    private static String title(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char ch : s.replace('_', ' ').toCharArray()) {
            b.append(up ? Character.toUpperCase(ch) : ch);
            up = ch == ' ';
        }
        return b.toString();
    }

    /** Called from the mixin for every sound the engine is asked to play. */
    public static void onSound(SoundInstance s) {
        Config c = Config.get();
        Minecraft mc = Minecraft.getInstance();
        seen++;
        if (!c.enabled || mc.player == null) { lastReject = "disabled/no player"; return; }
        if (s.isRelative()) { lastReject = "relative sound"; return; }
        String id = s.getIdentifier().getPath();
        if (id.startsWith("ui.") || id.startsWith("music.") || id.startsWith("menu.")) { lastReject = "ui/music " + id; return; }
        double x = s.getX(), y = s.getY(), z = s.getZ();
        var p = mc.player;
        double feet = Math.sqrt(p.distanceToSqr(x, y, z));
        double eye = Math.sqrt(p.distanceToSqr(x, y - p.getEyeHeight(), z));
        boolean selfLike = id.startsWith("entity.player.") || id.endsWith(".step") || id.endsWith(".fall") || id.endsWith(".hit")
                || id.contains("swim") || id.contains("splash") || id.startsWith("item.armor");
        if (selfLike && Math.min(feet, eye) < c.ignoreSelf) { lastReject = "own sound " + id; return; }
        if (feet > c.range) { lastReject = "out of range " + id; return; }
        int cat = category(id);
        if (!c.catOn[cat]) { lastReject = "category off " + id; return; }
        boolean muted = s.getVolume() <= 0.001f
                || mc.options.getSoundSourceVolume(s.getSource()) <= 0.001f
                || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.001f;
        accepted++;
        add(id, x, y, z, cat, muted, feet);
    }

    private static void add(String id, double x, double y, double z, int cat, boolean muted, double dist) {
        long now = System.currentTimeMillis();
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        synchronized (LIST) {
            Marker m = null;
            for (Marker o : LIST) if (o.bx == bx && o.by == by && o.bz == bz && o.id.equals(id)) { m = o; break; }
            if (m == null) {
                m = new Marker();
                m.id = id; m.bx = bx; m.by = by; m.bz = bz; m.cat = cat; m.first = now;
                LIST.add(m);
                if (LIST.size() > 300) LIST.remove(0);
            } else m.count++;
            m.x = x; m.y = y; m.z = z; m.muted = muted; m.born = now;
        }
        lastId = id; lastTime = now; lastMuted = muted; lastDist = dist; lastCat = cat;
    }

    /** Menu button: drops a sample marker a few blocks ahead so you can tune the look. */
    public static void preview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var look = mc.player.getLookAngle();
        add("block.chest.open", mc.player.getX() + look.x * 4, mc.player.getY() + 0.2, mc.player.getZ() + look.z * 4, 0, false, 4);
    }

    public static void clear() { synchronized (LIST) { LIST.clear(); } }

    public static void prune() {
        long now = System.currentTimeMillis();
        long life = (long) (Config.get().lifetime * 1000);
        synchronized (LIST) { LIST.removeIf(m -> now - m.born > life); }
    }

    public static List<Marker> snapshot() { synchronized (LIST) { return new ArrayList<>(LIST); } }

    /** 1 = fully visible, 0 = gone. Eased so markers glide in and out. */
    public static float fade(Marker m, Config c, long now) {
        double life = c.lifetime * 1000, age = now - m.born;
        if (age >= life) return 0;
        double t = age / life, f;
        switch (c.fadeMode) {
            case 1 -> f = 1 - t;
            case 2 -> f = (1 - t * t) * (0.82 + 0.18 * Math.sin(age / 110.0));
            default -> { double u = Mth.clamp((t - 0.35) / 0.65, 0, 1); f = 1 - u * u * (3 - 2 * u); }
        }
        double in = Math.min(1, (now - m.first) / 160.0);
        return (float) (f * in * in * (3 - 2 * in));
    }

    /** 0..1 burst that decays after the latest sound; drives the equalizers. */
    public static double activity() {
        if (lastTime == 0) return 0;
        return Math.exp(-(System.currentTimeMillis() - lastTime) / 900.0);
    }
}
