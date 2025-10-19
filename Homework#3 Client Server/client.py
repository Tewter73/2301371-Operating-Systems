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
