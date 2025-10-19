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
