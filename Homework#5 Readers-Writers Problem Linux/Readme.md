# ปัญหา Readers-Writers แบบที่ 1 (First Readers-Writers Problem)

การ implement ปัญหาคลาสสิก **First Readers-Writers Problem** โดยใช้ POSIX threads และ semaphores ในภาษา C

## 📋 รายละเอียดปัญหา

ปัญหา Readers-Writers เป็นปัญหาพื้นฐานด้าน synchronization ใน operating systems การ implement นี้จัดการกับ **แบบที่ 1** ซึ่งให้ความสำคัญกับ readers มากกว่า writers โดยรับประกัน:

- **Mutual Exclusion**: Writers มีสิทธิ์เข้าถึง critical section แบบ exclusive
- **Reader Concurrency**: Readers หลายตัวสามารถเข้า critical section พร้อมกันได้
- **No Deadlock**: ระบบรับประกันว่าจะไม่เกิด deadlock
- **Bounded Waiting**: ทุก thread จะเสร็จสิ้นภายในเวลาที่คาดการณ์ได้

### ข้อกำหนด

- **จำนวน threads ทั้งหมด 1,000 ตัว**: 10 writers, 990 readers
- **สุ่มเลือก writers**: Writers ถูกสุ่มเลือกจาก thread pool
- **Readers แบ่งเป็น batches**: Readers แบ่งเป็น 11 batches (แต่ละ batch มี 60-120 threads)
- **เวลาแม่นยำ**: แต่ละ critical section ใช้เวลา 1 วินาทีพอดี โดยใช้ spinlock busy-wait
- **รูปแบบลำดับ**: `R → W → R → W → R → W → ... → W → R`

## 🏗️ สถาปัตยกรรม

### กลยุทธ์การ Synchronization

การ implement นี้ใช้วิธี **semaphore-based orchestration**:

```
Main Thread (Orchestrator)
    ├─ ควบคุมการเข้า critical section ผ่าน semaphores
    ├─ เปิด gate_read สำหรับ reader batches
    ├─ เปิด gate_write สำหรับ writers แต่ละตัว
    └─ รับประกันรูปแบบ R-W-R-W ตามลำดับ

Reader Threads
    ├─ รออยู่ที่ semaphore gate_read
    ├─ ทำงานใน critical section พร้อมกัน (ภายใน batch)
    ├─ Reader ตัวสุดท้ายส่ง signal ว่า batch เสร็จแล้ว
    └─ ไม่พิมพ์ output

Writer Threads
    ├─ รออยู่ที่ semaphore gate_write
    ├─ ทำงานใน critical section แบบ exclusive
    ├─ พิมพ์: no = <ลำดับเข้า CS> x = <ค่าของ x>
    └─ ส่ง signal ว่าเสร็จแล้วกลับไปที่ orchestrator
```

### องค์ประกอบหลัก

| ส่วนประกอบ | หน้าที่ |
|-----------|---------|
| `gate_read` | ควบคุมการเข้าของ reader batch |
| `gate_write` | ควบคุมการเข้าของ writer |
| `batch_done` | ส่งสัญญาณว่า reader batch เสร็จแล้ว |
| `writer_done` | ส่งสัญญาณว่า writer เสร็จแล้ว |
| `enter_seq` | Atomic counter สำหรับนับลำดับเข้า CS |
| `x` | ตัวแปรร่วม (writers เพิ่มค่า) |

## 🔧 รายละเอียดการ Implement

### 1. Spinlock (ห้ามใช้ `sleep()`)

```c
static inline void busy_1s(void) {
    struct timespec t0, now;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    for (;;) {
        clock_gettime(CLOCK_MONOTONIC, &now);
        // คำนวณเวลาที่ผ่านไป
        if (เวลาที่ผ่านไป >= 1.0s) break;
        // Busy-wait (spin)
    }
}
```

**ทำไมต้องใช้ spinlock?**
- โจทย์กำหนดให้: ห้ามใช้ `sleep()`
- ให้เวลาที่แม่นยำพอดี 1 วินาที
- Trade-off: ใช้ CPU สูงเพื่อความแม่นยำ

### 2. การสุ่มเลือก Writers

ใช้ **Fisher-Yates shuffle** เพื่อสุ่มเลือก 10 threads เป็น writers จาก thread pool ทั้งหมด 1,000 ตัว

```c
shuffle(threads)
for k = 0 to 9:
    is_writer[shuffled[k]] = 1
```

### 3. การสุ่มขนาด Reader Batches

สร้างขนาด batch 11 กลุ่มในช่วง `[60, 120]` ที่รวมกันได้พอดี 990:

```c
เริ่มต้น: [90, 90, 90, ..., 90]  // 11 batches
สุ่มปรับ: [78, 95, 83, ..., 91]  // ผลรวม = 990
```

### 4. การติดตามลำดับเข้า CS

```c
// Global atomic counter
atomic_int enter_seq = 0;

// แต่ละ thread เพิ่มค่าเมื่อเข้า CS
int no = atomic_fetch_add(&enter_seq, 1) + 1;
```

วิธีนี้รับประกันว่า `no` แทน**ลำดับที่เข้า critical section จริง** ไม่ใช่ลำดับการสร้าง thread

## 📊 Timeline การทำงาน

