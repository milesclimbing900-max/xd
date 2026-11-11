package dev.resonance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;

/** Small drawing toolkit: rounded rectangles, soft shadows, bevels, text helpers. */
public final class Ui {
    public static final Identifier[] ART = {
            Identifier.fromNamespaceAndPath("resonance", "textures/gui/art_red.png"),
            Identifier.fromNamespaceAndPath("resonance", "textures/gui/art_lofi.png")};

    public static final class Theme {
        public final int win, side, card, cardHi, text, sub, seg, segSel, off;
        Theme(int win, int side, int card, int cardHi, int text, int sub, int seg, int segSel, int off) {
            this.win = win; this.side = side; this.card = card; this.cardHi = cardHi; this.text = text;
            this.sub = sub; this.seg = seg; this.segSel = segSel; this.off = off;
        }
    }

    private static final Theme LIGHT = new Theme(0xFFF2F2F7, 0xFFE8E8EE, 0xFFFFFFFF, 0xFFF7F7FA, 0xFF1D1D1F, 0xFF6E6E73, 0xFFE3E3E8, 0xFFFFFFFF, 0xFFD1D1D6);
    private static final Theme DARK = new Theme(0xFF1C1C1E, 0xFF262628, 0xFF2C2C2E, 0xFF3A3A3C, 0xFFF5F5F7, 0xFF98989D, 0xFF3A3A3C, 0xFF636366, 0xFF48484A);
    private static final Theme ROSE = new Theme(0xFFFBF1F2, 0xFFF4DEE2, 0xFFFFFFFF, 0xFFFFF5F6, 0xFF3B2A33, 0xFF8A6B78, 0xFFF0DADF, 0xFFFFFFFF, 0xFFE3CBD1);

    public static Theme theme(int i) { return i == 1 ? DARK : i == 2 ? ROSE : LIGHT; }

    public static int argb(int rgb, double a) { return (Mth.clamp((int) Math.round(a), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    public static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static int gs() { return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale())); }

    /** Physical screen pixels per font pixel. Auto: one step below the GUI scale so text is crisper, never below 1. */
    public static int tp() {
        int t = Config.get().textPx, gs = gs();
        return t > 0 ? t : (gs <= 2 ? gs : gs - 1);
    }

    public static int tw(String s) { return (int) Math.ceil(font().width(s) * (double) tp() / gs()); }

    /** Runs a draw call with the pose set so one font pixel = tp() physical pixels, top-left at GUI (x,y). */
    public static void at(GuiGraphics g, int x, int y, Runnable r) {
        int gs = gs(), tp = tp();
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        g.pose().translate(x * gs, y * gs + Math.round(4f * (gs - tp)));
        g.pose().scale(tp, tp);
        r.run();
        g.pose().popMatrix();
    }

    public static void seq(GuiGraphics g, net.minecraft.util.FormattedCharSequence q, int x, int y, int color) {
        at(g, x, y, () -> g.drawString(font(), q, 0, 0, color, false));
    }

    /** Rounded rectangle drawn at true screen-pixel resolution (smooth at any GUI scale). */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        int R = gs();
        g.pose().pushMatrix();
        g.pose().scale(1f / R, 1f / R);
        rrectPx(g, x * R, y * R, w * R, h * R, r * R, c);
        g.pose().popMatrix();
    }

    private static void rrectPx(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (w <= 0 || h <= 0) return;
        if (r <= 0) { g.fill(x, y, x + w, y + h, c); return; }
        g.fill(x, y + r, x + w, y + h - r, c);
        int a = c >>> 24;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5, ins = r - Math.sqrt((double) r * r - dy * dy);
            int x0 = (int) Math.ceil(ins);
            g.fill(x + x0, y + i, x + w - x0, y + i + 1, c);
            g.fill(x + x0, y + h - 1 - i, x + w - x0, y + h - i, c);
            int pa = (int) (a * (x0 - ins));
            if (x0 > 0 && pa > 3) {
                int pc = (pa << 24) | (c & 0xFFFFFF);
                g.fill(x + x0 - 1, y + i, x + x0, y + i + 1, pc);
                g.fill(x + w - x0, y + i, x + w - x0 + 1, y + i + 1, pc);
                g.fill(x + x0 - 1, y + h - 1 - i, x + x0, y + h - i, pc);
                g.fill(x + w - x0, y + h - 1 - i, x + w - x0 + 1, y + h - i, pc);
            }
        }
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r, int strength) {
        for (int i = 5; i >= 1; i--)
            rrect(g, x - i, y - i + 3, w + 2 * i, h + 2 * i, r + i, argb(0x000000, strength / (double) (i * 1.4 + 1)));
    }

    /** Inner highlight (top/left) and shade (bottom/right) along the straight edges. */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int r, int n, int hi, int lo) {
        int R = gs();
        g.pose().pushMatrix();
        g.pose().scale(1f / R, 1f / R);
        bevelPx(g, x * R, y * R, w * R, h * R, Math.min(r, Math.min(w, h) / 2) * R, n * Math.max(1, R / 2), hi, lo);
        g.pose().popMatrix();
    }

    private static void bevelPx(GuiGraphics g, int x, int y, int w, int h, int r, int n, int hi, int lo) {
        for (int k = 0; k < n; k++) {
            g.fill(x + r, y + k, x + w - r, y + k + 1, hi);
            g.fill(x + k, y + r, x + k + 1, y + h - r, hi);
            g.fill(x + r, y + h - 1 - k, x + w - r, y + h - k, lo);
            g.fill(x + w - 1 - k, y + r, x + w - k, y + h - r, lo);
        }
    }

    public static void art(GuiGraphics g, int idx, int x, int y, int s) {
        if (idx < 0 || idx > 1) return;
        g.blit(RenderPipelines.GUI_TEXTURED, ART[idx], x, y, 0f, 0f, s, s, 128, 128, 128, 128);
    }

    public static Font font() { return Minecraft.getInstance().font; }

    public static void text(GuiGraphics g, String s, int x, int y, int color) { at(g, x, y, () -> g.drawString(font(), s, 0, 0, color, false)); }

    public static void bold(GuiGraphics g, String s, int x, int y, int color) {
        at(g, x, y, () -> g.drawString(font(), Component.literal(s).withStyle(ChatFormatting.BOLD), 0, 0, color, false));
    }

    public static String fit(String s, int w) {
        if (tw(s) <= w) return s;
        while (s.length() > 1 && tw(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Animated equalizer text such as ▂▄▇▅▃ */
    public static String wave(int n, double t, double amp) {
        String bars = "▁▂▃▄▅▆▇█";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double v = (Math.sin(t * 2.4 + i * 0.75) * 0.5 + 0.5) * amp;
            b.append(bars.charAt(Mth.clamp((int) (v * 7.99), 0, 7)));
        }
        return b.toString();
    }

    public static float[] rgb2hsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), d = mx - mn, h;
        if (d == 0) h = 0;
        else if (mx == r) h = ((g - b) / d % 6) / 6f;
        else if (mx == g) h = ((b - r) / d + 2) / 6f;
        else h = ((r - g) / d + 4) / 6f;
        if (h < 0) h += 1;
        return new float[]{h, mx == 0 ? 0 : d / mx, mx};
    }
}
