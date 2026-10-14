from keras.layers import Conv1D, Dense, MaxPooling1D,LSTM, BatchNormalization,Flatten,Dropout,GlobalMaxPooling1D
import pandas as pd
import numpy as np
from keras.callbacks import EarlyStopping,ModelCheckpoint
from keras.utils import np_utils
from keras.models import Sequential
from keras.optimizers import  Nadam, Adam
from sklearn.model_selection import StratifiedKFold
from keras.models import load_model
from sklearn.metrics import confusion_matrix
import os
from sklearn import preprocessing
import time

inicio = time.time()

filepath = "melhor.h5"
x = pd.read_csv('baseMLJurandir4.csv')
x = x.drop_duplicates()
#x = x[x['tipoBloqueio']==0]
#x = x.drop(['utilizacaoRede'],axis=1)
#x = x.drop(['slotsTotalEnlace'],axis=1)
#x = x.drop(['circuitoSNR'],axis=1)
#x = x.drop(['tipoBloqueio'],axis=1)
y = x['Core'].values
x = x.drop(['Core'],axis=1)

#min_max_scaler = preprocessing.MinMaxScaler()
#x_scaled = min_max_scaler.fit_transform(x)
#X_train=pd.DataFrame(x_scaled, columns=x.columns)

X_train = x.values
y = y -1

y_train = np_utils.to_categorical(y,num_classes=6)


modelo = Sequential()
modelo.add(Dense(128,activation='relu'))
modelo.add(Dense(64,activation='relu'))
modelo.add(Dense(32,activation='relu'))
modelo.add(Dense(units = 6, activation = 'softmax'))
  
modelo.compile(optimizer='adam', loss='categorical_crossentropy',metrics = ['accuracy'])
parada = EarlyStopping(monitor='loss',
              min_delta=0.0002,
              patience=10,
              verbose=1, mode='auto',restore_best_weights=True)
checkpoint = ModelCheckpoint(filepath, monitor='loss', verbose=1, save_best_only=True, mode='auto')

historico = modelo.fit(X_train,y_train,epochs=1000, batch_size=64,callbacks=[parada,checkpoint])


modelo = load_model('melhor.h5')
  
adam  = Adam(lr=0.0001, beta_1=0.9, beta_2=0.999, decay=0.0, amsgrad=False)
parada = EarlyStopping(monitor='loss',
              min_delta=0.0004,
              patience=20,
              verbose=1, mode='auto',restore_best_weights=True)
checkpoint = ModelCheckpoint(filepath, monitor='loss', verbose=1, save_best_only=True, mode='auto')
modelo.compile(optimizer=adam, loss='categorical_crossentropy',metrics = ['accuracy'])

historico = modelo.fit(X_train,y_train,epochs=1000, batch_size=64,callbacks=[parada,checkpoint])
modelo.save('treinoCoreMLPbaseMLJurandir4.h5')

fim = time.time()

print('Tempo de treinamento: ')
print(fim - inicio)
