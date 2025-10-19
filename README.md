# 2301371-Operating-Systems


## 📂 โปรเจกต์และแบบฝึกหัด (Projects & Assignments)

### 1. 🐧 Linux Commands Cheat Sheet

**(พื้นฐานการใช้งาน OS)** เอกสารสรุปคำสั่งพื้นฐานของ Linux ที่จำเป็นสำหรับรายวิชา โดยจัดหมวดหมู่ตามหน้าที่การทำงานเพื่อให้ง่ายต่อการค้นหาและทำความเข้าใจ

* **💡 แนวคิดหลัก**: `Command-Line Interface`, `File System Navigation`, `Process & User Management`
* **🛠️ เทคโนโลยี**: `Bash`, `Core Utilities`

> **[ดูเอกสารสรุปฉบับเต็ม](https://github.com/Tewter73/2301371-Operating-Systems/tree/main/Homework%231%20Linux%20Commands)**

---

### 2. ⚙️ การสร้างโปรเซสด้วย fork()

**(การจัดการโปรเซส)** ตัวอย่างการใช้ฟังก์ชัน `fork()` ในภาษา C เพื่อสร้าง Child Processes และใช้ `wait()` เพื่อให้ Parent Process รอการทำงานของลูกๆ ทั้งหมด ซึ่งเป็นพื้นฐานของการจัดการโปรเซสในระบบ Unix

* **💡 แนวคิดหลัก**: `Process Management`, `Process Creation`, `fork()`, `wait()`
* **🛠️ เทคโนโลยี**: `C`

> **[ดูรายละเอียดโปรเจกต์ฉบับเต็ม](https://github.com/Tewter73/2301371-Operating-Systems/tree/main/Homework%232%20Fork)**

---

### 3. 🌐 Python - โปรแกรม Client-Server แบบ Concurrent

**(การประยุกต์ใช้ Concurrency กับ Network)** โปรแกรม Time Server ในภาษา Python ที่สามารถรองรับ Client ได้หลายรายพร้อมกัน โดยใช้เทคนิค Multi-threading ซึ่งเป็นการนำแนวคิดเรื่อง Concurrency มาประยุกต์ใช้กับการสื่อสารผ่านเครือข่าย

* **💡 แนวคิดหลัก**: `Socket Programming`, `Client-Server Architecture`, `Multi-threading`
* **🛠️ เทคโนโลยี**: `Python`, `socket`, `threading`, `Shell Script`

> **[ดูรายละเอียดโปรเจกต์ฉบับเต็ม](https://github.com/Tewter73/2301371-Operating-Systems/tree/main/Homework%233%20Client%20Server)**

---

### 4. 🖼️ การประมวลผลภาพ: Sequential (Python) vs Parallel (Java)

โปรเจกต์ที่ศึกษาและเปรียบเทียบประสิทธิภาพของการทำ **Image Dithering** ด้วยอัลกอริทึม Floyd–Steinberg โดยแบ่งออกเป็น 2 ส่วนหลัก:

* **เวอร์ชัน Python**: ใช้เป็น Baseline และชุดเครื่องมือ ประกอบด้วยสคริปต์สำหรับสร้างภาพทดสอบ, การทำ Dithering แบบลำดับ (Sequential), และการเปรียบเทียบความเหมือนของภาพ
* **เวอร์ชัน Java**: เน้นการเพิ่มประสิทธิภาพสูงสุด โดย Implement อัลกอริทึมเดียวกันในรูปแบบขนาน (Parallel) โดยใช้เทคนิค **Wavefront Pattern** เพื่อเปรียบเทียบประสิทธิภาพกับแบบลำดับอย่างชัดเจน

* **💡 แนวคิดหลัก**: `Image Processing`, `Parallelism & Concurrency`, `Synchronization`, `Wavefront Pattern`, `Performance Benchmarking`, `Algorithms`
* **🛠️ เทคโนโลยี**: `Java`, `Python`, `Java Concurrency Utilities`, `NumPy`, `Pillow (PIL)`, `Matplotlib`

> **[ดูรายละเอียดเวอร์ชัน Java](https://github.com/Tewter73/2301371-Operating-Systems/tree/main/Homework%234%20Error%20Diffusion%20Java)**
>
> **[ดูรายละเอียดเวอร์ชัน Python](https://github.com/Tewter73/2301371-Operating-Systems/tree/main/Homework%234%20Error%20Diffusion%20Python)**

---

### 5. 🧠 ปัญหา Readers-Writers (Readers-Writers Problem)

**(การจัดการภาวะพร้อมกัน)** การ Implement ปัญหา Synchronization คลาสสิก "First Readers-Writers Problem" ในภาษา C โดยใช้ POSIX threads และ semaphores เพื่อจัดการการเข้าถึงทรัพยากรที่ใช้ร่วมกัน

* **💡 แนวคิดหลัก**: `Synchronization`, `Mutual Exclusion`, `Semaphores`, `Pthreads`, `Spinlock`
* **🛠️ เทคโนโลยี**: `C`, `POSIX Threads`

> **[ดูรายละเอียดโปรเจกต์ฉบับเต็ม](https://github.com/Tewter73/2301371-Operating-Systems/tree/main/Homework%235%20Readers-Writers%20Problem%20Linux)**
