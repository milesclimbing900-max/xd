package dev.resonance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Through-wall block ESP. Draws on the HUD layer by projecting each marker's block into screen space,
 * so it is always visible through terrain. Edges are clipped against the near plane and drawn as
 * polygons (no overlapping translucent strokes), rendered at up to 3x GUI resolution for smooth lines.
 */
public final class EspRenderer {
    private static final double NEAR = 0.05;
    private static final int[][] EDGES = new int[12][];
    static {
        int k = 0;
        for (int i = 0; i < 8; i++) for (int bit : new int[]{1, 2, 4}) if ((i & bit) == 0) EDGES[k++] = new int[]{i, i | bit};
    }

    private static double W, H, cx, cy, sc, ox, oy, oz, fx, fy, fz, rx, ry, rz, ux, uy, uz;

    private record Label(int x, int y, float f, SoundTracker.Marker m, double dist) { }

    public static void render(GuiGraphics g, float pt) {
        Config c = Config.get();
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (!c.enabled || p == null) return;
        List<SoundTracker.Marker> ms = SoundTracker.snapshot();
        if (ms.isEmpty()) return;
        long now = System.currentTimeMillis();

        double yaw, pit;
        try {
            var ca = (dev.resonance.CameraAccess) (Object) mc.gameRenderer.getMainCamera();
            var pos = ca.resonance$pos();
            ox = pos.x; oy = pos.y; oz = pos.z;
            yaw = Math.toRadians(ca.resonance$yaw()); pit = Math.toRadians(ca.resonance$pitch());
        } catch (Throwable t) {
            ox = Mth.lerp(pt, p.xo, p.getX()); oy = Mth.lerp(pt, p.yo, p.getY()) + p.getEyeHeight(); oz = Mth.lerp(pt, p.zo, p.getZ());
            yaw = Math.toRadians(Mth.rotLerp(pt, p.yRotO, p.getYRot())); pit = Math.toRadians(Mth.lerp(pt, p.xRotO, p.getXRot()));
        }
        double cp = Math.cos(pit);
        fx = -Math.sin(yaw) * cp; fy = -Math.sin(pit); fz = Math.cos(yaw) * cp;
        rx = -Math.cos(yaw); ry = 0; rz = -Math.sin(yaw);
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;

        int d = Ui.gs(); // always draw at real screen-pixel resolution
        W = g.guiWidth() * d; H = g.guiHeight() * d; cx = W / 2; cy = H / 2;
        double fov = mc.options.fov().get();
        sc = (H / 2) / Math.tan(Math.toRadians(fov) / 2);

        ms.sort((a, b) -> Double.compare(dist2(a), dist2(b)));
        if (ms.size() > c.maxMarkers) ms = new ArrayList<>(ms.subList(0, c.maxMarkers));
        java.util.Collections.reverse(ms); // far first, near on top

        List<Label> labels = new ArrayList<>();
        g.pose().pushMatrix();
        g.pose().scale(1f / d, 1f / d);
        for (SoundTracker.Marker m : ms) {
            float f = SoundTracker.fade(m, c, now);
            if (f <= 0.01f) continue;
            drawMarker(g, c, m, f, now, d);
            if (c.labels) {
                double[] t = cam(m.bx + 0.5, m.by + 1.18, m.bz + 0.5);
                if (t[2] > NEAR) {
                    double[] s = pr(t);
                    labels.add(new Label((int) (s[0] / d), (int) (s[1] / d), f, m, Math.sqrt(dist2(m))));
                }
            }
        }
        g.pose().popMatrix();

        Font font = Ui.font();
        for (Label l : labels) {
            if (l.x < -80 || l.x > g.guiWidth() + 80 || l.y < 0 || l.y > g.guiHeight()) continue;
            String name = c.labelMode == 1 ? l.m.id : SoundTracker.friendly(l.m.id);
            String txt = Config.GLYPH[l.m.cat] + " " + name + (l.m.muted ? " · muted" : "")
                    + (c.showDistance ? "  " + Math.round(l.dist) + "m" : "");
            int w = font.width(txt) + 14;
            int x = l.x - w / 2, y = l.y - 15;
            double a = l.f;
            int cc = c.colorByCategory ? c.catColor[l.m.cat] : c.fillColor;
            Ui.rrect(g, x, y, w, 13, 6, Ui.argb(0x0C0C10, 200 * a));
            Ui.rrect(g, x + 3, y + 4, 5, 5, 2, Ui.argb(cc, 255 * a));
            Ui.text(g, txt, x + 11, y + 3, Ui.argb(0xFFFFFF, Math.max(8, 255 * a)));
        }
    }

