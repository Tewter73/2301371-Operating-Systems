import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;


public class ErrorDiffusion {
    private static final int THRESHOLD = 128;
    public static BufferedImage loadAndConvertToGrayscale(String image) {
        try {
            // 1. Load the source image
            BufferedImage originalImage = ImageIO.read(new File(image));
            if (originalImage == null) {
                System.err.println("Could not read the image file: " + image);
                return null;
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            // Check if image is already grayscale
            if (originalImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                System.out.println("Image is already grayscale");
                return originalImage;
            }

            // For color images, convert to grayscale
            BufferedImage grayscaleImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] grayPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();
            int pixelIndex = 0;

            // Loop through the NORMALIZED image, not the raw raster.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = originalImage.getRGB(x, y);
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = rgb & 0xFF;

                    // 1. avoid using floats by multiply 2^16 for convert to int: 19595 ≈ 0.299 * 65536, 38470 ≈ 0.587 * 65536, 7471  ≈ 0.114 * 65536
                    // 2. The '32768 = (1 << 15)' (which is 65536 / 2 = 32768) is added to the total. เพื่อให้ได้ผลลัพธ์ที่ใกล้เคียงค่าจริงที่สุด แทนที่จะเป็นการ "ปัดทิ้ง" (truncation) 
                    // 3. The final division by 65536( >> 16) scales the result back down to the proper 0-255 range.
                    int gray = (red * 19595 + green * 38470 + blue * 7471 + 32768) / 65536 ;
                    grayPixels[pixelIndex++] = (byte) gray;
                }
            }
            return grayscaleImage;

        } catch (IOException e) {
            System.err.println("Error loading image: " + e.getMessage());
            return null;
        }
    }

    public static long ErrorDiffusionSequential(BufferedImage grayscaleImage, boolean produceImage,String baseName) {
        if (grayscaleImage == null) return -1;

        int width = grayscaleImage.getWidth();
        int height = grayscaleImage.getHeight();
        byte[] originalPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();

        // Put space on edge
        // width + 2 = บวกเพิ่มเข้าไป 2 พิกเซล เพื่อสร้าง ขอบซ้าย 1 พิกเซล และ ขอบขวา 1 พิกเซล
        // height + 1 = Error Diffusion จะกระจายค่าความผิดพลาด (error) ไปยังพิกเซล "แถวด้านล่าง" เท่านั้น จึงเพิ่มแค่ 1 พิกเซล
        int paddedWidth = width + 2;
        int[] pixelBuffer = new int[paddedWidth * (height + 1)];

        // Copy original pixels into the center of the buffer.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // x + 1 = ขยับตำแหน่งของข้อมูลทั้งหมดไปทางขวา 1 ช่อง เพื่อสร้างขอบว่างด้านซ้าย
                // 0xFF จะเป็นการ แปลงค่า byte ที่มีเครื่องหมาย ให้เป็นค่า int ที่ไม่มีเครื่องหมาย (0-255)
                pixelBuffer[y * paddedWidth + (x + 1)] = originalPixels[y * width + x] & 0xFF;
            }
        }

        // Use byte[] for the final output to save memory.
        byte[] outputPixels = new byte[width * height];
        long seqStart = System.nanoTime();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int bufferIndex = y * paddedWidth + (x + 1);

                // Get the current pixel value (which includes diffused error from previous pixels).
                int oldPixel = pixelBuffer[bufferIndex];
                int newPixel = (oldPixel <= THRESHOLD) ? 0 : 255;

                outputPixels[y * width + x] = (byte) newPixel;

                int quantError = oldPixel - newPixel;

                // // +++ START: Debugging code for a specific pixel +++
                // if (y == 2 && x == 3) {
                //     System.out.println("\n--- DEBUG at (" + x + ", " + y + ") ---");
                //     System.out.println("Pixel Value from Buffer (rawOldPixel): " + oldPixel);
                //     System.out.println("Output Pixel (newPixel): " + newPixel);
                //     System.out.println("Quantization Error (quantError): " + quantError);
                //     System.out.println("------------------------------------");
                //     System.out.println("Floyd-Steinberg Neighbor Calculations:");


                //     System.out.println("Neighbor Original Values:");
                //     int valRight = pixelBuffer[bufferIndex + 1];
                //     int valDownLeft = pixelBuffer[bufferIndex - 1 + paddedWidth];
                //     int valDown = pixelBuffer[bufferIndex + paddedWidth];
                //     int valDownRight = pixelBuffer[bufferIndex + 1 + paddedWidth];

                //     System.out.println("  -> Original Right Neighbor: " + valRight);
                //     System.out.println("  -> Original Down-Left Neighbor: " + valDownLeft);
                //     System.out.println("  -> Original Down Neighbor: " + valDown);
                //     System.out.println("  -> Original Down-Right Neighbor: " + valDownRight);
                //     System.out.println("------------------------------------");

                //     // คำนวณค่า error ที่จะกระจายไปแต่ละทิศทาง
                //     int errorRight = Math.floorDiv(quantError * 7,16);
                //     int errorDownLeft = Math.floorDiv(quantError * 3,16);
                //     int errorDown = Math.floorDiv(quantError * 5,16);
                //     int errorDownRight = Math.floorDiv(quantError * 1,16);

                //     System.out.println("  -> Right Neighbor gets: " + errorRight);
                //     System.out.println("  -> Down-Left Neighbor gets: " + errorDownLeft);
                //     System.out.println("  -> Down Neighbor gets: " + errorDown);
                //     System.out.println("  -> Down-Right Neighbor gets: " + errorDownRight);
                //     System.out.println("------------------------------------");

                //     System.out.println("Neighbor Original Values after:");
                //     int valRightafter = pixelBuffer[bufferIndex + 1] + errorRight;
                //     int valDownLeftafter = pixelBuffer[bufferIndex - 1 + paddedWidth] + errorDownLeft;
                //     int valDownafter = pixelBuffer[bufferIndex + paddedWidth] + errorDown;
                //     int valDownRightafter = pixelBuffer[bufferIndex + 1 + paddedWidth] + errorDownRight;

                //     System.out.println("  -> New Right Neighbor: " + valRightafter);
                //     System.out.println("  -> New Down-Left Neighbor: " + valDownLeftafter);
                //     System.out.println("  -> New Down Neighbor: " + valDownafter);
                //     System.out.println("  -> New Down-Right Neighbor: " + valDownRightafter);
                //     System.out.println("------------------------------------");
                // }
                // // +++ END: Debugging code +++
                pixelBuffer[bufferIndex + 1] += Math.floorDiv(quantError * 7,16);
                pixelBuffer[bufferIndex - 1 + paddedWidth] += Math.floorDiv(quantError * 3,16);
                pixelBuffer[bufferIndex + paddedWidth] += Math.floorDiv(quantError * 5,16);
                pixelBuffer[bufferIndex + 1 + paddedWidth] += Math.floorDiv(quantError * 1,16);

            }
        }
        long seqTime = System.nanoTime() - seqStart;
        if(produceImage){
            System.out.println("\nRunning Sequential Error Diffusion = " + String.format("%.2f", seqTime / 1_000_000.0) + " ms");
            // Create the final dithered image efficiently.
            BufferedImage ditheredImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            // Get direct access to the new image's data buffer.
            byte[] ditheredData = ((DataBufferByte) ditheredImage.getRaster().getDataBuffer()).getData();
            // Copy our processed pixels into the image buffer in a single, fast operation.
            System.arraycopy(outputPixels, 0, ditheredData, 0, outputPixels.length);
            saveImage(ditheredImage, "output/" + baseName + "_sequential.png");
        }
        return seqTime;
    }
    public static long ErrorDiffusionParallel(BufferedImage grayscaleImage, int nThreads,boolean produceImage, String baseName) {
        if (grayscaleImage == null) return -1;

        int width = grayscaleImage.getWidth(), height = grayscaleImage.getHeight();
        byte[] originalPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();

        int paddedWidth = width + 2;
        final AtomicIntegerArray pixelBuffer = new AtomicIntegerArray(paddedWidth * (height + 1));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelBuffer.set(y * paddedWidth + (x + 1), originalPixels[y * width + x] & 0xFF);
            }
        }

        final byte[] outputPixels = new byte[width * height];

        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        final CountDownLatch latch = new CountDownLatch(nThreads);

        final int[] rowProgress = new int[height];
        for (int i = 0; i < height; i++) {
            rowProgress[i] = -1;
        }

        long startTime = System.nanoTime();
        int chunkHeight = height / nThreads;
        for (int threadId = 0; threadId < nThreads; threadId++) {
            final int startY = threadId * chunkHeight;
            final int endY = (threadId == nThreads - 1) ? height : startY + chunkHeight;
            executor.submit(() -> {
                try {
                    for (int y = startY; y < endY; y++) {
                        for (int x = 0; x < width; x++) {
                            // การรอจะเกิดขึ้นแค่ที่ขอบบนของบล็อกเท่านั้น
                            if (y > 0 && y == startY) {
                                while (rowProgress[y - 1] < x + 1 && rowProgress[y - 1] != width - 1) {
                                    Thread.yield();
                                }
                            }

                            int bufferIndex = y * paddedWidth + (x + 1);
                            int oldPixel = pixelBuffer.get(bufferIndex);
                            int newPixel = (oldPixel <= THRESHOLD) ? 0 : 255;
                            outputPixels[y * width + x] = (byte) newPixel;

                            int quantError = oldPixel - newPixel;
                            pixelBuffer.addAndGet(bufferIndex + 1, Math.floorDiv(quantError * 7, 16));
                            pixelBuffer.addAndGet(bufferIndex - 1 + paddedWidth, Math.floorDiv(quantError * 3, 16));
                            pixelBuffer.addAndGet(bufferIndex + paddedWidth, Math.floorDiv(quantError * 5, 16));
                            pixelBuffer.addAndGet(bufferIndex + 1 + paddedWidth, Math.floorDiv(quantError * 1, 16));

                            // อัปเดต progress ของแถวปัจจุบัน
                            rowProgress[y] = x;
                        }
                    }
                } finally { latch.countDown(); }
            });
        }
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        long endTime = System.nanoTime();
        executor.shutdown();

        if (produceImage) {
            BufferedImage ditheredImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] ditheredData = ((DataBufferByte) ditheredImage.getRaster().getDataBuffer()).getData();
            System.arraycopy(outputPixels, 0, ditheredData, 0, outputPixels.length);
            saveImage(ditheredImage, "output/" + baseName + "_parallel.png");
        }
        return endTime - startTime;
    }

    // public static long ErrorDiffusionParallel(BufferedImage grayscaleImage, int nThreads,boolean produceImage, String baseName){
    //     //ห้ามยุ่ง
    //     if (grayscaleImage == null) return -1;

    //     int width = grayscaleImage.getWidth();
    //     int height = grayscaleImage.getHeight();
    //     byte[] originalPixels = ((DataBufferByte) grayscaleImage.getRaster().getDataBuffer()).getData();

    //     int paddedWidth = width + 2;
    //     //final int[] pixelBuffer = new int[paddedWidth * (height + 1)];
    //     final AtomicIntegerArray pixelBuffer = new AtomicIntegerArray(paddedWidth * (height + 1));
    //     for (int y = 0; y < height; y++) {
    //         for (int x = 0; x < width; x++) {
    //            // pixelBuffer[y * paddedWidth + (x + 1)] = originalPixels[y * width + x] & 0xFF;
    //            pixelBuffer.set(y * paddedWidth + (x + 1), originalPixels[y * width + x] & 0xFF);
    //         }
    //     }

    //     final byte[] outputPixels = new byte[width * height];
    //     //ห้ามยุ่ง

    //     // int nThreads = Runtime.getRuntime().availableProcessors();
    //     ExecutorService executor = Executors.newFixedThreadPool(nThreads);

    //     final CountDownLatch latch = new CountDownLatch(nThreads);

    //     // ตัวแปรสำคัญ: ใช้บอกว่าแต่ละแถวถูกประมวลผลไปถึงคอลัมน์ไหนแล้ว
    //     // volatile เพื่อให้แน่ใจว่าทุก thread จะเห็นค่าล่าสุดเสมอ
    //     final int[] rowProgress = new int[height];
    //     for (int i = 0; i < height; i++) {
    //         rowProgress[i] = -1; // -1 หมายถึงยังไม่เริ่ม
    //     }
    //     long startTime = System.nanoTime();

    //     // สร้าง n tasks โดยแต่ละ task จะรับผิดชอบหลายแถว
    //     for (int threadId = 0; threadId < nThreads; threadId++) {
    //         final int currentThreadId = threadId;
    //         executor.submit(() -> {
    //             try {
    //                 // Thread แต่ละตัวจะประมวลผลแถวของตัวเองแบบเว้นระยะ (Interleaved)
    //                 // เช่น threadId=0 จะทำแถว 0, n, 2n, ...
    //                 for (int y = currentThreadId; y < height; y += nThreads) {

    //                     for (int x = 0; x < width; x++) {

    //                         // --- นี่คือหัวใจของ Wavefront Pipelining ---
    //                         // ถ้าไม่ใช่แถวแรกสุด (y > 0)
    //                         // ให้รอจนกว่าแถวด้านบน (y-1) จะประมวลผลเลยคอลัมน์ที่เราต้องการไปแล้ว
    //                         // เราต้องการข้อมูลจาก (x+1, y-1) ดังนั้นเราต้องรอให้ progress >= x+1
    //                         if (y > 0) {
    //                             while (rowProgress[y - 1] < x + 1 && rowProgress[y - 1] != width - 1) {
    //                                 // แค่รอเฉยๆ (Spin wait) หรืออาจจะ Thread.yield()
    //                                 // เพื่อให้ CPU ไปทำงานอื่นก่อนได้
    //                                 Thread.yield(); 
    //                             }
    //                         }

    //                         // --- ส่วนประมวลผลหลักของ Error Diffusion (เหมือนเดิม) ---
    //                         int bufferIndex = y * paddedWidth + (x + 1);
    //                         //pixelBuffer[bufferIndex]
    //                         int oldPixel = pixelBuffer.get(bufferIndex);
    //                         int newPixel = (oldPixel <= THRESHOLD) ? 0 : 255;
    //                         outputPixels[y * width + x] = (byte) newPixel;
    //                         int quantError = oldPixel - newPixel;

    //                         // 3. ใช้ Integer Programming
    //                         pixelBuffer.addAndGet(bufferIndex + 1, Math.floorDiv(quantError * 7, 16));
    //                         pixelBuffer.addAndGet(bufferIndex - 1 + paddedWidth, Math.floorDiv(quantError * 3, 16));
    //                         pixelBuffer.addAndGet(bufferIndex + paddedWidth, Math.floorDiv(quantError * 5, 16));
    //                         pixelBuffer.addAndGet(bufferIndex + 1 + paddedWidth, Math.floorDiv(quantError * 1, 16));

    //                         // อัปเดต progress ของแถวปัจจุบัน
    //                         rowProgress[y] = x;
    //                     }
    //                 }
    //             } finally {
    //                 latch.countDown(); // บอก main thread ว่า task นี้เสร็จแล้ว
    //             }
    //         });
    //     }

    //     try {
    //         latch.await(); // main thread จะรอจนกว่า task ทั้งหมดจะเสร็จ
    //     } catch (InterruptedException e) {
    //         Thread.currentThread().interrupt();
    //     }
    //     long endTime = System.nanoTime();
    //     executor.shutdown();

    //     if (produceImage) {
    //         BufferedImage ditheredImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    //         byte[] ditheredData = ((DataBufferByte) ditheredImage.getRaster().getDataBuffer()).getData();
    //         System.arraycopy(outputPixels, 0, ditheredData, 0, outputPixels.length);
    //         saveImage(ditheredImage, "output/" + baseName + "_parallel.png");
    //     }
    //     return endTime - startTime;
    // }

    public static void saveImage(BufferedImage image, String outputPath) {
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            ImageIO.write(image,"PNG", outputFile);

        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Could not determine image format from file name. Please use a valid extension (e.g., .png).");
        }
    }

    public static void createPerformanceChart(long[] coreTimes_ns, long sequentialTime_ns, int maxCores, String imageName) {
        int chartWidth = 800, chartHeight = 600;
        BufferedImage chart = new BufferedImage(chartWidth, chartHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = chart.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, chartWidth, chartHeight);

        long maxTime_ns = 0;
        for (long t : coreTimes_ns) if (t > maxTime_ns) maxTime_ns = t;
        if (maxTime_ns == 0) maxTime_ns = 1; // Avoid division by zero

        int margin = 80;
        int plotWidth = chartWidth - 2 * margin;
        int plotHeight = chartHeight - 2 * margin;

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1)); // Thinner axis line
        g.drawLine(margin, chartHeight - margin, chartWidth - margin, chartHeight - margin); // X-axis
        g.drawLine(margin, margin, margin, chartHeight - margin); // Y-axis

        g.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        String title = "Performance: " + imageName;
        g.drawString(title, (chartWidth - fm.stringWidth(title)) / 2, 40);

        // --- Y-axis ticks modification (now in milliseconds) ---
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        int numYTicks = 7; // Use fewer ticks for a cleaner look
        double maxTime_ms = maxTime_ns / 1_000_000.0;

        for (int i = 0; i <= numYTicks; i++) {
            int y = chartHeight - margin - (i * plotHeight / numYTicks);
            double tickValue_ms = (double) i * maxTime_ms / numYTicks;
            // Format to show one decimal place for milliseconds
            String yLabel = String.format("%.1f", tickValue_ms);
            g.drawString(yLabel, margin - fm.stringWidth(yLabel) - 5, y + (fm.getHeight() / 4));
        }

        // --- Bar drawing modification ---
        int numBars = Math.min(maxCores, coreTimes_ns.length);
        int barGroupWidth = plotWidth / numBars;

        for (int i = 0; i < numBars; i++) {
            int x = margin + (i * barGroupWidth) + barGroupWidth / 4;
            int barWidth = barGroupWidth / 2;
            int barHeight = (int) ((double) coreTimes_ns[i] / maxTime_ns * plotHeight);

            g.setColor(new Color(70, 130, 180));
            g.fillRect(x, chartHeight - margin - barHeight, barWidth, barHeight);

            String label = String.valueOf(i + 1);
            g.setColor(Color.BLACK);
            g.drawString(label, x + (barWidth - fm.stringWidth(label)) / 2, chartHeight - margin + 20);
        }

        // --- Axis label modification ---
        g.setFont(new Font("kanit", Font.BOLD, 14));
        fm = g.getFontMetrics();
        String xLabel = "Number of CPU Cores";
        g.drawString(xLabel, (chartWidth - fm.stringWidth(xLabel)) / 2, chartHeight - 20);

        g.rotate(-Math.PI / 2);
        String yLabel = "Execution Time (ms)";
        g.drawString(yLabel, -(chartHeight + fm.stringWidth(yLabel)) / 2 + 50, 30);

        g.dispose();

        try {
            ImageIO.write(chart, "PNG", new File("output/" + imageName + "_performance.png"));
            System.out.println("\nPerformance chart saved: output/" + imageName + "_performance.png");
        } catch (IOException e) { System.err.println("Error saving chart: " + e.getMessage()); }
    }
    public static void main(String[] args) {
        String inputPath = "bigset/noise_10000x10000.png";
        String baseName = "newnoise";

        // Load and convert to grayscale
        BufferedImage grayscaleImg = loadAndConvertToGrayscale(inputPath);
        if (grayscaleImg == null) return;

        saveImage(grayscaleImg, "output/" + baseName + "_grayscale.png");

        // Sequential
        long seqResult = ErrorDiffusionSequential(grayscaleImg,true,baseName);
        // Parallel with different core counts
        int maxCores = Runtime.getRuntime().availableProcessors();
        System.out.println("\nRunning Parallel Error Diffusion");
        System.out.println("   " + "-".repeat(50));
        System.out.println("   Cores | Time (ms) | Speedup");
        System.out.println("   " + "-".repeat(50));

        long[] times = new long[maxCores];
        for (int n = 1; n <= maxCores; n++) {
            long parResult = ErrorDiffusionParallel(grayscaleImg, n, true,baseName);
            times[n - 1] = parResult;

            double speedup = (double) seqResult / parResult;
            System.out.println(String.format("   %5d | %9.2f | %.2fx",
                    n, parResult / 1_000_000.0, speedup));
        }
        // createPerformanceChart(times, maxCores, baseName);
        createPerformanceChart(times, seqResult, maxCores, baseName);
    }
}