# Parallel Error Diffusion Dithering in Java

โปรเจกต์นี้เป็นการนำเสนอการประมวลผลภาพด้วยเทคนิค **Error Diffusion Dithering** (อัลกอริทึม Floyd–Steinberg) ในภาษา Java โดยเปรียบเทียบประสิทธิภาพระหว่างการประมวลผล **แบบลำดับ (Sequential)** และ **แบบขนาน (Parallel)** เพื่อแสดงให้เห็นถึงประโยชน์ของการใช้ Multi-threading ในการเร่งความเร็วการทำงาน

โปรแกรมไม่เพียงแต่จะทำการแปลงภาพ แต่ยังทำการทดสอบ, วัดประสิทธิภาพ, และสร้างกราฟเปรียบเทียบผลลัพธ์โดยอัตโนมัติ


## 📜 สารบัญ

1.  **ภาพรวมโปรเจกต์ (Overview)**
2.  **แนวคิดหลัก (Core Concepts)**
    * อัลกอริทึม Error Diffusion แบบลำดับ
    * ความท้าทายในการทำแบบขนาน
    * การแก้ปัญหาด้วย Wavefront Parallelism
3.  **โครงสร้างโค้ด (Code Breakdown)**
4.  **วิธีรันโปรแกรม (How to Run)**
5.  **ผลลัพธ์ที่คาดหวัง (Expected Output)**
6.  **โค้ดทั้งหมด (Full Code)**

---

## 1. ภาพรวมโปรเจกต์ (Overview)

เป้าหมายหลักของโปรเจกต์นี้คือการเร่งความเร็วการทำ Dithering บนภาพขนาดใหญ่ด้วยการประมวลผลแบบขนาน โดยใช้ประโยชน์จาก CPU ที่มีหลาย Core

### ✨ คุณสมบัติเด่น (Key Features)

* **Sequential Implementation**: การประมวลผลแบบมาตรฐานตามอัลกอริทึม Floyd–Steinberg
* **Parallel Implementation**: การประมวลผลแบบขนานโดยใช้เทคนิค **Wavefront Parallelism** เพื่อจัดการกับ Data Dependency
* **Performance Benchmarking**: ระบบการทดสอบที่รันโค้ดหลายครั้งเพื่อหาค่าเฉลี่ยที่แม่นยำ และเปรียบเทียบ Speedup/Efficiency
* **Chart Generation**: สร้างกราฟเปรียบเทียบประสิทธิภาพระหว่างการใช้จำนวน Core ที่แตกต่างกันโดยอัตโนมัติ
* **Thread Safety**: ใช้ `AtomicIntegerArray` เพื่อให้แน่ใจว่าการเข้าถึงข้อมูลพิกเซลจากหลายเธรดมีความปลอดภัยและถูกต้อง

---

## 2. แนวคิดหลัก (Core Concepts)

### อัลกอริทึม Error Diffusion แบบลำดับ

เป็นเทคนิคการแปลงภาพ Grayscale ให้เป็นภาพขาว-ดำ โดยพยายามรักษาระดับความสว่างโดยรวมของภาพไว้ มีขั้นตอนดังนี้:
1.  ประมวลผลพิกเซลทีละจุด จากซ้ายไปขวา, บนลงล่าง
2.  ณ พิกเซลปัจจุบัน, ตัดสินใจว่าจะเปลี่ยนเป็นสีดำหรือขาว (Quantization)
3.  คำนวณหาค่าความผิดพลาด (Quantization Error) ซึ่งคือผลต่างระหว่างสีเดิมกับสีใหม่
4.  **กระจาย** ค่าความผิดพลาดนั้นไปยังพิกเซลข้างเคียงที่ยังไม่ถูกประมวลผล

### ความท้าทายในการทำแบบขนาน

ปัญหาหลักคือ **Data Dependency** การคำนวณพิกเซล ณ ตำแหน่ง `(x, y)` จำเป็นต้องใช้ค่า Error ที่ถูกกระจายมาจากพิกเซล `(x-1, y)`, `(x-1, y-1)`, `(x, y-1)`, และ `(x+1, y-1)` ทำให้ไม่สามารถแบ่งภาพเป็นส่วนๆ แล้วประมวลผลพร้อมกันแบบตรงๆ ได้ เพราะผลลัพธ์จะผิดเพี้ยน

### 💡 การแก้ปัญหาด้วย Wavefront Parallelism

โปรเจกต์นี้ใช้เทคนิคที่เรียกว่า **Wavefront Parallelism** เพื่อแก้ปัญหานี้:
1.  **แบ่งภาพตามแนวนอน**: แบ่งภาพออกเป็นส่วนๆ ตามความสูง (Horizontal Chunks) และมอบหมายให้แต่ละเธรดรับผิดชอบคนละส่วน
2.  **Synchronization ระหว่างแถว**: เธรดที่กำลังจะประมวลผลแถวที่ `y` จะต้อง **หยุดรอ** จนกว่าเธรดที่รับผิดชอบแถวที่ `y-1` จะประมวลผลเสร็จสิ้นก่อน
3.  **การทำงานแบบท่อน้ำ (Pipeline)**: การรอคอยนี้ทำให้เกิดรูปแบบการทำงานที่เหมือน "คลื่น" (Wavefront) ที่วิ่งจากมุมบนซ้ายไปยังมุมล่างขวาของภาพ แม้ว่าเธรดจะต้องรอแถวก่อนหน้า แต่มันสามารถเริ่มทำงานได้ทันทีที่แถวนั้นเสร็จ ทำให้เกิดการทำงานที่ซ้อนทับกัน (Overlapping) และช่วยลดเวลาโดยรวมลงได้
4.  **Thread-Safe Data**: ใช้ `AtomicIntegerArray` สำหรับ `pixelBuffer` เพื่อให้เธรดหลายตัวสามารถอ่านและเขียนค่า Error ที่กระจายข้ามแถวกันได้อย่างปลอดภัยโดยไม่ต้องใช้ Lock

