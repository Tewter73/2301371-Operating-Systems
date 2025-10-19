# โปรเจกต์การประมวลผลและเปรียบเทียบภาพ Grayscale

โปรเจกต์นี้ประกอบด้วยชุดสคริปต์ Python สำหรับการสร้าง, ประมวลผล, และเปรียบเทียบภาพ Grayscale โดยเน้นที่เทคนิค Error Diffusion Dithering และการวัดประสิทธิภาพ

## 📜 สารบัญ

1.  **`generate_grayscale.py`** - สคริปต์สำหรับสร้างไฟล์ภาพ Grayscale
2.  **`error_diffusion.py`** - สคริปต์สำหรับแปลงภาพ Grayscale เป็นภาพขาว-ดำด้วยเทคนิค Error Diffusion
3.  **`time.py`** - สคริปต์ `error_diffusion` ที่เพิ่มการจับเวลาเพื่อวัดประสิทธิภาพ
4.  **`bw_similarity.py`** - สคริปต์สำหรับเปรียบเทียบความเหมือนของภาพขาว-ดำสองภาพ

---

## 1. `generate_grayscale.py` - เครื่องมือสร้างภาพทดสอบ

สคริปต์นี้ใช้สำหรับสร้างไฟล์ภาพ Grayscale ขนาดใหญ่ตามที่กำหนด เหมาะสำหรับใช้เป็นข้อมูลนำเข้า (input) เพื่อทดสอบสคริปต์อื่นๆ ในโปรเจกต์นี้ สามารถสร้างภาพได้ 3 รูปแบบคือ `gradient` (ไล่ระดับสี), `noise` (จุดรบกวน), และ `checkerboard` (ลายตารางหมากรุก)

### การทำงานหลัก

* รับค่าความกว้าง, ความสูง, ชื่อไฟล์ผลลัพธ์, และรูปแบบ (pattern) จาก Command Line
* ใช้ไลบรารี **NumPy** เพื่อสร้างอาร์เรย์ (array) ของพิกเซลอย่างรวดเร็วตามรูปแบบที่เลือก ซึ่งมีประสิทธิภาพสูงและใช้หน่วยความจำน้อยกว่าการสร้างทีละพิกเซล
* แปลง NumPy array เป็นออบเจ็กต์รูปภาพด้วยไลบรารี **Pillow (PIL)**
* บันทึกไฟล์ภาพเป็น PNG และแสดงเวลาที่ใช้ในการประมวลผล

### วิธีใช้งาน

```bash
# รูปแบบคำสั่ง
python generate_grayscale.py <width> <height> <output_filename.png> [pattern]

# ตัวอย่าง: สร้างภาพ 4K แบบไล่ระดับสี (gradient)
python generate_grayscale.py 4096 2160 image_4k.png

# ตัวอย่าง: สร้างภาพ 8K แบบ noise
python generate_grayscale.py 7680 4320 image_8k.png noise
```

### โค้ด

