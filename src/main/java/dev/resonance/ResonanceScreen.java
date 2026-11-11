package dev.resonance;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Apple-inspired settings window. Immediate-mode: every frame the active tab re-declares its rows,
 * and each row draws itself and handles its own input (mouse state is polled from GLFW).
 */
public class ResonanceScreen extends Screen {
    private static final String[] TABS = {"Detection", "ESP", "Island", "Appearance", "About"};
    private static final String[] ICONS = {"♪", "◉", "▬", "◐", "✦"};
    private static final String[] SUBS = {"Hear with your eyes", "Make every block sing", "A pill that listens", "Make it yours", "The idea behind it"};

    private int tab = 0;
    private double scroll = 0, maxScroll = 0;
    private boolean wasDown, down, pressed;
    private String active;
    private final Map<String, Float> anim = new HashMap<>();
    private final Set<String> open = new HashSet<>();
    private final Map<String, float[]> hsv = new HashMap<>();

    private GuiGraphics g;
    private Font f;
    private Config c;
    private Ui.Theme th;
    private int mx, my, cx, cw, ry, clipT, clipB, accent;

    public ResonanceScreen() { super(Component.literal("Resonance")); }

    @Override
    public boolean isPauseScreen() { return false; }

    public void removed() { Config.save(); }

    /** Mouse wheel scrolling (no @Override on purpose: harmless if the signature differs). */
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scroll = Mth.clamp(scroll - sy * 22, 0, maxScroll);
        return true;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        g = gg; mx = mouseX; my = mouseY; f = this.font; c = Config.get();
        th = Ui.theme(c.uiTheme); accent = 0xFF000000 | c.uiAccent;
        long win = GLFW.glfwGetCurrentContext();
        down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        pressed = down && !wasDown;
        wasDown = down;
        if (!down) active = null;
        double now = System.currentTimeMillis() / 1000.0;

        g.fill(0, 0, width, height, 0x59000000);

        int W = Math.min(456, width - 12), H = Math.min(284, height - 12);
        int x0 = (width - W) / 2, y0 = (height - H) / 2, side = 114;
        Ui.shadow(g, x0, y0, W, H, 14, 120);
        Ui.rrect(g, x0 - 1, y0 - 1, W + 2, H + 2, 15, Ui.argb(th.sub & 0xFFFFFF, 55));
        Ui.rrect(g, x0, y0, W, H, 14, th.win);

        // ---- sidebar
        Ui.rrect(g, x0 + 6, y0 + 6, side - 6, H - 12, 10, th.side);
        Ui.rrect(g, x0 + 13, y0 + 13, 26, 26, 6, Ui.argb(c.uiAccent, 80));
        Ui.art(g, 0, x0 + 14, y0 + 14, 24);
        Ui.bold(g, "Resonance", x0 + 44, y0 + 16, th.text);
        Ui.text(g, Ui.fit("see the unheard", side - 50), x0 + 44, y0 + 27, th.sub);
        for (int i = 0; i < TABS.length; i++) {
            int ty = y0 + 48 + i * 23, tw = side - 20, tx = x0 + 11;
            boolean sel = i == tab, hv = mx >= tx && mx < tx + tw && my >= ty && my < ty + 21;
            if (sel) Ui.rrect(g, tx, ty, tw, 21, 7, accent);
            else if (hv) Ui.rrect(g, tx, ty, tw, 21, 7, Ui.argb(th.sub & 0xFFFFFF, 35));
            Ui.text(g, ICONS[i], tx + 8, ty + 6, sel ? 0xFFFFFFFF : accent);
            Ui.text(g, TABS[i], tx + 23, ty + 6, sel ? 0xFFFFFFFF : th.text);
            if (pressed && hv && !sel) { tab = i; scroll = 0; }
        }
        // sidebar equalizer driven by real detected sound
        double act = SoundTracker.activity();
        for (int i = 0; i < 12; i++) {
            int bh = 2 + (int) Math.round(Island.level(i, act) * 18);
            Ui.rrect(g, x0 + 16 + i * 7, y0 + H - 16 - bh, 4, bh, 2, Ui.argb(c.uiAccent, 110 + 140 * Island.level(i, act)));
        }