```
t=0.0s    : สร้าง 1000 threads (ทุกตัวรออยู่)
t=0.0s    : เปิดประตูสำหรับ Batch 0 (78 readers)
t=0.0-1.0s: 78 readers ทำงานพร้อมกัน
t=1.0s    : Reader ตัวสุดท้าย signal → Writer #1 เข้า
t=1.0-2.0s: Writer #1 ทำงาน → พิมพ์ "no = 79 x = 1"
t=2.0s    : เปิดประตูสำหรับ Batch 1 (95 readers)
...
t=20.0s   : Writer #10 เสร็จสิ้น
t=20.0-21.0s: Batch 10 (91 readers) ทำงาน
t=21.0s   : โปรแกรมจบ
```

**เวลาที่คาดหวัง**: ประมาณ 21 วินาที (11 reader batches + 10 writers)

## 🚀 วิธีใช้งาน

### การ Compile

```bash
gcc -O2 -pthread -o rw_best rw_best.c
```

**Compiler flags:**
- `-O2`: เปิดการ optimize
- `-pthread`: Link POSIX threads library

### การรันโปรแกรม

```bash
./rw_best
```

### ตัวอย่าง Output

```
no = 67   x = 1
no = 189  x = 2
no = 301  x = 3
no = 423  x = 4
no = 534  x = 5
no = 658  x = 6
no = 771  x = 7
no = 889  x = 8
no = 912  x = 9
no = 986  x = 10
Finished in 21.34 seconds. Final x = 10
```

**คำอธิบาย Output:**
- `no`: ลำดับที่เข้า CS ทั้งหมด (รวม readers + writers)
- `x`: ค่าหลังจาก write (ค่าสุดท้ายต้องเป็น 10)
- มีเฉพาะ writers เท่านั้นที่พิมพ์ output

## 📈 ลักษณะด้านประสิทธิภาพ

### เวลาตามทฤษฎี vs เวลาจริง

| เกณฑ์ | ค่า |
|--------|-------|
| เวลาต่ำสุดตามทฤษฎี | 21.0s |
| เวลาทำงานจริง | 21.0 - 21.5s |
| Overhead | ~1-2% |

### Trade-offs ของ Spinlock

จากการวิเคราะห์เชิงประจักษ์ (ดู [SpinLock.pdf](SpinLock.pdf)):

| วิธีการ | Sequential | Concurrent (1000 threads) | การใช้ CPU |
|--------|-----------|---------------------------|-----------|
| Sleep | 1005ms avg | 1001ms avg | 1.4% |
| Spinlock | 1000ms avg | 1046ms avg | 98.8% |
| Hybrid | 1001ms avg | 1004ms avg | 10.0% |

**สรุป**: แม้ spinlock จะให้ความแม่นยำตามทฤษฎี แต่มีปัญหาภายใใต้ high concurrency เนื่องจาก CPU contention อย่างไรก็ตาม spinlock ยังคงเป็นทางเลือกที่ถูกต้องตามข้อกำหนดของโจทย์

## 🎯 การรับประกันความถูกต้อง

### 1. Mutual Exclusion ✅
- มีเพียง writer ตัวเดียวใน CS ในแต่ละครั้ง (บังคับโดย orchestrator)
- Readers ไม่สามารถเข้าได้ระหว่างที่ writer ทำงาน

### 2. Progress ✅
- ไม่มีโอกาสเกิด deadlock (orchestrator ควบคุมทุก gates)
- FIFO semaphore queue รับประกันความเป็นธรรม

### 3. Bounded Waiting ✅
- เวลารอสูงสุด: 21 วินาที (ระยะเวลาทั้งโปรแกรม)
- ไม่มี thread starvation

### 4. Reader Priority ✅
- Readers ใน batch เดียวกันทำงานพร้อมกัน
- Writers รอให้ readers ทั้งหมดใน batch ปัจจุบันเสร็จก่อน

## 🔬 การทดสอบ

### Checklist การตรวจสอบ

- [ ] ค่าสุดท้าย `x = 10` (writers ทุกตัวทำงานแล้ว)
- [ ] เวลาทำงานรวม ≈ 21 วินาที (±0.5s ยอมรับได้)
- [ ] ลำดับเข้า CS ของ writers (`no`) ไม่ซ้ำกันและเรียงตามลำดับ
- [ ] ไม่เกิด race conditions (รันหลายครั้ง)
- [ ] ไม่เกิด deadlocks (โปรแกรมจบเสมอ)

### ข้อจำกัดที่ทราบ

1. **การใช้ CPU สูง**: Spinlock กิน CPU cycles ระหว่าง busy-wait
2. **ไม่ scalable**: ประสิทธิภาพลดลงเมื่อมี threads หลายพันตัว
3. **First problem variant**: Writers อาจ starve ถ้า readers เข้ามาเรื่อยๆ (แก้ไขด้วยการแบ่ง batches)

## 📚 เอกสารอ้างอิง

- Silberschatz, A., Galvin, P. B., & Gagne, G. (2018). *Operating System Concepts* (10th ed.). Wiley.
- POSIX Threads Programming: https://computing.llnl.gov/tutorials/pthreads/
- Semaphore Documentation: `man sem_overview`

## 📄 สัญญาอนุญาต

การ implement นี้จัดทำขึ้นเพื่อการศึกษา สามารถนำไปใช้และดัดแปลงสำหรับโปรเจกต์ทางการศึกษาได้อย่างอิสระ

## 👤 ผู้จัดทำ

จัดทำเป็นส่วนหนึ่งของวิชา Operating Systems - การบ้านปัญหา Readers-Writers

---

**หมายเหตุ**: การ implement นี้ให้ความสำคัญกับความถูกต้องและความชัดเจนมากกว่าการ optimize ประสิทธิภาพ สำหรับระบบ production แนะนำให้พิจารณาใช้ read-write locks (`pthread_rwlock_t`) หรือ lock-free algorithms