```python
import numpy as np
from PIL import Image
import sys
import time

def generate_grayscale_image(width, height, output_path, pattern='gradient', block_size=128):
    """
    สร้างและบันทึกไฟล์ภาพ grayscale ขนาดใหญ่ตามรูปแบบที่กำหนด

    Parameters:
    - width (int): ความกว้างของภาพ (พิกเซล)
    - height (int): ความสูงของภาพ (พิกเซล)
    - output_path (str): ตำแหน่งที่จะบันทึกไฟล์ภาพ (เช่น 'image.png')
    - pattern (str): รูปแบบของภาพ ('gradient', 'noise', 'checkerboard')
    - block_size (int): ขนาดของช่องในลายตารางหมากรุก
    """
    print(f"กำลังสร้างภาพขนาด {width}x{height} ด้วยรูปแบบ '{pattern}'...")
    start_time = time.time()

    try:
        # สร้าง array ของพิกเซลด้วย NumPy ซึ่งเร็วมาก
        if pattern == 'gradient':
            # สร้างการไล่ระดับสีแนวนอนจาก 0 ถึง 255
            gradient = np.linspace(0, 255, width, dtype=np.uint8)
            # ขยายการไล่ระดับสีให้เต็มความสูงของภาพ
            image_array = np.tile(gradient, (height, 1))

        elif pattern == 'noise':
            # สร้าง array 2 มิติด้วยค่าสุ่มระหว่าง 0 ถึง 255
            image_array = np.random.randint(0, 256, (height, width), dtype=np.uint8)

        elif pattern == 'checkerboard':
            # สร้างลายตารางหมากรุก
            # สร้าง grid ของ index
            x = np.arange(width)
            y = np.arange(height)
            xx, yy = np.meshgrid(x, y)
            # คำนวณค่าสีของแต่ละช่อง
            image_array = ((xx // block_size) + (yy // block_size)) % 2 * 255
            image_array = image_array.astype(np.uint8)
            
        else:
            print(f"Error: ไม่รู้จักรูปแบบ '{pattern}'. กรุณาเลือก 'gradient', 'noise', หรือ 'checkerboard'.")
            return

        # แปลง NumPy array เป็นออบเจ็กต์รูปภาพของ Pillow
        # 'L' คือโหมดสำหรับภาพ 8-bit grayscale
        img = Image.fromarray(image_array, 'L')

        # บันทึกเป็นไฟล์ PNG
        img.save(output_path)
        
        end_time = time.time()
        print(f"สร้างภาพสำเร็จ! บันทึกที่: {output_path}")
        print(f"ใช้เวลา: {end_time - start_time:.2f} วินาที")

    except MemoryError:
        print(f"Error: หน่วยความจำไม่เพียงพอที่จะสร้างภาพขนาด {width}x{height}.")
        print("กรุณาลองลดขนาดของภาพลง")
    except Exception as e:
        print(f"เกิดข้อผิดพลาดที่ไม่คาดคิด: {e}")


if __name__ == "__main__":
    # --- ตัวอย่างการใช้งาน ---
    # รูปแบบการรัน: python generate_grayscale.py <width> <height> <output_filename> [pattern]
    
    if len(sys.argv) < 4:
        print("วิธีใช้: python generate_grayscale.py <width> <height> <output_filename.png> [pattern]")
        print("   - [pattern] (ตัวเลือกเสริม): 'gradient' (ค่าเริ่มต้น), 'noise', 'checkerboard'")
        print("\nตัวอย่าง:")
        print("   python generate_grayscale.py 4096 2160 image_4k.png")
        print("   python generate_grayscale.py 7680 4320 image_8k.png noise")
        sys.exit(1)

    try:
        img_width = int(sys.argv[1])
        img_height = int(sys.argv[2])
        output_file = sys.argv[3]
        
        # รับค่า pattern (ถ้ามี)
        img_pattern = 'gradient' # ค่าเริ่มต้น
        if len(sys.argv) > 4:
            img_pattern = sys.argv[4]

        generate_grayscale_image(img_width, img_height, output_file, pattern=img_pattern)

    except ValueError:
        print("Error: กรุณาระบุความกว้างและความสูงเป็นตัวเลข")
    except IndexError:
         print("Error: กรุณาระบุอาร์กิวเมนต์ให้ครบถ้วน")
```
---
## 2. `error_diffusion.py` - การแปลงภาพด้วย Error Diffusion

สคริปต์นี้เป็นหัวใจของโปรเจกต์ ใช้สำหรับแปลงภาพ Grayscale (8-bit) ให้เป็นภาพขาว-ดำ (1-bit) โดยใช้เทคนิค **Floyd–Steinberg Dithering** ซึ่งเป็นอัลกอริทึมประเภท Error Diffusion ที่ช่วยรักษาระดับความสว่างโดยรวมของภาพไว้ ทำให้ภาพที่ได้ดูมีมิติมากกว่าการปัดค่าสีทิ้งไปเฉยๆ

