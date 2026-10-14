import tensorflow as tf
from tensorflow.keras.layers import (Conv1D, Dense, MaxPooling1D, LSTM, BatchNormalization, Flatten, Dropout, GlobalMaxPooling1D)
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint
from tensorflow.keras.utils import CustomObjectScope
from tensorflow.keras.models import Sequential, load_model
from tensorflow.keras.optimizers import Nadam, Adam
import numpy as np
import socket

import platform

print(f"Python version: {platform.python_version()}")
print(f"TensorFlow version: {tf.__version__}")
print(f"Keras version (included in TensorFlow): {tf.keras.__version__}")
print(f"NumPy version: {np.__version__}")


class RedeNeural:
    def __init__(self):
        self.rede = tf.keras.models.load_model(
            r'melhormodelo.h5', compile=False
        )

    def executar(self, *args):
        entrada = np.array([args])
        banda = self.rede.predict(entrada)
        return banda.argmax() + 1


host = 'localhost'
port = 7042
addr = (host, port)

serv_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
serv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
serv_socket.bind(addr)
serv_socket.listen(5)

x = RedeNeural()
print('Aguardando conexão...')
while True:
    con, cliente = serv_socket.accept()
    recebe = con.recv(1024)
    lista = [float(i) for i in recebe.decode("utf-8").split('/')]
    print(f"Recebido: {lista}")

    banda_guarda = str(x.executar(*lista))
    print(f"Resultado: {banda_guarda}")

    con.send(bytes(banda_guarda, "utf-8"))
    con.close()
