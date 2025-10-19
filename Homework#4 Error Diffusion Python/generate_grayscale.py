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