### การทำงานหลัก

1.  **โหลดภาพ**: เปิดไฟล์ภาพและแปลงเป็น Grayscale
2.  **วนลูปทุกพิกเซล**: ไล่ประมวลผลพิกเซลจากซ้ายไปขวา, บนลงล่าง
3.  **Quantization**: ตัดสินใจว่าพิกเซลปัจจุบันควรเป็นสีดำ (0) หรือสีขาว (255)
4.  **คำนวณ Error**: หาค่าความผิดพลาด (Quantization Error) ซึ่งคือผลต่างระหว่างค่าสีเดิมกับค่าสีใหม่
5.  **กระจาย Error**: นำค่าความผิดพลาดไปกระจายให้กับพิกเซลข้างเคียงที่ยังไม่ถูกประมวลผลตามอัตราส่วนที่กำหนด (7/16, 3/16, 5/16, 1/16)
6.  **บันทึกผลลัพธ์**: บันทึกภาพขาว-ดำที่ได้ลงไฟล์ใหม่

### วิธีใช้งาน

```bash
# รูปแบบคำสั่ง
python error_diffusion.py <input_image_path> <output_image_path>

# ตัวอย่าง
python error_diffusion.py image_4k.png dithered_image.png
```

### โค้ด

```python
import numpy as np
import math
import sys
from PIL import Image
from matplotlib import pyplot as plt

def seq_error_diffusion(image_path, output_path, threshold=128):
    """
    Applies sequential error diffusion dithering to an image and saves the result.

    Parameters:
    - image_path: str, path to the input image.
    - output_path: str, path to save the dithered image.
    - threshold: int, threshold value for dithering (default is 128).
    """
    # Load image and convert to grayscale
    img = Image.open(image_path).convert('L')
    img_array = np.array(img, dtype=np.int32)

    # Initialize output array
    dithered_img = np.zeros_like(img_array)

    # Get image dimensions
    height, width = img_array.shape

    # Sequential error diffusion
    for y in range(height):
        for x in range(width):
            old_pixel = img_array[y, x]
            new_pixel = 255 if old_pixel > threshold else 0
            dithered_img[y, x] = new_pixel
            quant_error = int(old_pixel - new_pixel)

            # Distribute the quantization error to neighboring pixels
            if x + 1 < width:
                img_array[y, x + 1] += int((quant_error * 7) // 16)
            if x - 1 >= 0 and y + 1 < height:
                img_array[y + 1, x - 1] += int((quant_error * 3) // 16)
            if y + 1 < height:
                img_array[y + 1, x] += int((quant_error * 5) // 16)
            if x + 1 < width and y + 1 < height:
                img_array[y + 1, x + 1] += int((quant_error * 1) // 16)

    # Save the dithered image
    dithered_image = Image.fromarray(np.clip(dithered_img, 0, 255).astype(np.uint8))
    dithered_image.save(output_path)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python error_diffusion.py <input_image_path> <output_image_path>")
        sys.exit(1)
    input_image_path = sys.argv[1]
    output_image_path = sys.argv[2]
    seq_error_diffusion(input_image_path, output_image_path)
```
---
## 3. `time.py` - การวัดประสิทธิภาพ Error Diffusion

สคริปต์นี้มีฟังก์ชันการทำงานเหมือนกับ `error_diffusion.py` ทุกประการ แต่ได้เพิ่มโค้ดสำหรับ **จับเวลา** เข้าไป เพื่อวัดประสิทธิภาพการทำงานของส่วนที่เป็นลูป Error Diffusion โดยเฉพาะ และวัดเวลารวมทั้งหมดของโปรแกรมด้วย

### การทำงานหลัก

* ใช้ `time.perf_counter()` เพื่อจับเวลาอย่างแม่นยำก่อนและหลังการทำงานของลูป `for` ที่เป็นหัวใจของอัลกอริทึม
* คำนวณและแสดงผลเวลาที่ใช้ในลูป (Diffusion loop time) และเวลารวมทั้งหมดของโปรแกรม (Total program time)

