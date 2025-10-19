# โปรแกรม Time Server และ Client ด้วย Python

โปรเจกต์นี้เป็นการสาธิตสถาปัตยกรรม Client-Server ขั้นพื้นฐานในภาษา Python โดยใช้ซ็อกเก็ต (sockets) และเธรด (threading) ตัวเซิร์ฟเวอร์ถูกออกแบบมาเพื่อรองรับการเชื่อมต่อจาก Client หลายรายพร้อมกัน (Concurrency) โดยจะส่งข้อมูลเวลาปัจจุบันให้กับ Client แต่ละรายทุกๆ วินาที นอกจากนี้ยังมีไฟล์ Shell Script สำหรับใช้จำลองการเชื่อมต่อของ Client จำนวนมากเพื่อการทดสอบ



##  ส่วนประกอบหลัก

1.  **`server.py`**: TCP เซิร์ฟเวอร์ที่ทำงานแบบ Multi-threaded เพื่อรอรับการเชื่อมต่อจาก Client
2.  **`client.py`**: TCP Client สำหรับเชื่อมต่อไปยังเซิร์ฟเวอร์เพื่อรับข้อมูล
3.  **`run_clients.sh`**: Shell Script สำหรับรัน Client หลายๆ ตัวพร้อมกันเพื่อทดสอบประสิทธิภาพของเซิร์ฟเวอร์

---

## 📜 `server.py` - เซิร์ฟเวอร์ที่ทำงานพร้อมกัน

เซิร์ฟเวอร์จะทำหน้าที่สร้างซ็อกเก็ต, ผูกกับ `HOST` และ `PORT` ที่กำหนด, และรอรับการเชื่อมต่อ คุณสมบัติเด่นคือการทำงานพร้อมกัน ซึ่งเกิดขึ้นได้โดยการสร้างเธรด (thread) ใหม่ขึ้นมาสำหรับ Client ทุกรายที่เชื่อมต่อเข้ามา

### การทำงานหลัก:
* เธรดหลัก (Main Thread) จะทำงานในลูปไม่มีที่สิ้นสุดเพื่อรอรับการเชื่อมต่อใหม่ๆ ผ่าน `server_socket.accept()`
* เมื่อรับการเชื่อมต่อใหม่เข้ามาได้ เซิร์ฟเวอร์จะสร้างและเริ่มการทำงานของ `threading.Thread` ใหม่ทันที
* เธรดที่ถูกสร้างขึ้นใหม่นี้จะไปทำงานในฟังก์ชัน `handle_client` ซึ่งรับผิดชอบการสื่อสารทั้งหมดกับ Client รายนั้นโดยเฉพาะ
* ฟังก์ชัน `handle_client` จะส่งเวลาปัจจุบันของเซิร์ฟเวอร์ไปยัง Client อย่างต่อเนื่องทุกๆ 1 วินาที จนกว่าการเชื่อมต่อจะถูกตัด

การออกแบบเช่นนี้ช่วยให้เธรดหลักของเซิร์ฟเวอร์ยังคงพร้อมรับ Client รายใหม่ๆ อยู่เสมอ ในขณะที่เธรดอื่นๆ ก็กำลังทำงานเพื่อให้บริการ Client ที่เชื่อมต่ออยู่แล้ว

```python
# server.py
import socket
import threading
from datetime import datetime
import time

# --- Configuration ---
HOST = '127.0.0.1'
PORT = 6013

def handle_client(client_socket, client_address):
    """
    Handles a single client connection. Each instance of this function
    runs in a separate thread.
    """
    print(f"[NEW CONNECTION] {client_address} connected.")
    
    try:
        while True:
            current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            # Encode string to bytes for network transmission.
            client_socket.send(current_time.encode('utf-8'))
            time.sleep(1)
    except (ConnectionResetError, BrokenPipeError):
        print(f"[CONNECTION CLOSED] {client_address} disconnected.")
    finally:
        client_socket.close()

def start_server():
    """
    Initializes the server socket and listens for incoming connections.
    """
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((HOST, PORT))
    server_socket.listen(5) # Set the backlog for incoming connections.
    print(f"[LISTENING] Server is listening on {HOST}:{PORT}")
    
    while True:
        # Accept new connections (this is a blocking call).
        client_sock, client_addr = server_socket.accept()
        
        # Create and start a new thread to handle the client.
        thread = threading.Thread(target=handle_client, args=(client_sock, client_addr))
        thread.start()
        
        # The main thread's active_count() includes itself.
        print(f"[ACTIVE CONNECTIONS] {threading.active_count() - 1}")

if __name__ == "__main__":
    start_server()
```

---

## 📜 `client.py` - ไคลเอนต์

ไคลเอนต์เป็นสคริปต์ที่ทำหน้าที่เชื่อมต่อไปยังที่อยู่ของเซิร์ฟเวอร์ เมื่อเชื่อมต่อสำเร็จ จะเข้าสู่ลูปเพื่อรอรับ, ถอดรหัส, และพิมพ์ข้อมูลที่เซิร์ฟเวอร์ส่งมาอย่างต่อเนื่อง

