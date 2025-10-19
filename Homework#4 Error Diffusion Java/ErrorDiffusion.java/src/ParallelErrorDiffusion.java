import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class ParallelErrorDiffusion {
    private static final int THRESHOLD = 128;

    public static BufferedImage loadAndConvertToGrayscale(String imagePath) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            if (originalImage == null) {
                System.err.println("Could not read the image file: " + imagePath);
                return null;
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (originalImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                System.out.println("Image is already grayscale");
                return originalImage;
            }

            BufferedImage grayscaleImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] grayPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();
            int pixelIndex = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = originalImage.getRGB(x, y);
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = rgb & 0xFF;

                    int gray = (red * 19595 + green * 38470 + blue * 7471 + 32768) / 65536;
                    grayPixels[pixelIndex++] = (byte) gray;
                }
            }
            return grayscaleImage;

        } catch (IOException e) {
            System.err.println("Error loading image: " + e.getMessage());
            return null;
        }
    }

    public static long applyErrorDiffusionSequential(BufferedImage grayscaleImage,
                                                     boolean saveOutput, String outputPath) {
        if (grayscaleImage == null) return -1;

        int width = grayscaleImage.getWidth();
        int height = grayscaleImage.getHeight();
        byte[] originalPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();

        int paddedWidth = width + 2;
        int[] pixelBuffer = new int[paddedWidth * (height + 1)];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelBuffer[y * paddedWidth + (x + 1)] = originalPixels[y * width + x] & 0xFF;
            }
        }

        byte[] outputPixels = new byte[width * height];

        long startTime = System.nanoTime();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int bufferIndex = y * paddedWidth + (x + 1);
                int oldPixel = pixelBuffer[bufferIndex];
                int newPixel = (oldPixel <= THRESHOLD) ? 0 : 255;
                outputPixels[y * width + x] = (byte) newPixel;

                int quantError = oldPixel - newPixel;
                pixelBuffer[bufferIndex + 1] += Math.floorDiv(quantError * 7, 16);
                pixelBuffer[bufferIndex - 1 + paddedWidth] += Math.floorDiv(quantError * 3, 16);
                pixelBuffer[bufferIndex + paddedWidth] += Math.floorDiv(quantError * 5, 16);
                pixelBuffer[bufferIndex + 1 + paddedWidth] += Math.floorDiv(quantError * 1, 16);
            }
        }

        long endTime = System.nanoTime();

        if (saveOutput) {
            BufferedImage ditheredImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] ditheredData = ((DataBufferByte) ditheredImage.getRaster().getDataBuffer()).getData();
            System.arraycopy(outputPixels, 0, ditheredData, 0, outputPixels.length);
            saveImage(ditheredImage, outputPath);
        }

        return endTime - startTime;
    }

    public static long applyErrorDiffusionParallelOptimized(BufferedImage grayscaleImage, int numCores,
                                                            boolean saveOutput, String outputPath) {
        if (grayscaleImage == null) return -1;

        int width = grayscaleImage.getWidth();
        int height = grayscaleImage.getHeight();
        byte[] originalPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();

        int paddedWidth = width + 2;
        final AtomicIntegerArray pixelBuffer = new AtomicIntegerArray(paddedWidth * (height + 1));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelBuffer.set(y * paddedWidth + (x + 1), originalPixels[y * width + x] & 0xFF);
            }
        }

        final byte[] outputPixels = new byte[width * height];
        final AtomicIntegerArray rowComplete = new AtomicIntegerArray(height);

        ExecutorService executor = Executors.newFixedThreadPool(numCores);
        final CountDownLatch latch = new CountDownLatch(numCores);

        long startTime = System.nanoTime();
        int chunkHeight = (height + numCores - 1) / numCores;

        for (int threadId = 0; threadId < numCores; threadId++) {
            final int startY = threadId * chunkHeight;
            final int endY = Math.min(startY + chunkHeight, height);

            if (startY >= height) {
                latch.countDown();
                continue;
            }

            executor.submit(() -> {
                try {
                    for (int y = startY; y < endY; y++) {
                        if (y > 0) {
                            while (rowComplete.get(y - 1) == 0) {
                                Thread.onSpinWait();
                            }
                        }

                        for (int x = 0; x < width; x++) {
                            int bufferIndex = y * paddedWidth + (x + 1);
                            int oldPixel = pixelBuffer.get(bufferIndex);
                            int newPixel = (oldPixel <= THRESHOLD) ? 0 : 255;
                            outputPixels[y * width + x] = (byte) newPixel;

                            int quantError = oldPixel - newPixel;
                            pixelBuffer.addAndGet(bufferIndex + 1, Math.floorDiv(quantError * 7, 16));
                            pixelBuffer.addAndGet(bufferIndex - 1 + paddedWidth, Math.floorDiv(quantError * 3, 16));
                            pixelBuffer.addAndGet(bufferIndex + paddedWidth, Math.floorDiv(quantError * 5, 16));
                            pixelBuffer.addAndGet(bufferIndex + 1 + paddedWidth, Math.floorDiv(quantError * 1, 16));
                        }

                        rowComplete.set(y, 1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        executor.shutdown();

        if (saveOutput) {
            BufferedImage ditheredImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] ditheredData = ((DataBufferByte) ditheredImage.getRaster().getDataBuffer()).getData();
            System.arraycopy(outputPixels, 0, ditheredData, 0, outputPixels.length);
            saveImage(ditheredImage, outputPath);
        }

        return endTime - startTime;
    }

    public static void saveImage(BufferedImage image, String outputPath) {
        try {
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            ImageIO.write(image, "PNG", outputFile);
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }
    }

    public static void createPerformanceChart(long[] coreTimes_ns, long seqTime_ns,
                                              int maxCores, String outputPath) {
        int chartWidth = 900, chartHeight = 600;
        BufferedImage chart = new BufferedImage(chartWidth, chartHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = chart.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, chartWidth, chartHeight);

        long maxTime_ns = seqTime_ns;
        for (long t : coreTimes_ns) if (t > maxTime_ns) maxTime_ns = t;
        if (maxTime_ns == 0) maxTime_ns = 1;

        int margin = 80;
        int plotWidth = chartWidth - 2 * margin;
        int plotHeight = chartHeight - 2 * margin;

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1));
        g.drawLine(margin, chartHeight - margin, chartWidth - margin, chartHeight - margin);
        g.drawLine(margin, margin, margin, chartHeight - margin);

        g.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        String title = "Error Diffusion Performance (Cores)";
        g.drawString(title, (chartWidth - fm.stringWidth(title)) / 2, 40);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        int numYTicks = 7;
        double maxTime_ms = maxTime_ns / 1_000_000.0;

        for (int i = 0; i <= numYTicks; i++) {
            int y = chartHeight - margin - (i * plotHeight / numYTicks);
            double tickValue_ms = (double) i * maxTime_ms / numYTicks;
            String yLabel = String.format("%.1f", tickValue_ms);
            g.drawString(yLabel, margin - fm.stringWidth(yLabel) - 5, y + (fm.getHeight() / 4));
        }

        int numBars = maxCores + 1;
        int barGroupWidth = plotWidth / numBars;

        int x = margin + barGroupWidth / 4;
        int barWidth = barGroupWidth / 2;
        int barHeight = (int) ((double) seqTime_ns / maxTime_ns * plotHeight);
        g.setColor(new Color(220, 20, 60));
        g.fillRect(x, chartHeight - margin - barHeight, barWidth, barHeight);
        g.setColor(Color.BLACK);
        g.drawString("Seq", x + (barWidth - fm.stringWidth("Seq")) / 2, chartHeight - margin + 20);

        for (int i = 0; i < coreTimes_ns.length; i++) {
            x = margin + ((i + 1) * barGroupWidth) + barGroupWidth / 4;
            barHeight = (int) ((double) coreTimes_ns[i] / maxTime_ns * plotHeight);

            g.setColor(new Color(70, 130, 180));
            g.fillRect(x, chartHeight - margin - barHeight, barWidth, barHeight);

            String label = String.valueOf(i + 1);
            g.setColor(Color.BLACK);
            g.drawString(label, x + (barWidth - fm.stringWidth(label)) / 2, chartHeight - margin + 20);
        }

        g.setFont(new Font("Arial", Font.BOLD, 14));
        fm = g.getFontMetrics();
        String xLabel = "Sequential vs Parallel (Number of CPU Cores)";
        g.drawString(xLabel, (chartWidth - fm.stringWidth(xLabel)) / 2, chartHeight - 20);

        g.rotate(-Math.PI / 2);
        String yLabel = "Execution Time (ms)";
        g.drawString(yLabel, -(chartHeight + fm.stringWidth(yLabel)) / 2 + 50, 30);

        g.dispose();

        try {
            ImageIO.write(chart, "PNG", new File(outputPath));
            System.out.println("\nPerformance chart saved: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String inputPath = "original_image/10k-Image.png";
        String baseName = "output/10k";
        final int NUM_RUNS = 3;

        BufferedImage grayscaleImg = loadAndConvertToGrayscale(inputPath);
        if (grayscaleImg == null) return;

        int width = grayscaleImg.getWidth();
        int height = grayscaleImg.getHeight();
        System.out.printf("\nImage size: %d x %d pixels%n", width, height);
        System.out.printf("Running %d iterations per configuration for accuracy...%n", NUM_RUNS);

        saveImage(grayscaleImg, baseName + "_grayscale.png");

        System.out.println("\n=== Sequential Error Diffusion ===");
        long seqTimeTotal = 0;
        for (int run = 0; run < NUM_RUNS; run++) {
            long time = applyErrorDiffusionSequential(grayscaleImg, run == 0,
                    baseName + "_sequential.png");
            seqTimeTotal += time;
            System.out.printf("  Run %d: %.2f ms%n", run + 1, time / 1_000_000.0);
        }
        long seqTime = seqTimeTotal / NUM_RUNS;
        System.out.printf("Average: %.2f ms%n", seqTime / 1_000_000.0);

        int numPhysicalCores = Runtime.getRuntime().availableProcessors() / 2;
        int maxCores = Math.min(12, Runtime.getRuntime().availableProcessors());

        System.out.println("\n=== Parallel Error Diffusion (Optimized Wavefront) ===");
        System.out.println("Note: Testing up to " + maxCores + " cores (avoiding SMT overhead)");
        System.out.println("-".repeat(70));
        System.out.printf("%-10s | %-15s | %-12s | %-12s%n", "Cores", "Time (ms)", "Speedup", "Efficiency");
        System.out.println("-".repeat(70));

        long[] times = new long[maxCores];
        long bestTime = Long.MAX_VALUE;
        int bestCores = 1;

        for (int n = 1; n <= maxCores; n++) {
            long totalTime = 0;
            for (int run = 0; run < NUM_RUNS; run++) {
                long time = applyErrorDiffusionParallelOptimized(grayscaleImg, n, false, null);
                totalTime += time;
            }
            long avgTime = totalTime / NUM_RUNS;
            times[n - 1] = avgTime;

            if (avgTime < bestTime) {
                bestTime = avgTime;
                bestCores = n;
            }

            double speedup = (double) seqTime / avgTime;
            double efficiency = (speedup / n) * 100;

            String marker = (avgTime == bestTime) ? " ★" : "";
            System.out.printf("%-10d | %-15.2f | %-12.2fx | %-11.1f%%%s%n",
                    n, avgTime / 1_000_000.0, speedup, efficiency, marker);
        }

        System.out.println("\n★ Best performance: " + bestCores + " cores (average of " + NUM_RUNS + " runs)");

        System.out.println("\nSaving output with best configuration (" + bestCores + " cores)...");
        applyErrorDiffusionParallelOptimized(grayscaleImg, bestCores, true, baseName + "_parallel.png");

        createPerformanceChart(times, seqTime, maxCores, baseName + "_performance.png");

        System.out.println("\n=== Recommendation ===");
        if (seqTime < bestTime) {
            System.out.println("✓ Use SEQUENTIAL version (fastest for this image size)");
        } else {
            double improvement = ((seqTime - bestTime) / (double)seqTime) * 100;
            System.out.printf("✓ Use PARALLEL with %d cores (%.1f%% faster)%n", bestCores, improvement);
        }

        System.out.println("\nAll outputs saved with basename: " + baseName);
    }
}