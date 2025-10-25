# การทำ Error Diffusion แบบขนานในภาษา Java (Optimized Parallel Error Diffusion)

โปรเจกต์นี้สาธิตการแปลงภาพขาวเทา (Grayscale) ไปเป็นภาพขาวดำ (Black & White) โดยใช้อัลกอริทึม **Floyd–Steinberg Error Diffusion**

จุดเด่นของโปรเจกต์นี้คือการเปรียบเทียบประสิทธิภาพระหว่างเวอร์ชันที่ทำงานแบบ **Sequential** (Thread เดียว) กับเวอร์ชันที่ทำงานแบบ **Parallel** (หลาย Thread) ที่ผ่านการปรับแต่งประสิทธิภาพขั้นสูงเพื่อจัดการกับปัญหา Data Dependency

## 🌟 คุณสมบัติหลัก (Features)

* **`fsSequential`**: อัลกอริทึม Error Diffusion แบบดั้งเดิมที่ทำงานบน Thread เดียว
* **`fsParallelChunked`**: อัลกอริทึมแบบขนาน (Multi-thread) ที่ออกแบบมาเพื่อแก้ปัญหา Data Dependency โดยเฉพาะ
* **Benchmark Framework**: มีระบบวัดประสิทธิภาพในตัว ทำการ Warmup, วัดผลซ้ำหลายครั้งเพื่อหาค่าเฉลี่ย (NUM_RUNS), และเปรียบเทียบผลลัพธ์
* **Speedup Calculation**: คำนวณและแสดงผล "Speedup" (อัตราเร่ง) ของเวอร์ชัน Parallel เทียบกับ Sequential
* **Performance Chart**: สร้างกราฟเปรียบเทียบประสิทธิภาพ (เวลาที่ใช้ vs จำนวน Thread) และบันทึกเป็นไฟล์ `.png` โดยอัตโนมัติ

---

## 💡 ปัญหา Data Dependency ใน Error Diffusion

เราไม่สามารถแบ่งให้แต่ละ Thread ทำงานทีละหลายๆ แถวพร้อมกัน (เช่น Thread 1 ทำแถว 0-99, Thread 2 ทำแถว 100-199) ได้ตรงๆ

**เพราะอะไร?**
การคำนวณ Pixel ที่ `[y, x]` (แถว y, คอลัมน์ x) จำเป็นต้องใช้ค่า Error (ส่วนต่างความผิดพลาด) ที่ถูกกระจายมาจาก **แถวด้านบน (`y-1`)**

จากอัลกอริทึม Floyd-Steinberg, Pixel `[y, x]` จะได้รับ Error มาจาก:
* `[y-1, x-1]` (ซ้ายบน)`]
* `[y-1, x]` (ตรงบน)`]
* `[y-1, x+1]` (ขวาบน)`]
* รวมถึง `[y, x-1]` (ซ้าย)`]

ดังนั้น Thread ที่กำลังประมวลผล `row[y]` จึงต้องรอให้ Thread ที่ประมวลผล `row[y-1]` ทำงานเสร็จไปก่อนในระดับหนึ่งเสมอ นี่คือปัญหาคอขวดที่เรียกว่า **Data Dependency**

---

## 🌊 การแก้ปัญหาด้วย Parallel Wavefront (Chunked)

โค้ดนี้ใช้วิธีที่เรียกว่า **"Parallel Wavefront"** (หรือ Pipeline) ร่วมกับการแบ่งข้อมูลเป็น "Chunk" เพื่อแก้ปัญหานี้

### 1. การแบ่งงานแบบ Interleaved (Row-striped)
แทนที่จะแบ่งงานเป็นกลุ่มแถว (เช่น 0-99, 100-199), เราจะแบ่งงานแบบ "สลับฟันปลา" โดย `T` คือจำนวน Thread ทั้งหมด:
* **Thread 0** รับผิดชอบแถว 0, T, 2T, 3T, ...
* **Thread 1** รับผิดชอบแถว 1, T+1, 2T+1, ...
* **Thread 2** รับผิดชอบแถว 2, T+2, 2T+2, ...

โค้ดที่ใช้คือ: `for (int y = tid; y < H; y += T)`

### 2. การแบ่งแถวเป็น "Chunk"
ในแต่ละแถวที่ Thread รับผิดชอบ, งานจะถูกแบ่งย่อยเป็น "ก้อน" (Chunk) ขนาดเท่าๆ กัน (เช่น 128 pixels ตามค่า `CHUNK = 128`)

### 3. การรอ (Synchronization)
นี่คือหัวใจสำคัญ:
* Thread ที่กำลังทำ `row[y]` **Chunk ที่ `k`**
* จะต้องรอให้ Thread ที่ทำ `row[y-1]` (แถวบน) ประมวลผล **Chunk ที่ `k`** (หรือ `k+1` ขึ้นอยู่กับการ implement) เสร็จสิ้นก่อน
* เมื่อแถวบนทำ Chunk `k` เสร็จ, มันจะส่งสัญญาณ "ปลดล็อค" ให้แถวล่างเริ่มทำ Chunk `k` ของตัวเองได้