        // ---- content header
        cx = x0 + side + 12; cw = W - side - 24;
        g.pose().pushMatrix();
        g.pose().translate(cx + 2, y0 + 12);
        g.pose().scale(2f, 2f);
        Ui.bold(g, TABS[tab], 0, 0, th.text);
        g.pose().popMatrix();
        Ui.text(g, Ui.wave(7, now, 0.3 + act * 0.7) + " " + SUBS[tab], cx + 2, y0 + 30, accent);

        // ---- scrolling content
        clipT = y0 + 44; clipB = y0 + H - 8;
        g.enableScissor(cx - 2, clipT, cx + cw + 2, clipB);
        ry = clipT - (int) scroll;
        int startY = ry;
        switch (tab) {
            case 0 -> detection();
            case 1 -> esp();
            case 2 -> island(cx + cw / 2);
            case 3 -> look();
            default -> about();
        }
        int total = ry - startY;
        g.disableScissor();
        maxScroll = Math.max(0, total - (clipB - clipT));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        if (maxScroll > 0) {
            int bh = Math.max(16, (int) ((clipB - clipT) * (clipB - clipT) / (double) total));
            int by = clipT + (int) ((clipB - clipT - bh) * (scroll / maxScroll));
            Ui.rrect(g, cx + cw + 3, by, 3, bh, 1, Ui.argb(th.sub & 0xFFFFFF, 110));
        }
    }

    // ================= tabs =================

    private void detection() {
        header("Detection");
        toggle("Detect sounds", () -> c.enabled, v -> c.enabled = v);
        slider("Range", 8, 128, 1, () -> c.range, v -> c.range = (int) v, " blocks");
        slider("Marker lifetime", 1, 15, 0.5, () -> c.lifetime, v -> c.lifetime = v, " s");
        slider("Max markers", 4, 40, 1, () -> c.maxMarkers, v -> c.maxMarkers = (int) v, "");
        slider("Ignore my footsteps & hits within", 0, 6, 0.5, () -> c.ignoreSelf, v -> c.ignoreSelf = v, " blocks");
        header("Sounds to show — muted ones included");
        for (int i = 0; i < Config.CATS.length; i++) {
            final int k = i;
            toggle(Config.GLYPH[i] + "  " + Config.CATS[i], () -> c.catOn[k], v -> c.catOn[k] = v);
        }
        header("Diagnostics");
        para("Sounds seen by hook: " + SoundTracker.seen + "  ·  accepted: " + SoundTracker.accepted + "  ·  overlay frames: " + Resonance.hudFrames
                + "  ·  markers: " + SoundTracker.snapshot().size() + "\nLast rejected: " + SoundTracker.lastReject + "\nHook error: " + SoundTracker.lastError);
        header("Try it");
        button("♪  Drop a preview marker ahead of me", SoundTracker::preview);
        button("✕  Clear all markers", SoundTracker::clear);
    }

    private void esp() {
        header("Fill");
        toggle("Show fill", () -> c.fillOn, v -> c.fillOn = v);
        color("Fill colour", () -> c.fillColor, v -> c.fillColor = v);
        slider("Fill opacity", 0, 100, 1, () -> c.fillAlpha, v -> c.fillAlpha = (int) v, "%");
        header("Outline");
        toggle("Show outline", () -> c.outlineOn, v -> c.outlineOn = v);
        color("Outline colour", () -> c.outlineColor, v -> c.outlineColor = v);
        slider("Outline opacity", 0, 100, 1, () -> c.outlineAlpha, v -> c.outlineAlpha = (int) v, "%");
        slider("Outline thickness", 1, 5, 1, () -> c.lineWidth, v -> c.lineWidth = (int) v, " px");
        choice("Outline style", new String[]{"Full", "Corners", "Hull"}, () -> c.outlineMode, v -> c.outlineMode = v);
        header("Colours by sound type");
        toggle("Colour by sound type", () -> c.colorByCategory, v -> c.colorByCategory = v);
        if (c.colorByCategory)
            for (int i = 0; i < Config.CATS.length; i++) {
                final int k = i;
                color(Config.GLYPH[i] + "  " + Config.CATS[i], () -> c.catColor[k], v -> c.catColor[k] = v);
            }
        header("Motion");
        choice("Fade", new String[]{"Smooth", "Linear", "Pulse"}, () -> c.fadeMode, v -> c.fadeMode = v);
        toggle("Shrink while fading", () -> c.shrink, v -> c.shrink = v);
        toggle("Soft edges", () -> c.softEdges, v -> c.softEdges = v);
        header("Labels");
        toggle("Show labels", () -> c.labels, v -> c.labels = v);
        choice("Label text", new String[]{"Friendly", "Sound ID"}, () -> c.labelMode, v -> c.labelMode = v);
        toggle("Show distance", () -> c.showDistance, v -> c.showDistance = v);
    }

    private void island(int midX) {
        int ph = c.islandH + 30;
        int y = row(ph + 4);
        Ui.rrect(g, cx, y, cw, ph, 8, c.uiTheme == 1 ? 0xFF3A3A3C : 0xFFD9D9DE);
        Ui.text(g, "LIVE PREVIEW", cx + 8, y + 5, Ui.argb(0x000000, 90));
        g.pose().pushMatrix();
        float s = Math.min(1f, (cw - 12f) / c.islandW);
        g.pose().translate(midX, y + 15);
        g.pose().scale(s, s);
        Island.render(g, 0, 0, true);
        g.pose().popMatrix();

        header("Island");
        toggle("Show the island", () -> c.islandOn, v -> c.islandOn = v);
        choice("Style", new String[]{"Glass", "Midnight", "Vinyl", "Custom"}, () -> c.islandStyle, v -> c.islandStyle = v);
        header("Shape");
        slider("Width", 120, 320, 1, () -> c.islandW, v -> c.islandW = (int) v, "");
        slider("Height", 26, 64, 1, () -> c.islandH, v -> c.islandH = (int) v, "");
        slider("Corner radius", 0, 32, 1, () -> c.islandRadius, v -> c.islandRadius = (int) v, "");
        slider("Bevel", 0, 5, 1, () -> c.islandBevel, v -> c.islandBevel = (int) v, "");
        toggle("Soft shadow", () -> c.islandShadow, v -> c.islandShadow = v);
        header("Colours");
        color("Accent", () -> c.islandAccent, v -> c.islandAccent = v);
        if (c.islandStyle == 3) {
            color("Background", () -> c.islandBg, v -> c.islandBg = v);
            slider("Background opacity", 20, 100, 1, () -> c.islandBgAlpha, v -> c.islandBgAlpha = (int) v, "%");
            color("Text", () -> c.islandText, v -> c.islandText = v);
        }
        header("Content");
        choice("Artwork", new String[]{"Headphones", "Lo-fi", "Note"}, () -> c.islandArt, v -> c.islandArt = v);
        toggle("Equalizer bars", () -> c.islandWave, v -> c.islandWave = v);
        toggle("Expand when a sound plays", () -> c.islandExpand, v -> c.islandExpand = v);
        header("Position");
        choice("Anchor", new String[]{"Top", "Bottom"}, () -> c.islandPos, v -> c.islandPos = v);
        slider("Horizontal offset", -300, 300, 1, () -> c.islandX, v -> c.islandX = (int) v, "");
        slider("Distance from edge", 0, 200, 1, () -> c.islandY, v -> c.islandY = (int) v, "");
    }

    private void look() {
        header("Menu theme");
        choice("Theme", new String[]{"Light", "Dark", "Rose"}, () -> c.uiTheme, v -> c.uiTheme = v);
        color("Accent colour", () -> c.uiAccent, v -> c.uiAccent = v);
        slider("Text size (0 = auto)", 0, 4, 1, () -> c.textPx, v -> c.textPx = (int) v, "");
        header("Controls");
        para("Rebind the open-menu and toggle keys in Options ▸ Controls ▸ Key Binds ▸ Resonance. Default: Right Shift.");
        para("Commands: /sounddetector, /sounddetector toggle, /sounddetector clear");
    }

    private void about() {
        int y = row(66);
        Ui.rrect(g, cx, y, cw, 62, 8, th.card);
        Ui.art(g, 0, cx + 6, y + 6, 50);
        Ui.art(g, 1, cx + 62, y + 6, 50);
        Ui.bold(g, "Now Playing", cx + 120, y + 10, th.text);
        Ui.text(g, Ui.fit("the world, as a waveform", cw - 128), cx + 120, y + 22, th.sub);
        Ui.text(g, Ui.wave(14, System.currentTimeMillis() / 1000.0, 0.5 + SoundTracker.activity() * 0.5), cx + 120, y + 38, accent);
        header("Resonance");
        para("Resonance turns every sound the game asks to play into something you can see: a glowing block outline through walls, a label naming the sound, and a live island. Sounds you have muted still show up.");
        para("It reads sounds on your own computer only. It sends nothing and changes nothing on the server, and it ignores the sounds you make yourself.");
        para("Limit: a sound the server never sends to your client can't be shown.");
    }

    // ================= widgets =================

    private int row(int h) { int y = ry; ry += h; return y; }

    private boolean hov(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipT && my < clipB;
    }

    private float an(String id, float target) {
        float v = anim.getOrDefault(id, target);
        v += (target - v) * 0.3f;
        anim.put(id, v);
        return v;
    }

    private void tile(int y, int h, boolean hv) { Ui.rrect(g, cx, y, cw, h, 7, hv ? th.cardHi : th.card); }

    private void header(String s) {
        int y = row(19);
        Ui.text(g, Ui.fit(s.toUpperCase(), cw), cx + 4, y + 9, th.sub);
    }

    private void para(String s) {
        double sc = (double) Ui.tp() / Ui.gs();
        var ls = f.split(Component.literal(s), (int) ((cw - 12) / sc));
        int lh = (int) Math.ceil(10 * sc) + 1, h = ls.size() * lh + 8;
        int y = row(h + 3);
        Ui.rrect(g, cx, y, cw, h, 7, th.card);
        int ly = y + 5;
        for (FormattedCharSequence l : ls) { Ui.seq(g, l, cx + 6, ly, th.text); ly += lh; }
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        int y = row(24); boolean v = get.getAsBoolean(), hv = hov(cx, y, cw, 22);
        tile(y, 22, hv);
        Ui.text(g, Ui.fit(label, cw - 56), cx + 8, y + 7, th.text);
        float a = an("t:" + tab + label, v ? 1 : 0);
        int sx = cx + cw - 36, sy = y + 3;
        Ui.rrect(g, sx, sy, 28, 16, 8, Ui.mix(th.off, accent, a));
        int kx = sx + 2 + Math.round(12 * a);
        Ui.rrect(g, kx - 1, sy + 2, 14, 14, 7, Ui.argb(0x000000, 40));
        Ui.rrect(g, kx, sy + 2, 12, 12, 6, 0xFFFFFFFF);
        if (pressed && hv) { set.accept(!v); }
    }

    private void slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, String unit) {
        slider("s:" + tab + ":" + label, label, min, max, step, get, set, unit);
    }

    private void slider(String id, String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, String unit) {
        int y = row(30);
        tile(y, 28, false);
        int tx = cx + 8, tw = cw - 16, ty = y + 19;
        if (pressed && hov(cx + 2, y + 10, cw - 4, 18)) active = id;
        if (id.equals(active) && down) {
            double nf = Mth.clamp((mx - tx) / (double) tw, 0, 1);
            double nv = Math.round((min + nf * (max - min)) / step) * step;
            set.accept(Mth.clamp(nv, min, max));
        }
        double v = get.getAsDouble();
        float fr = (float) Mth.clamp((v - min) / (max - min), 0, 1);
        String val = (step >= 1 ? String.valueOf((int) Math.round(v)) : String.format("%.1f", v)) + unit;
        Ui.text(g, Ui.fit(label, cw - 70), cx + 8, y + 5, th.text);
        Ui.text(g, val, cx + cw - 8 - Ui.tw(val), y + 5, th.sub);
        Ui.rrect(g, tx, ty, tw, 4, 2, th.off);
        int fw = Math.round(tw * fr);
        if (fw > 0) Ui.rrect(g, tx, ty, Math.max(4, fw), 4, 2, accent);
        int kx = tx + fw - 5;
        Ui.rrect(g, kx - 1, ty - 4, 12, 12, 6, Ui.argb(0x000000, 40));
        Ui.rrect(g, kx, ty - 3, 10, 10, 5, 0xFFFFFFFF);
    }

    private void choice(String label, String[] opts, IntSupplier get, IntConsumer set) {
        int y = row(24); boolean hv = hov(cx, y, cw, 22);
        tile(y, 22, false);
        int segW = Math.min(cw - 110, opts.length * 54), sx = cx + cw - segW - 5, n = opts.length, cur = get.getAsInt();
        Ui.text(g, Ui.fit(label, cw - segW - 20), cx + 8, y + 7, th.text);
        Ui.rrect(g, sx, y + 3, segW, 16, 6, th.seg);
        for (int i = 0; i < n; i++) {
            int x1 = sx + segW * i / n, x2 = sx + segW * (i + 1) / n;
            boolean sel = i == cur;
            if (sel) {
                Ui.rrect(g, x1 + 1, y + 4, x2 - x1 - 1, 14, 5, Ui.argb(0x000000, 28));
                Ui.rrect(g, x1 + 1, y + 3, x2 - x1 - 1, 14, 5, th.segSel);
            }
            String s = Ui.fit(opts[i], x2 - x1 - 4);
            Ui.text(g, s, (x1 + x2) / 2 - Ui.tw(s) / 2, y + 7, sel ? th.text : th.sub);
            if (pressed && hov(x1, y + 3, x2 - x1, 16)) set.accept(i);
        }
    }

    private void color(String label, IntSupplier get, IntConsumer set) {
        int y = row(24); String id = "c:" + tab + ":" + label;
        boolean hv = hov(cx, y, cw, 22);
        tile(y, 22, hv);
        int rgb = get.getAsInt();
        Ui.text(g, Ui.fit(label, cw - 110), cx + 8, y + 7, th.text);
        int sx = cx + cw - 32;
        Ui.rrect(g, sx - 1, y + 3, 26, 16, 6, Ui.argb(0x000000, 50));
        Ui.rrect(g, sx, y + 4, 24, 14, 5, 0xFF000000 | rgb);
        String hex = String.format("#%06X", rgb & 0xFFFFFF);
        Ui.text(g, hex, sx - 6 - Ui.tw(hex), y + 7, th.sub);
        if (pressed && hv) {
            if (!open.remove(id)) { open.add(id); hsv.put(id, Ui.rgb2hsv(rgb)); }
        }
        if (open.contains(id)) {
            float[] h = hsv.computeIfAbsent(id, k -> Ui.rgb2hsv(get.getAsInt()));
            slider(id + "h", "Hue", 0, 360, 1, () -> h[0] * 360, v -> { h[0] = (float) v / 360f; set.accept(Mth.hsvToRgb(h[0], h[1], h[2])); }, "°");
            slider(id + "s", "Saturation", 0, 100, 1, () -> h[1] * 100, v -> { h[1] = (float) v / 100f; set.accept(Mth.hsvToRgb(h[0], h[1], h[2])); }, "%");
            slider(id + "b", "Brightness", 0, 100, 1, () -> h[2] * 100, v -> { h[2] = (float) v / 100f; set.accept(Mth.hsvToRgb(h[0], h[1], h[2])); }, "%");
        }
    }

    private void button(String label, Runnable r) {
        int y = row(24); boolean hv = hov(cx, y, cw, 22);
        tile(y, 22, hv);
        Ui.text(g, Ui.fit(label, cw - 12), cx + 8, y + 7, accent);
        if (pressed && hv) r.run();
    }
}