### การทำงานหลัก:
* สร้างซ็อกเก็ตและใช้ `client_socket.connect()` เพื่อเชื่อมต่อไปยังเซิร์ฟเวอร์
* เข้าสู่ `while` ลูปเพื่อรอรับข้อมูล
* `client_socket.recv(1024)` จะหยุดรอจนกว่าจะได้รับข้อมูลจากเซิร์ฟเวอร์
* หาก `recv` คืนค่าว่างเปล่า หมายความว่าเซิร์ฟเวอร์ได้ปิดการเชื่อมต่อแล้ว ไคลเอนต์จะออกจากลูป
* ข้อมูลที่ได้รับจะถูกถอดรหัสจาก `bytes` เป็น `utf-8` (string) ก่อนจะพิมพ์ออกทางหน้าจอ

```python
# client.py
import socket

# --- Configuration ---
HOST = '127.0.0.1'  # Server IP address
PORT = 6013         # Server port

try:
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((HOST, PORT))
    print(f"Successfully connected to server at {HOST}:{PORT}")
    
    while True:
        # Receive data from the server (buffer size: 1024 bytes).
        data = client_socket.recv(1024)
        
        # An empty response indicates the server has closed the connection.
        if not data:
            print("Server closed the connection.")
            break
            
        # Decode bytes to string for printing.
        print(f"Received from server: {data.decode('utf-8')}")

except ConnectionRefusedError:
    print(f"Connection failed. Ensure the server is running.")
except Exception as e:
    print(f"An error occurred: {e}")
finally:
    print("Closing connection.")
    client_socket.close()
```

---

## 📜 `run_clients.sh` - สคริปต์ทดสอบ

Shell Script นี้เป็นเครื่องมือง่ายๆ สำหรับทดสอบความสามารถของเซิร์ฟเวอร์ในการรองรับหลายการเชื่อมต่อ โดยจะทำการรันไคลเอนต์หลายๆ ตัวให้ทำงานในเบื้องหลัง (background)

### การทำงานหลัก:
* ตัวแปร `NUM_CLIENTS` ใช้กำหนดจำนวนไคลเอนต์ที่ต้องการจำลอง
* `for` ลูปจะวนทำงานตั้งแต่ 1 ถึงค่าที่กำหนดใน `NUM_CLIENTS`
* ภายในลูป คำสั่ง `python3 client.py &` จะรันสคริปต์ไคลเอนต์ โดยเครื่องหมาย `&` มีความสำคัญอย่างยิ่ง เพราะเป็นการสั่งให้โปรเซสทำงานใน **เบื้องหลัง (background)** ซึ่งทำให้สคริปต์สามารถรันไคลเอนต์ตัวต่อไปได้ทันทีโดยไม่ต้องรอให้ตัวแรกทำงานเสร็จก่อน

```bash
#!/bin/bash

# Number of concurrent clients to simulate.
NUM_CLIENTS=100

for i in $(seq 1 $NUM_CLIENTS)
do
   # Run the client script in the background using '&'.
   python3 client.py &
done

echo "$NUM_CLIENTS client processes started."
```

---

## 🚀 วิธีรันโปรแกรม

คุณจะต้องใช้หน้าต่าง Terminal อย่างน้อย 2 หน้าต่าง

### 1. รันเซิร์ฟเวอร์
ใน Terminal แรก ให้เข้าไปยังไดเรกทอรีของโปรเจกต์และรันคำสั่ง:
```bash
python3 server.py
```
เซิร์ฟเวอร์จะเริ่มทำงานและแสดงข้อความว่ากำลังรอรับการเชื่อมต่อ ให้เปิดหน้าต่างนี้ทิ้งไว้

### 2. เตรียมสคริปต์ทดสอบ
ใน Terminal ที่สอง ทำให้สคริปต์สามารถรันได้ (ทำเพียงครั้งเดียว):
```bash
chmod +x run_clients.sh
```

### 3. รันไคลเอนต์
ใน Terminal เดิม ให้รันสคริปต์เพื่อจำลองการเชื่อมต่อของ Client จำนวนมาก:
```bash
./run_clients.sh
```
คุณจะเห็นข้อความการเชื่อมต่อใหม่ๆ แสดงขึ้นมาจำนวนมากในหน้าต่างของเซิร์ฟเวอร์

### 4. การหยุดโปรแกรม
* **วิธีหยุดเซิร์ฟเวอร์:** ไปที่หน้าต่าง Terminal ของเซิร์ฟเวอร์ แล้วกด `Ctrl + C`
* **วิธีหยุดไคลเอนต์ทั้งหมด:** ใน Terminal ใดก็ได้ ให้รันคำสั่ง `pkill -f client.py`
