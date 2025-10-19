import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

// NOTE: This class has been renamed to better reflect its purpose.
public class DitheringBenchmark {

    private static final int THRESHOLD = 128;
    // The core of the performance optimization. This value can be tuned.
    private static final int CHUNK_SIZE = 64;

    /**
     * The Worker class for the optimized parallel algorithm.
     * It processes the image in chunks to minimize synchronization overhead.
     */
    private static class ParallelChunkWorker implements Runnable {
        private final int threadId;
        private final int numThreads;
        private final int width;
        private final int height;
        private final int[] pixelBuffer; // Uses a plain int[] for maximum speed.
        private final byte[] outputPixels;
        // Progress is now tracked per chunk, not per pixel or per row.
        private final java.util.concurrent.atomic.AtomicIntegerArray chunkProgress;

        public ParallelChunkWorker(int threadId, int numThreads, int width, int height,
                                   int[] pixelBuffer, byte[] outputPixels,
                                   java.util.concurrent.atomic.AtomicIntegerArray chunkProgress) {
            this.threadId = threadId;
            this.numThreads = numThreads;
            this.width = width;
            this.height = height;
            this.pixelBuffer = pixelBuffer;
            this.outputPixels = outputPixels;
            this.chunkProgress = chunkProgress;
        }

        @Override
        public void run() {
            int paddedWidth = width + 2;
            int numChunks = (width + CHUNK_SIZE - 1) / CHUNK_SIZE;

            // Each thread processes its assigned rows (strided distribution for good load balancing)
            for (int y = threadId; y < height; y += numThreads) {
                // The outer loop iterates over chunks within the row.
                for (int chunk = 0; chunk < numChunks; chunk++) {
                    // --- HIGHLY OPTIMIZED SYNCHRONIZATION ---
                    // This check happens only ONCE per chunk, drastically reducing overhead.
                    if (y > 0) {
                        // Wait for the chunk directly above in the previous row to be completed.
                        while (chunkProgress.get(y - 1) < (chunk + 1)) {
                            Thread.onSpinWait();
                        }
                    }

                    // --- ACTUAL WORK: Process all pixels within this chunk ---
                    int startX = chunk * CHUNK_SIZE;
                    int endX = Math.min(startX + CHUNK_SIZE, width);

                    for (int x = startX; x < endX; x++) {
                        int bufferIndex = y * paddedWidth + (x + 1);
                        int oldPixel = pixelBuffer[bufferIndex];
                        int newPixel = (oldPixel > THRESHOLD) ? 255 : 0; // Corrected threshold logic
                        outputPixels[y * width + x] = (byte) newPixel;

                        int quantError = oldPixel - newPixel;

                        pixelBuffer[bufferIndex + 1] += Math.floorDiv(quantError * 7, 16);
                        pixelBuffer[bufferIndex - 1 + paddedWidth] += Math.floorDiv(quantError * 3, 16);
                        pixelBuffer[bufferIndex + paddedWidth] += Math.floorDiv(quantError * 5, 16);
                        pixelBuffer[bufferIndex + 1 + paddedWidth] += Math.floorDiv(quantError, 16);
                    }

                    // --- SIGNAL: Mark this chunk as completed for this row ---
                    chunkProgress.set(y, chunk + 1);
                }
            }
        }
    }

    // --- BENCHMARKING AND UTILITY METHODS ---

    public static BufferedImage loadAndConvertToGrayscale(String imagePath) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            if (originalImage == null) {
                System.err.println("Could not read image file: " + imagePath);
                return null;
            }
            if (originalImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                return originalImage;
            }