### 4. `AtomicIntegerArray progress`
โค้ดนี้ใช้ `AtomicIntegerArray` เป็น "กระดานบอกความก้าวหน้า"
* `progress[y]` จะเก็บค่าว่า `row[y]` (แถวที่ y) ประมวลผลเสร็จสิ้นถึง **คอลัมน์ที่เท่าไหร่** แล้ว
* Thread ที่ทำ `row[y]` จะ "วนรอ" (spin-wait) โดยเช็คค่า `progress[y-1]`
* เมื่อ `progress[y-1]` มีค่ามากพอ (เช่น แถวบนนำหน้าไป 1 chunk), Thread `row[y]` จึงจะเริ่มทำงานใน Chunk ของตัวเอง และเมื่อทำเสร็จ ก็จะอัปเดต `progress[y]` เพื่อส่งสัญญาณให้ `row[y+1]` ทำงานต่อ

ผลลัพธ์ที่ได้คือ Thread ทั้งหมดจะทำงาน "ไล่ตามกัน" เป็น "คลื่น" (Wavefront) ในแนวทแยงจากมุมบนซ้ายไปยังมุมล่างขวาของภาพ

---

## 🚀 การปรับแต่งประสิทธิภาพ (Optimizations)

โค้ดนี้มีการปรับแต่งประสิทธิภาพหลายจุดเพื่อให้ทำงานได้เร็วที่สุด:

**1. Dummy Pixels (Padding)**
* มีการสร้าง Buffer `buf` ให้มีขนาด `(H + 2) * (W + 2)` ซึ่งใหญ่กว่ารูปจริง buf = new int[(H + 2) * (W + 2)];`]
* วิธีนี้เป็นการเพิ่ม "ขอบ" (Dummy pixels) รอบรูปจริง ทำให้ Loop หลักที่กระจาย Error ไม่ต้องเสียเวลาตรวจสอบเงื่อนไข `if (x == 0)` หรือ `if (y == W-1)` ตลอดเวลา ช่วยลด Branching ใน Hot Loop

**2. Integer Programming**
* ใช้การคำนวณ Error ด้วยเลขจำนวนเต็ม `Math.floorDiv(err * 7, 16)` แทนการใช้เลขทศนิยม (`(double)err * 7.0 / 16.0`) ซึ่งทำงานได้เร็วกว่า

**3. การป้องกัน False Sharing**
* `PROG_STRIDE = 16` ถูกใช้เป็น "ช่องว่าง" (Padding) ใน `AtomicIntegerArray progress`
* เพื่อให้ `progress[y]` และ `progress[y+1]` (ซึ่งถูกเขียนโดยคนละ Thread) ไม่อยู่บน **Cache Line** เดียวกันใน CPU
* นี่เป็นการป้องกันปัญหา "False Sharing" ที่ CPU Cores หลายตัวแย่งกันเขียนข้อมูลลง Cache Line เดียวกัน ซึ่งเป็นคอขวดที่ร้ายแรงในการทำ Parallel Programming

**4. Hybrid Spin-Wait**
* การ "วนรอ" ใน `while (progress.get(prevIdx) < need)` ใช้ `Thread.onSpinWait()` ซึ่งเป็นการวนรอแบบประหยัดพลังงาน (ดีกว่า `while(true){}`)
* และหากรอนานเกินไป (`(++spins & 1023) == 0`) มันจะเรียก `LockSupport.parkNanos(BACKOFF_NS)` เพื่อ "พัก" Thread ชั่วคราว (Sleep) แทนการวนรอแบบ 100% (Busy-wait) เพื่อคืน CPU ให้งานอื่น

---

## ⚙️ วิธีใช้งาน

1.  แก้ไขค่า `IN_PATH` (ไฟล์รูปต้นฉบับ) และ `OUT_BASE` (ที่เก็บผลลัพธ์) ในโค้ด
2.  Compile: `javac OptimizedErrorDiffusion.java`
3.  Run: `java OptimizedErrorDiffusion`
4.  โปรแกรมจะทำการ Warmup, Benchmark, และพิมพ์ผลลัพธ์ Speedup ออกทาง Console

## 📊 ผลลัพธ์ (Output)

โปรแกรมจะสร้างไฟล์ 4 ประเภทใน Directory ที่ระบุใน `OUT_BASE`:

1.  `..._gray.png`: รูปต้นฉบับที่ถูกแปลงเป็น Grayscale (เพื่อยืนยัน Input)
2.  `..._seq.png`: ผลลัพธ์จาก `fsSequential` (Single-thread)
3.  `..._par_X.png`: ผลลัพธ์จาก `fsParallelChunked` ที่ใช้จำนวน Thread (`X`) ที่เร็วที่สุด
4.  `..._perf.png`: กราฟเปรียบเทียบประสิทธิภาพระหว่าง Sequential และ Parallel ที่จำนวน Thread ต่างๆ