### วิธีใช้งาน

```bash
# รูปแบบคำสั่ง
python time.py <input_image_path> <output_image_path> [threshold]

# ตัวอย่าง
python time.py image_4k.png timed_dithered_image.png
```

### โค้ด

```python
import numpy as np
import math
import sys
import time
from PIL import Image
from matplotlib import pyplot as plt

def seq_error_diffusion(image_path, output_path, threshold=128):
    """
    Applies sequential error diffusion dithering to an image and saves the result.

    Parameters:
    - image_path: str, path to the input image.
    - output_path: str, path to save the dithered image.
    - threshold: int, threshold value for dithering (default is 128).
    """
    # Load image and convert to grayscale
    img = Image.open(image_path).convert('L')
    img_array = np.array(img, dtype=np.int32)

    # Initialize output array
    dithered_img = np.zeros_like(img_array)

    # Get image dimensions
    height, width = img_array.shape

    # ----- start timing: JUST BEFORE the error-diffusion loop -----
    t0_diff = time.perf_counter()

    # Sequential error diffusion (Floyd–Steinberg)
    for y in range(height):
        for x in range(width):
            old_pixel = img_array[y, x]
            new_pixel = 255 if old_pixel > threshold else 0
            dithered_img[y, x] = new_pixel
            quant_error = int(old_pixel - new_pixel)

            # Distribute the quantization error to neighboring pixels
            if x + 1 < width:
                img_array[y, x + 1] += int((quant_error * 7) // 16)
            if x - 1 >= 0 and y + 1 < height:
                img_array[y + 1, x - 1] += int((quant_error * 3) // 16)
            if y + 1 < height:
                img_array[y + 1, x] += int((quant_error * 5) // 16)
            if x + 1 < width and y + 1 < height:
                img_array[y + 1, x + 1] += int((quant_error * 1) // 16)

    # ----- end timing: RIGHT AFTER the loop -----
    t1_diff = time.perf_counter()
    diffusion_time = t1_diff - t0_diff

    # Save the dithered image
    dithered_image = Image.fromarray(np.clip(dithered_img, 0, 255).astype(np.uint8))
    dithered_image.save(output_path)

    return diffusion_time

if __name__ == "__main__":
    # Optional: total runtime as well
    t0_total = time.perf_counter()

    if len(sys.argv) not in (3, 4):
        print("Usage: python error_diffusion.py <input_image_path> <output_image_path> [threshold]")
        sys.exit(1)

    input_image_path = sys.argv[1]
    output_image_path = sys.argv[2]
    threshold = int(sys.argv[3]) if len(sys.argv) == 4 else 128

    diff_time = seq_error_diffusion(input_image_path, output_image_path, threshold=threshold)

    t1_total = time.perf_counter()
    total_time = t1_total - t0_total

    print(f"Diffusion loop time: {diff_time:.6f} seconds")
    print(f"Total program time: {total_time:.6f} seconds")
```
---
## 4. `bw_similarity.py` - เครื่องมือเปรียบเทียบภาพ

สคริปต์นี้ใช้สำหรับเปรียบเทียบภาพสองภาพ โดยจะแปลงภาพทั้งสองให้เป็นภาพขาว-ดำ (1-bit) ก่อน แล้วจึงคำนวณหา **เปอร์เซ็นต์ความเหมือน (Similarity)** โดยการเปรียบเทียบพิกเซลต่อพิกเซล นอกจากนี้ยังสร้างภาพผลลัพธ์ที่แสดง **จุดที่แตกต่างกันเป็นสีแดง** เพื่อให้เห็นภาพชัดเจนขึ้น

### การทำงานหลัก