    private static void drawMarker(GuiGraphics g, Config c, SoundTracker.Marker m, float f, long now, int d) {
        double flash = Math.max(0, 1 - (now - m.born) / 260.0);
        int fillRgb = c.colorByCategory ? c.catColor[m.cat] : c.fillColor;
        int lineRgb = c.colorByCategory ? Ui.mix(c.catColor[m.cat], 0xFFFFFF, 0.3) : c.outlineColor;
        double ins = c.shrink ? (1 - f) * 0.28 : 0;
        double[][] v = new double[8][];
        for (int i = 0; i < 8; i++)
            v[i] = cam(m.bx + ((i & 1) == 0 ? ins : 1 - ins), m.by + ((i & 2) == 0 ? ins : 1 - ins), m.bz + ((i & 4) == 0 ? ins : 1 - ins));

        List<double[][]> segs = new ArrayList<>();
        List<double[]> pts = new ArrayList<>();
        for (int[] e : EDGES) {
            double[][] s = clip(v[e[0]], v[e[1]]);
            if (s != null) { segs.add(s); pts.add(pr(s[0])); pts.add(pr(s[1])); }
        }
        if (pts.size() < 3) return;
        List<double[]> hull = hull(pts);

        if (c.fillOn && hull.size() >= 3)
            fillPoly(g, hull, Ui.argb(fillRgb, Math.min(255, c.fillAlpha * 2.55 * f + flash * 70 * f)));
        if (!c.outlineOn) return;
        int oc = Ui.argb(lineRgb, Math.min(255, c.outlineAlpha * 2.55 * f + flash * 80 * f));
        int t = Math.max(1, Math.round(c.lineWidth * d * 0.5f));
        softExtra = c.softEdges ? Math.max(2, d) : 0;
        softCol = Ui.argb(lineRgb, Math.min(255, c.outlineAlpha * 2.55 * f * 0.28));
        switch (c.outlineMode) {
            case 1 -> {
                for (int[] e : EDGES) {
                    double[] a = v[e[0]], b = v[e[1]];
                    seg(g, clip(a, lerp(a, b, 0.3)), t, oc);
                    seg(g, clip(lerp(a, b, 0.7), b), t, oc);
                }
            }
            case 2 -> {
                for (int i = 0; i < hull.size(); i++) {
                    double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
                    line(g, a, b, t, oc);
                }
            }
            default -> { for (double[][] s : segs) seg(g, s, t, oc); }
        }
    }

    private static void seg(GuiGraphics g, double[][] s, int t, int col) {
        if (s != null) line(g, pr(s[0]), pr(s[1]), t, col);
    }

    private static double dist2(SoundTracker.Marker m) {
        double dx = m.bx + 0.5 - ox, dy = m.by + 0.5 - oy, dz = m.bz + 0.5 - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double[] cam(double x, double y, double z) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        return new double[]{dx * rx + dy * ry + dz * rz, dx * ux + dy * uy + dz * uz, dx * fx + dy * fy + dz * fz};
    }

