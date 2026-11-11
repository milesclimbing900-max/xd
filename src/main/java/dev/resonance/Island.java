package dev.resonance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** The optional "dynamic island": a floating pill that expands when sound is detected. */
public final class Island {
    private static double ex = 0;
    private static long lastNs = System.nanoTime();

    /** Equalizer bar height 0..1 for bar i. */
    public static double level(int i, double act) {
        double t = System.currentTimeMillis() / 1000.0;
        double live = act * (0.3 + 0.7 * Math.abs(Math.sin(t * 5.2 + i * 1.7 + Math.sin(i * 2.1) * 2)));
        double idle = 0.14 + 0.08 * Math.sin(t * 1.6 + i * 0.9);
        return Mth.clamp(Math.max(live, idle), 0, 1);
    }

    public static void hud(GuiGraphics g) {
        Config c = Config.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.islandOn || mc.player == null || mc.screen instanceof ResonanceScreen) return;
        int cx = g.guiWidth() / 2 + c.islandX;
        int top = c.islandPos == 0 ? c.islandY : g.guiHeight() - c.islandH - c.islandY;
        render(g, cx, top, false);
    }

    public static void render(GuiGraphics g, int cx, int top, boolean preview) {
        Config c = Config.get();
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (System.nanoTime() - lastNs) / 1e9);
        lastNs = System.nanoTime();

        boolean hasLast = SoundTracker.lastTime > 0;
        boolean on = preview || (hasLast && now - SoundTracker.lastTime < 2600);
        ex += ((!c.islandExpand || on ? 1 : 0) - ex) * (1 - Math.exp(-dt * 9));
        double e = ex * ex * (3 - 2 * ex);
        double act = preview && !hasLast ? 0.7 : SoundTracker.activity();

        int H = c.islandH, W0 = c.islandW;
        int idleW = (int) Math.max(H * 2.6, W0 * 0.6);
        int w = (int) Math.round(idleW + (W0 - idleW) * e);
        int h = H + (int) Math.round(act * 2);
        int x = cx - w / 2, y = top, r = Math.min(c.islandRadius, h / 2);

        int bg, txt, sub, hi, lo;
        double bgA = 235;
        switch (c.islandStyle) {
            case 1 -> { bg = 0x1C1C1E; txt = 0xF5F5F7; sub = 0x98989D; hi = Ui.argb(0xFFFFFF, 46); lo = Ui.argb(0x000000, 110); }
            case 2 -> { bg = 0xF7DDE2; txt = 0x4A2C3A; sub = 0x9A7384; hi = Ui.argb(0xFFFFFF, 190); lo = Ui.argb(0x8A4A5E, 40); }
            case 3 -> { bg = c.islandBg; txt = c.islandText; sub = Ui.mix(0xFF000000 | c.islandText, 0xFF000000 | c.islandBg, 0.45) & 0xFFFFFF; bgA = c.islandBgAlpha * 2.55; hi = Ui.argb(0xFFFFFF, 120); lo = Ui.argb(0x000000, 60); }
            default -> { bg = 0xF5F5F7; txt = 0x1D1D1F; sub = 0x6E6E73; bgA = 235; hi = Ui.argb(0xFFFFFF, 220); lo = Ui.argb(0x000000, 28); }
        }
        int accent = c.islandAccent;

        if (c.islandShadow) Ui.shadow(g, x, y, w, h, r, 70);
        Ui.rrect(g, x, y, w, h, r, Ui.argb(bg, bgA));
        if (c.islandBevel > 0) Ui.bevel(g, x, y, w, h, r, c.islandBevel, hi, lo);

        int a = Math.round(h * 0.62f), ax = x + (h - a) / 2, ay = y + (h - a) / 2;
        if (c.islandArt < 2) {
            Ui.rrect(g, ax - 1, ay - 1, a + 2, a + 2, 4, Ui.argb(accent, 90));
            Ui.art(g, c.islandArt, ax, ay, a);
        } else {
            Ui.rrect(g, ax, ay, a, a, a / 2, 0xFF000000 | accent);
            Ui.text(g, "♪", ax + a / 2 - 3, ay + a / 2 - 4, 0xFFFFFFFF);
        }

        int waveW = c.islandWave && w > H * 3 ? 7 * 5 - 2 : 0;
        int tx = ax + a + 7, tr = x + w - 9 - (waveW > 0 ? waveW + 6 : 0), room = tr - tx;
        String title, line;
        if (on) {
            String id = hasLast ? SoundTracker.lastId : "block.chest.open";
            title = SoundTracker.friendly(id);
            line = Config.GLYPH[hasLast ? SoundTracker.lastCat : 0] + " " + (hasLast && SoundTracker.lastMuted ? "muted · " : "") + id;
        } else {
            title = "Resonance";
            line = Ui.wave(6, now / 1000.0, 0.45) + " listening";
        }
        int ty = y + (h - 19) / 2;
        if (room > 24) {
            Ui.bold(g, Ui.fit(title, room), tx, ty, 0xFF000000 | txt);
            Ui.text(g, Ui.fit(line, room), tx, ty + 10, 0xFF000000 | sub);
        }

        if (waveW > 0) {
            int bx = x + w - 9 - waveW;
            for (int i = 0; i < 7; i++) {
                int bh = 3 + (int) Math.round(level(i, act) * (h * 0.5 - 3));
                Ui.rrect(g, bx + i * 5, y + (h - bh) / 2, 3, bh, 1, Ui.argb(accent, 235));
            }
        }
    }
}