            BufferedImage grayscaleImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = grayscaleImage.createGraphics();
            g.drawImage(originalImage, 0, 0, null);
            g.dispose();
            return grayscaleImage;
        } catch (IOException e) {
            System.err.println("Error loading image '" + imagePath + "': " + e.getMessage());
            return null;
        }
    }

    public static long applyErrorDiffusionSequential(BufferedImage grayscaleImage, boolean saveOutput, String outputPath) {
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
                int newPixel = (oldPixel > THRESHOLD) ? 255 : 0;
                outputPixels[y * width + x] = (byte) newPixel;

                int quantError = oldPixel - newPixel;
                pixelBuffer[bufferIndex + 1] += Math.floorDiv(quantError * 7, 16);
                pixelBuffer[bufferIndex - 1 + paddedWidth] += Math.floorDiv(quantError * 3, 16);
                pixelBuffer[bufferIndex + paddedWidth] += Math.floorDiv(quantError * 5, 16);
                pixelBuffer[bufferIndex + 1 + paddedWidth] += Math.floorDiv(quantError, 16);
            }
        }

        long endTime = System.nanoTime();

        if (saveOutput) {
            saveDitheredImage(outputPixels, width, height, outputPath);
        }
        return endTime - startTime;
    }

    public static long applyErrorDiffusionParallelChunked(BufferedImage grayscaleImage, int numCores, boolean saveOutput, String outputPath) {
        int width = grayscaleImage.getWidth();
        int height = grayscaleImage.getHeight();
        byte[] originalPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();

        int paddedWidth = width + 2;
        final int[] pixelBuffer = new int[paddedWidth * (height + 1)];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelBuffer[y * paddedWidth + (x + 1)] = originalPixels[y * width + x] & 0xFF;
            }
        }

        final byte[] outputPixels = new byte[width * height];
        final java.util.concurrent.atomic.AtomicIntegerArray chunkProgress = new java.util.concurrent.atomic.AtomicIntegerArray(height);

        try (ExecutorService executor = Executors.newFixedThreadPool(numCores)) {
            final CountDownLatch latch = new CountDownLatch(numCores);
            long startTime = System.nanoTime();

            for (int i = 0; i < numCores; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        new ParallelChunkWorker(threadId, numCores, width, height, pixelBuffer, outputPixels, chunkProgress).run();
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

            if (saveOutput) {
                saveDitheredImage(outputPixels, width, height, outputPath);
            }
            return endTime - startTime;
        }
    }

    public static void saveDitheredImage(byte[] pixelData, int width, int height, String outputPath) {
        // --- FIX: Correct way to save a 1-bit binary image ---
        BufferedImage ditheredImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster raster = ditheredImage.getRaster();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get the 0 or 255 value from our result.
                int pixelValue = pixelData[y * width + x] & 0xFF;
                // Set the sample in the raster. It expects 0 for black, 1 for white.
                raster.setSample(x, y, 0, (pixelValue > 0) ? 1 : 0);
            }
        }

        saveImage(ditheredImage, outputPath);
    }

    public static void saveImage(BufferedImage image, String outputPath) {
        try {
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("Warning: Could not create directory " + parentDir);
                }
            }
            ImageIO.write(image, "PNG", outputFile);
        } catch (IOException e) {
            System.err.println("Error saving image '" + outputPath + "': " + e.getMessage());
        }
    }

    public static void createPerformanceChart(long[] coreTimes_ns, long seqTime_ns, int maxCores, String outputPath) {
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
        String title = "Dithering Performance: Sequential vs. Parallel (Chunked)";
        g.drawString(title, (chartWidth - fm.stringWidth(title)) / 2, 40);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        for (int i = 0; i <= 10; i++) {
            int y = chartHeight - margin - (i * plotHeight / 10);
            String yLabel = String.format("%.1f", (double) i * maxTime_ns / 1_000_000.0 / 10.0);
            g.drawString(yLabel, margin - fm.stringWidth(yLabel) - 5, y + 4);
        }

        int barGroupWidth = plotWidth / (maxCores + 1);
        int barWidth = barGroupWidth * 2 / 3;

        int x = margin + (barGroupWidth - barWidth) / 2;
        int barHeight = (int) ((double) seqTime_ns / maxTime_ns * plotHeight);
        g.setColor(new Color(220, 20, 60));
        g.fillRect(x, chartHeight - margin - barHeight, barWidth, barHeight);
        g.setColor(Color.BLACK);
        g.drawString("Seq", x + (barWidth - fm.stringWidth("Seq")) / 2, chartHeight - margin + 20);

        for (int i = 0; i < coreTimes_ns.length; i++) {
            x = margin + ((i + 1) * barGroupWidth) + (barGroupWidth - barWidth) / 2;
            barHeight = (int) ((double) coreTimes_ns[i] / maxTime_ns * plotHeight);
            g.setColor(new Color(70, 130, 180));
            g.fillRect(x, chartHeight - margin - barHeight, barWidth, barHeight);
            String label = String.valueOf(i + 1);
            g.setColor(Color.BLACK);
            g.drawString(label, x + (barWidth - fm.stringWidth(label)) / 2, chartHeight - margin + 20);
        }

        g.setFont(new Font("Arial", Font.BOLD, 14));
        fm = g.getFontMetrics();
        g.drawString("Number of CPU Cores", (chartWidth - fm.stringWidth("Number of CPU Cores")) / 2, chartHeight - 20);
        g.rotate(-Math.PI / 2);
        g.drawString("Execution Time (ms)", -(chartHeight + fm.stringWidth("Execution Time (ms)")) / 2, 30);
        g.dispose();

        saveImage(chart, outputPath);
        System.out.println("\nPerformance chart saved: " + outputPath);
    }

    public static void main(String[] args) {
        String inputPath = "original_image/baboon_512.png"; // <-- REPLACE THIS with your copied absolute path

        System.out.println("Using input image specified in code: " + inputPath);

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found at '" + inputPath + "'");
            System.err.println("Please check the path specified in the main() method.");
            return;
        }

        String baseName = inputPath.substring(0, inputPath.lastIndexOf('.'));
        final int NUM_RUNS = 5;

        BufferedImage grayscaleImage = loadAndConvertToGrayscale(inputPath);
        if (grayscaleImage == null) return;

        System.out.printf("\nImage: '%s' (%d x %d)%n", inputFile.getName(), grayscaleImage.getWidth(), grayscaleImage.getHeight());
        System.out.printf("Benchmarking with %d runs per configuration...%n", NUM_RUNS);

        System.out.println("\n=== Sequential Baseline ===");
        long seqTimeTotal = 0;
        for (int run = 0; run < NUM_RUNS; run++) {
            long time = applyErrorDiffusionSequential(grayscaleImage, run == 0, baseName + "_sequential.png");
            seqTimeTotal += time;
        }
        long seqTime = seqTimeTotal / NUM_RUNS;
        System.out.printf("Average Time: %.2f ms%n", seqTime / 1_000_000.0);

        int maxCores = Runtime.getRuntime().availableProcessors();
        System.out.println("\n=== Parallel (Chunked Wavefront) ===");
        System.out.println("-".repeat(70));
        System.out.printf("%-10s | %-15s | %-12s | %-12s%n", "Cores", "Time (ms)", "Speedup", "Efficiency");
        System.out.println("-".repeat(70));

        long[] times = new long[maxCores];
        long bestTime = Long.MAX_VALUE;
        int bestCores = 1;

        for (int n = 1; n <= maxCores; n++) {
            long totalTime = 0;
            for (int run = 0; run < NUM_RUNS; run++) {
                totalTime += applyErrorDiffusionParallelChunked(grayscaleImage, n, false, null);
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

        System.out.println("-".repeat(70));
        System.out.println("\n★ Best performance achieved with " + bestCores + " cores.");

        System.out.println("\nSaving final parallel output image using best configuration...");
        applyErrorDiffusionParallelChunked(grayscaleImage, bestCores, true, baseName + "_parallel_final.png");

        createPerformanceChart(times, seqTime, maxCores, baseName + "_performance_chart.png");
    }
}