---

## 3. โครงสร้างโค้ด (Code Breakdown)

ไฟล์ `ParallelErrorDiffusion.java` ประกอบด้วยเมธอดหลักๆ ดังนี้:

* `main()`:
    * เป็นจุดเริ่มต้นของโปรแกรม
    * จัดการการโหลดภาพ, รันการทดสอบทั้งแบบ Sequential และ Parallel (โดยวนลูปทดสอบกับจำนวน Core ที่ 1 ถึง N)
    * พิมพ์ตารางสรุปผลลัพธ์ (เวลา, Speedup, Efficiency)
    * บันทึกภาพผลลัพธ์จากวิธีที่ดีที่สุด และสร้างกราฟเปรียบเทียบ

* `applyErrorDiffusionSequential()`:
    * เมธอดสำหรับประมวลผลแบบลำดับ (Single-thread)
    * ใช้เป็น Baseline ในการวัดประสิทธิภาพ

* `applyErrorDiffusionParallelOptimized()`:
    * เมธอดสำหรับประมวลผลแบบขนานโดยใช้ `ExecutorService` (Thread Pool)
    * แบ่งงานตามแนวนอนและใช้ `AtomicIntegerArray` ร่วมกับ `while(rowComplete.get(...) == 0)` เพื่อทำ Wavefront Synchronization

* `createPerformanceChart()`:
    * ใช้ `Java 2D Graphics` (AWT) เพื่อวาดและบันทึกกราฟแท่งเปรียบเทียบประสิทธิภาพ

* `loadAndConvertToGrayscale()` และ `saveImage()`:
    * เมธอดเสริมสำหรับจัดการไฟล์รูปภาพ

---

## 4. วิธีรันโปรแกรม (How to Run)

### สิ่งที่ต้องมี

* Java Development Kit (JDK) เวอร์ชัน 11 หรือสูงกว่า

### ขั้นตอนการรัน

1.  **เตรียมไฟล์ภาพ**:
    * สร้างโฟลเดอร์ชื่อ `original_image` ในไดเรกทอรีเดียวกับไฟล์ `.java`
    * นำไฟล์ภาพที่ต้องการทดสอบไปใส่ในโฟลเดอร์นี้ และตั้งชื่อเป็น `10k-Image.png` (หรือแก้ไขชื่อในโค้ด `main` method)

2.  **คอมไพล์โค้ด**:
    * เปิด Terminal หรือ Command Prompt แล้วไปยังที่อยู่ของไฟล์
    * รันคำสั่ง:
    ```bash
    javac ParallelErrorDiffusion.java
    ```

3.  **รันโปรแกรม**:
    * รันคำสั่ง:
    ```bash
    java ParallelErrorDiffusion
    ```

โปรแกรมจะเริ่มทำงาน, แสดงผลลัพธ์ทางหน้าจอ, และบันทึกไฟล์ผลลัพธ์ลงในโฟลเดอร์ `output` ที่จะถูกสร้างขึ้นโดยอัตโนมัติ

---

## 5. ผลลัพธ์ที่คาดหวัง (Expected Output)

### Console Output
โปรแกรมจะแสดงผลการทดสอบในรูปแบบตาราง:

```
Image size: 10240 x 5760 pixels
Running 3 iterations per configuration for accuracy...

=== Sequential Error Diffusion ===
  Run 1: 5210.45 ms
  Run 2: 5189.33 ms
  Run 3: 5201.12 ms
Average: 5200.30 ms

=== Parallel Error Diffusion (Optimized Wavefront) ===
Note: Testing up to 12 cores (avoiding SMT overhead)
----------------------------------------------------------------------
Cores      | Time (ms)       | Speedup      | Efficiency
----------------------------------------------------------------------
1          | 5312.45         | 0.98x        | 97.9%
2          | 2789.01         | 1.86x        | 93.2%
...
6          | 1150.88         | 4.52x        | 75.3%      ★
...
12         | 1210.55         | 4.30x        | 35.8%

★ Best performance: 6 cores (average of 3 runs)
```

### Generated Files
ไฟล์ทั้งหมดจะถูกบันทึกในโฟลเดอร์ `output`:
* `10k_grayscale.png`: ภาพต้นฉบับที่แปลงเป็น Grayscale
* `10k_sequential.png`: ภาพผลลัพธ์จากการประมวลผลแบบลำดับ
* `10k_parallel.png`: ภาพผลลัพธ์จากการประมวลผลแบบขนานด้วยจำนวน Core ที่ดีที่สุด
* `10k_performance.png`: กราฟแท่งเปรียบเทียบประสิทธิภาพ


---

## 6. โค้ดทั้งหมด (Full Code)

```java
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
            long time = applyErrorDiffusionSequential(grayscaleImage, run == 0,
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
                long time = applyErrorDiffusionParallelOptimized(grayscaleImage, n, false, null);
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
        applyErrorDiffusionParallelOptimized(grayscaleImage, bestCores, true, baseName + "_parallel.png");

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
```
