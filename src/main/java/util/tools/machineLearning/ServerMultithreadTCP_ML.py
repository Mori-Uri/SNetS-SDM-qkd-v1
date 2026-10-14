# Python server

import socket
import threading

def apply_strategy(message_received):

    lista = message_received.split('/')
    print(lista)

    message_to_send = "accepted"

    return message_to_send

def handle_client(client_socket, addr):
    try:
        # receive client messages
        request = client_socket.recv(1024).decode("utf-8")
        # print client messages
        print(f"Received: {request}")
        
        # convert and send accept response to the client
        response = apply_strategy(request)
        client_socket.send(response.encode("utf-8"))

    except Exception as e:
        print(f"Error when hanlding client: {e}")
    finally:
        client_socket.close()
        print(f"Connection to client ({addr[0]}:{addr[1]}) closed")


def run_server():
    server_host = "localhost"  # server hostname or IP address
    server_port = 7766  # server port number
    
    try:
		# create a socket object
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        # bind the socket to the host and port
        server.bind((server_host, server_port))
        # listen for incoming connections
        server.listen()
        print(f"Listening on {server_host}:{server_port}")

        while True:
            # accept a client connection
            client_socket, addr = server.accept()
            print(f"Accepted connection from {addr[0]}:{addr[1]}")
            # start a new thread to handle the client
            thread = threading.Thread(target=handle_client, args=(client_socket, addr,))
            thread.start()
    
    except Exception as e:
        print(f"Error: {e}")
    finally:
        server.close()

run_server()