1.  **โหลดภาพ**: เปิดไฟล์ภาพ 2 ภาพและแปลงเป็น Grayscale
2.  **แปลงเป็น 1-bit**: แปลงภาพทั้งสองเป็นภาพขาว-ดำ โดยใช้เกณฑ์ (threshold) ที่ 128
3.  **คำนวณความเหมือน**: เปรียบเทียบอาร์เรย์ของภาพทั้งสอง เพื่อนับจำนวนพิกเซลที่เหมือนกัน แล้วคำนวณเป็นเปอร์เซ็นต์
4.  **สร้างภาพความแตกต่าง**: สร้างภาพใหม่ขึ้นมาโดยใช้ภาพแรกเป็นพื้นฐาน และระบายสีแดง (`[255, 0, 0]`) ลงบนพิกเซลที่มีค่าไม่ตรงกับภาพที่สอง
5.  **แสดงผล**: ใช้ **Matplotlib** เพื่อแสดงภาพต้นฉบับทั้งสอง (แบบ 1-bit) และภาพที่แสดงจุดแตกต่าง พร้อมทั้งแสดงค่า Similarity

### วิธีใช้งาน

```bash
# รูปแบบคำสั่ง
python bw_similarity.py <input_image1_path> <input_image2_path>

# ตัวอย่าง: เปรียบเทียบภาพต้นฉบับกับภาพที่ผ่านการ dither
python bw_similarity.py image_4k.png dithered_image.png
```
### โค้ด
```python
from PIL import Image
import sys
import numpy as np
import matplotlib.pyplot as plt

def convert_to_1bit(image):
    """
    Converts a grayscale image to 1-bit (black and white).
    Parameters:
    - image: numpy array, input grayscale image.
    Returns:
    - onebit_img: numpy array, 1-bit image.
    """
    # Initialize output array
    onebit_img = np.zeros_like(image)

    # Get image dimensions
    height, width = image.shape

    for i in range(height):
        for j in range(width):
            old_pixel = image[i, j]
            new_pixel = 1 if old_pixel > 128 else 0
            onebit_img[i, j] = new_pixel

    return onebit_img

def error_diffusion_similarity(img1_path, img2_path):
    """
    Compares two images using error diffusion similarity and highlights differences.
    Parameters:
    - img1_path: str, path to the first image.
    - img2_path: str, path to the second image.
    """

    # Load image and convert to grayscale
    img1 = Image.open(img1_path).convert('L')
    img2 = Image.open(img2_path).convert('L')
    img1_array = np.array(img1)
    img2_array = np.array(img2)

    # Convert images to 1-bit
    img1_1bit = convert_to_1bit(img1_array)
    img2_1bit = convert_to_1bit(img2_array)

    # Calculate similarity
    similarity = np.sum(img1_1bit == img2_1bit) / img1_1bit.size
    print(f"Similarity: {similarity * 100:.2f}%")

    # Mark the differences in red
    diff_mask = img1_1bit != img2_1bit
    # Create an RGB image from img1_1bit (scale to 0-255)
    base_rgb = np.stack([img1_1bit * 255]*3, axis=-1)
    # Set differing pixels to red ([255, 0, 0])
    base_rgb[diff_mask] = [255, 0, 0]
    difference_image = base_rgb

    # Display images
    plt.figure(figsize=(15, 5))
    plt.subplot(1, 3, 1)
    plt.title('Image 1 (1-bit)')
    plt.imshow(img1_1bit, cmap='gray')
    plt.axis('off')
    plt.subplot(1, 3, 2)
    plt.title('Image 2 (1-bit)')
    plt.imshow(img2_1bit, cmap='gray')
    plt.axis('off')
    plt.subplot(1, 3, 3)
    plt.title('Differences (Red)')
    plt.imshow(difference_image)
    plt.axis('off')
    plt.figtext(0.5, 0.01, f"Similarity: {similarity * 100:.2f}%", ha="center", fontsize=14, color="blue")
    plt.show()
    return similarity

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python bw_similarity.py <input_image1_path> <input_image1_path>")
        sys.exit(1)
    input_image1_path = sys.argv[1]
    input_image2_path = sys.argv[2]
    error_diffusion_similarity(input_image1_path, input_image2_path) 
```
