#from tensorflow.keras.models import load_model
from keras.layers import Conv1D, Dense, MaxPooling1D,LSTM, BatchNormalization,Flatten,Dropout,GlobalMaxPooling1D
#import pandas as pd
import numpy as np
from keras.callbacks import EarlyStopping,ModelCheckpoint
from keras.utils import np_utils, CustomObjectScope
from keras.models import Sequential
from keras.optimizers import Nadam, Adam
#from sklearn.model_selection import StratifiedKFold
from keras.models import load_model
#from keras.initializers import glorot_uniform
#from sklearn.metrics import confusion_matrix
#import os
import sys
import socket
#import keras
#import tensorflow


class RedeNeural:
    def __init__(self):
        self.rede = load_model(r'C:\Users\necly\Documents\Redes SDM com ML 2\treinoCoreMLPbaseMLJurandir4.h5')
        #self.rede = tf.keras.models.load_model(r'C:\Users\Neclyeux\Documents\SNetS\simulations\Cost239Deep\redeCostTreinada.h5')
    def executar(self,a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s):
        entrada = np.array([[a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s]])
        #entrada = np.expand_dims(entrada, axis = 2) #pra convolucional
        core = self.rede.predict(entrada)
        return core.argmax()+1

host = 'localhost' 
port = 7766 
addr = (host, port) 
serv_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
serv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) 
serv_socket.bind(addr) 
serv_socket.listen(999999999)

x = RedeNeural()
print('aguardando conexao')
while True:
    con, cliente = serv_socket.accept()
    recebe = con.recv(1024)
    #print("Dados recebidos em bytes: ", recebe)
    #print("Dados decodificados: ", recebe.decode("utf-8"))
    lista = recebe.decode("utf-8").split('/')
    #print("Dados decodificados e separados: ", lista)
    modulacao = str(x.executar(lista[0], lista[1], lista[2], lista[3], lista[4], lista[5], lista[6], lista[7], lista[8], lista[9], lista[10], lista[11], lista[12], lista[13], lista[14], lista[15], lista[16], lista[17], lista[18]))
    #print("Rota escolhida pela rede neural: ", rota)
    message_to_send = modulacao.encode("UTF-8")
    con.send(len(message_to_send).to_bytes(2, byteorder='big'))
    con.send(message_to_send)
    #con.send(rota.encode('utf-8'))
    #serv_socket.send(bytes(banda_guarda,'utf-8'))
    #print(rota)
serv_socket.close() 
