import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;

public class OptimizedErrorDiffusion {

    // ===== Settings =====
    private static final String IN_PATH    = "ORIGINAL_image/noise_10000x10000.png";
    private static final String OUT_BASE   = "output/OptimizedErrorDiffusion/noise10000x10000/noise_10000x10000_128";
    private static final int    WARMUP_SEQ = 5;   // Sequential warm-up runs
    private static final int    NUM_RUNS   = 3;   // Measured runs (for average)
    private static final int    MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors()); // Max threads to test

    // Performance tuning
    private static final int CHUNK = 128;  // 64–256 is often optimal
    private static final int    BACKOFF_NS  = 0;    // 0 = fastest on most machines
    private static final int    PROG_STRIDE = 16;   // Padding to prevent false-sharing (≈64B / 4)
    // ==========================

    private static final int THRESHOLD = 128;

    // ---------- Load & convert to grayscale (PIL 'L' equiv.) ----------
    private static BufferedImage loadAsGrayscale(String path) throws IOException {
        BufferedImage src = ImageIO.read(new File(path));
        if (src == null) throw new IOException("Can't read input file: " + path);
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) return src;

        int w = src.getWidth(), h = src.getHeight();
        BufferedImage g = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] gp = ((DataBufferByte) g.getRaster().getDataBuffer()).getData();
        int k = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, gc = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                // Round-nearest for 0.299 / 0.587 / 0.114
                int gray = (299 * r + 587 * gc + 114 * b + 500) / 1000;
                gp[k++] = (byte) gray;
            }
        }
        return g;
    }

    // ---------- Sequential (Measure/Save) ----------
    private static long fsSequential(BufferedImage g, String outPathOrNull) throws IOException {
        final int W = g.getWidth(), H = g.getHeight(), PW = W + 2;
        final byte[] src = ((DataBufferByte) g.getRaster().getDataBuffer()).getData();

        int[] buf = new int[(H + 2) * (W + 2)];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                buf[(y + 1) * PW + (x + 1)] = src[y * W + x] & 0xFF;

        byte[][] out = new byte[H][W];

        long t0 = System.nanoTime();
        for (int y = 0; y < H; y++) {
            int y1 = (y + 1) * PW, y2 = (y + 2) * PW;
            byte[] outRow = out[y];
            for (int x = 0; x < W; x++) {
                int oldv = buf[y1 + x + 1];
                int newv = (oldv > THRESHOLD) ? 255 : 0;
                outRow[x] = (byte) newv;
                int err = oldv - newv;
                // Floyd–Steinberg using floorDiv (for Python compatibility)
                buf[y1 + x + 2] += Math.floorDiv(err * 7, 16);   // right
                buf[y2 + x    ] += Math.floorDiv(err * 3, 16);   // down-left
                buf[y2 + x + 1] += Math.floorDiv(err * 5, 16);   // down
                buf[y2 + x + 2] += Math.floorDiv(err * 1, 16);   // down-right
            }
        }
        long t1 = System.nanoTime();

        if (outPathOrNull != null) saveGray(out, outPathOrNull);
        return t1 - t0;
    }

    // ---------- Parallel wavefront (chunked) ----------
    private static long fsParallelChunked(BufferedImage g, int threads, String outPathOrNull) throws IOException {
        final int W = g.getWidth(), H = g.getHeight(), PW = W + 2;
        final byte[] src = ((DataBufferByte) g.getRaster().getDataBuffer()).getData();

        final int[] buf = new int[(H + 2) * (W + 2)];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                buf[(y + 1) * PW + (x + 1)] = src[y * W + x] & 0xFF;

        final byte[][] out = new byte[H][W];

        // progress[y * PROG_STRIDE] = next processed column index
        final AtomicIntegerArray progress = new AtomicIntegerArray(Math.max(1, H * PROG_STRIDE));

        final int T = Math.max(1, Math.min(threads, MAX_THREADS));
        Thread[] th = new Thread[T];

        long t0 = System.nanoTime();

        for (int t = 0; t < T; t++) {
            final int tid = t;
            th[t] = new Thread(() -> {
                for (int y = tid; y < H; y += T) {
                    int y1 = (y + 1) * PW, y2 = (y + 2) * PW;
                    byte[] outRow = out[y];

                    for (int x0 = 0; x0 < W; x0 += CHUNK) {
                        int x1 = Math.min(W - 1, x0 + CHUNK - 1);

                        // Wait for the row above to process up to (x1+1) => nextIndex >= x1+2
                        if (y > 0) {
                            int need = x1 + 2;
                            int prevIdx = (y - 1) * PROG_STRIDE;
                            int spins = 0;
                            while (progress.get(prevIdx) < need) {
                                Thread.onSpinWait();
                                if ((++spins & 1023) == 0) LockSupport.parkNanos(BACKOFF_NS);
                            }
                        }

                        // Process chunk x0..x1
                        for (int x = x0; x <= x1; x++) {
                            int oldv = buf[y1 + x + 1];
                            int newv = (oldv > THRESHOLD) ? 255 : 0;
                            outRow[x] = (byte) newv;
                            int err = oldv - newv;
                            buf[y1 + x + 2] += Math.floorDiv(err * 7, 16);
                            buf[y2 + x    ] += Math.floorDiv(err * 3, 16);
                            buf[y2 + x + 1] += Math.floorDiv(err * 5, 16);
                            buf[y2 + x + 2] += Math.floorDiv(err * 1, 16);
                        }

                        // Signal progress to the row below
                        progress.set(y * PROG_STRIDE, x1 + 1);
                    }
                    // Finalize row progress
                    progress.set(y * PROG_STRIDE, W + 1);
                }
            }, "WF-Chunk-" + t);
            th[t].start();
        }
        for (Thread tt : th) { try { tt.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

        long t1 = System.nanoTime();

        if (outPathOrNull != null) saveGray(out, outPathOrNull);
        return t1 - t0;
    }

    // ---------- Helpers ----------
    private static void saveGray(byte[][] out, String path) throws IOException {
        int H = out.length, W = out[0].length;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        int p = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                dst[p++] = out[y][x];
        ensureParent(path);
        ImageIO.write(img, "png", new File(path));
    }

    private static void ensureParent(String p) {
        File f = new File(p).getAbsoluteFile();
        File d = f.getParentFile();
        if (d != null && !d.exists()) d.mkdirs();
    }

    private static void savePerfChart(long seqNs, long[] parNs, String outPath) throws IOException {
        int W = 900, H = 560, margin = 80;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));

        long mx = seqNs;
        for (long v : parNs) if (v > mx) mx = v;
        if (mx <= 0) mx = 1;

        int plotW = W - 2 * margin, plotH = H - 2 * margin;
        g.drawLine(margin, H - margin, W - margin, H - margin);
        g.drawLine(margin, margin, margin, H - margin);

        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        String title = "Error Diffusion Performance (avg of " + NUM_RUNS + " runs)";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (W - tw) / 2, 36);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (int i = 0; i <= 6; i++) {
            int y = H - margin - (i * plotH / 6);
            double ms = (mx / 1_000_000.0) * i / 6.0;
            String s = String.format("%.1f", ms);
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, margin - sw - 6, y + 4);
        }

        int bars = parNs.length + 1;
        int group = plotW / bars;
        int barW = (int)(group * 0.6);

        // Seq bar
        int x = margin + group / 2 - barW / 2;
        int hSeq = (int)((double)seqNs / (double)mx * plotH);
        g.setColor(new Color(220, 20, 60));
        g.fillRect(x, H - margin - hSeq, barW, hSeq);
        g.setColor(Color.BLACK); g.drawString("Seq", x + (barW - g.getFontMetrics().stringWidth("Seq"))/2, H - margin + 18);

        // Parallel bars
        for (int i = 0; i < parNs.length; i++) {
            x = margin + (i + 1) * group + group / 2 - barW / 2;
            int h = (int)((double)parNs[i] / (double)mx * plotH);
            g.setColor(new Color(70, 130, 180));
            g.fillRect(x, H - margin - h, barW, h);
            g.setColor(Color.BLACK);
            String lbl = String.valueOf(i + 1);
            g.drawString(lbl, x + (barW - g.getFontMetrics().stringWidth(lbl))/2, H - margin + 18);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String xlab = "Number of Threads (Parallel)";
        int xlw = g.getFontMetrics().stringWidth(xlab);
        g.drawString(xlab, (W - xlw)/2, H - 8);
        g.rotate(-Math.PI/2);
        String ylab = "Execution Time (ms)";
        g.drawString(ylab, -(H + g.getFontMetrics().stringWidth(ylab))/2 + 20, 22);
        g.dispose();

        ensureParent(outPath);
        ImageIO.write(img, "png", new File(outPath));
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        try {
            BufferedImage gray = loadAsGrayscale(IN_PATH);
            String grayOut = OUT_BASE + "_gray.png";
            ensureParent(grayOut);
            ImageIO.write(gray, "png", new File(grayOut));

            // 1) Warm-up Sequential
            System.out.println("\n== Sequential warm-up (" + WARMUP_SEQ + " runs) ==");
            for (int i = 1; i <= WARMUP_SEQ; i++) {
                fsSequential(gray, null);  // Discard results, don't time
                System.out.println("  Warm-up " + i + " done");
            }

            // 2) Measure Sequential
            System.out.println("\n== Sequential (measured) ==");
            long seqSum = 0;
            for (int i = 1; i <= NUM_RUNS; i++) {
                long t = fsSequential(gray, null);  // Discard results during timing
                seqSum += t;
                System.out.printf("  Run %d: %.2f ms%n", i, t / 1_000_000.0);
            }
            long seqAvg = seqSum / NUM_RUNS;
            System.out.printf("  Avg : %.2f ms%n", seqAvg / 1_000_000.0);

            // Save sequential result (untimed)
            fsSequential(gray, OUT_BASE + "_seq.png");

            // 3) Measure Parallel
            int Tlim = Math.max(1, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors()));
            long[] parAvg = new long[Tlim];
            long best = Long.MAX_VALUE; int bestT = 1;

            System.out.println("\n== Parallel Wavefront (chunked) ==");
            System.out.printf("CHUNK=%d, BACKOFF=%dns, Threads up to %d%n", CHUNK, BACKOFF_NS, Tlim);

            for (int t = 1; t <= Tlim; t++) {
                long sum = 0;
                for (int r = 1; r <= NUM_RUNS; r++) {
                    long tt = fsParallelChunked(gray, t, null); // Discard results during timing
                    sum += tt;
                }
                long avg = sum / NUM_RUNS; // <-- FIXED
                parAvg[t - 1] = avg;

                double speed = (double) seqAvg / avg;
                System.out.printf("  T=%02d  Avg: %.2f ms  Speedup: %.2fx%n",
                        t, avg / 1_000_000.0, speed);

                if (avg < best) { best = avg; bestT = t; }
            }
            System.out.printf("%nBest: T=%d (%.2f ms, speedup %.2fx)%n",
                    bestT, best / 1_000_000.0, (double) seqAvg / best);

            // Save best parallel result (untimed)
            fsParallelChunked(gray, bestT, OUT_BASE + "_par_" + bestT + ".png");

            // 4) Generate Performance Chart
            savePerfChart(seqAvg, parAvg, OUT_BASE + "_perf.png");

            System.out.println("\nSaved:");
            System.out.println("  " + grayOut);
            System.out.println("  " + OUT_BASE + "_seq.png");
            System.out.println("  " + OUT_BASE + "_par_" + bestT + ".png");
            System.out.println("  " + OUT_BASE + "_perf.png");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}