    private static double[] pr(double[] v) { return new double[]{cx + v[0] / v[2] * sc, cy - v[1] / v[2] * sc}; }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Clips a camera-space segment to the near plane; null if fully behind the camera. */
    private static double[][] clip(double[] a, double[] b) {
        if (a[2] < NEAR && b[2] < NEAR) return null;
        if (a[2] < NEAR) { a = lerp(a, b, (NEAR - a[2]) / (b[2] - a[2])); a[2] = NEAR; }
        else if (b[2] < NEAR) { b = lerp(a, b, (NEAR - a[2]) / (b[2] - a[2])); b[2] = NEAR; }
        return new double[][]{a, b};
    }

    private static int softCol, softExtra;

    private static void line(GuiGraphics g, double[] a, double[] b, int t, int col) {
        if (softExtra > 0) quad(g, a, b, t + softExtra, softCol);
        quad(g, a, b, t, col);
    }

    private static void quad(GuiGraphics g, double[] a, double[] b, int t, int col) {
        double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5 || Double.isNaN(len)) return;
        dx /= len; dy /= len;
        double h = Math.max(1, t) / 2.0, nx = -dy * h, ny = dx * h, ex = dx * h, ey = dy * h;
        List<double[]> q = new ArrayList<>(4);
        q.add(new double[]{a[0] - ex + nx, a[1] - ey + ny});
        q.add(new double[]{b[0] + ex + nx, b[1] + ey + ny});
        q.add(new double[]{b[0] + ex - nx, b[1] + ey - ny});
        q.add(new double[]{a[0] - ex - nx, a[1] - ey - ny});
        fillPoly(g, q, col);
    }

    /** Scanline fill of a convex polygon along its shorter axis, one fill per scanline, clamped to the screen. */
    private static void fillPoly(GuiGraphics g, List<double[]> p, int col) {
        double minx = 1e18, maxx = -1e18, miny = 1e18, maxy = -1e18;
        for (double[] q : p) { minx = Math.min(minx, q[0]); maxx = Math.max(maxx, q[0]); miny = Math.min(miny, q[1]); maxy = Math.max(maxy, q[1]); }
        boolean rows = (maxy - miny) <= (maxx - minx);
        int s0 = Math.max(0, (int) Math.floor(rows ? miny : minx));
        int s1 = Math.min((int) (rows ? H : W) - 1, (int) Math.floor(rows ? maxy : maxx));
        int lim = (int) (rows ? W : H), n = p.size();
        for (int s = s0; s <= s1; s++) {
            double c = s + 0.5, lo = 1e18, hi = -1e18;
            for (int i = 0; i < n; i++) {
                double[] a = p.get(i), b = p.get((i + 1) % n);
                double a0 = rows ? a[1] : a[0], b0 = rows ? b[1] : b[0];
                if ((a0 <= c && b0 > c) || (b0 <= c && a0 > c)) {
                    double a1 = rows ? a[0] : a[1], b1 = rows ? b[0] : b[1];
                    double v = a1 + (c - a0) / (b0 - a0) * (b1 - a1);
                    lo = Math.min(lo, v); hi = Math.max(hi, v);
                }
            }
            if (hi < lo) continue;
            int l = Mth.clamp((int) Math.round(lo), 0, lim), h = Mth.clamp((int) Math.round(hi), 0, lim);
            if (h <= l) continue;
            if (rows) g.fill(l, s, h, s + 1, col); else g.fill(s, l, s + 1, h, col);
        }
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private static List<double[]> hull(List<double[]> pts) {
        pts.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        int n = pts.size(), k = 0;
        double[][] h = new double[2 * n][];
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], pts.get(i)) <= 0) k--;
            h[k++] = pts.get(i);
        }
        for (int i = n - 2, t = k + 1; i >= 0; i--) {
            while (k >= t && cross(h[k - 2], h[k - 1], pts.get(i)) <= 0) k--;
            h[k++] = pts.get(i);
        }
        return new ArrayList<>(Arrays.asList(h).subList(0, Math.max(0, k - 1)));
    }
}
