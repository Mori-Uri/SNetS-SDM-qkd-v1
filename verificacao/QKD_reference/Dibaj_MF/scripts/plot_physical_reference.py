#!/usr/bin/env python3
from pathlib import Path
import argparse
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.colors import Normalize


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('physical_results_dir')
    args=ap.parse_args()
    out=Path(args.physical_results_dir)

    f1=pd.read_csv(out/'snets_fig1b_skr.csv')
    fig,ax=plt.subplots(figsize=(8,5))
    for length,g in f1.groupby('length_km'):
        ax.plot(g.wavelength_nm,g.skr_mbps,marker='.',linewidth=1,label=f'{int(length)} km')
    ax.set_yscale('log')
    ax.set_xlabel('Wavelength [nm]')
    ax.set_ylabel('SKR [Mbit/s]')
    ax.set_title('SNetS-QKD: camada física MF')
    ax.grid(True,which='both',alpha=.3)
    ax.legend(ncol=3,fontsize=8)
    fig.tight_layout()
    fig.savefig(out/'fig1b_snets_mf_skr.png',dpi=200)
    plt.close(fig)

    f5=pd.read_csv(out/'snets_fig5b_skr_per_qtrx.csv')
    wavelengths=sorted(f5.wavelength_nm.unique())
    qtnds=sorted(f5.qtnd_km.unique())
    matrix=f5.pivot(index='wavelength_nm',columns='qtnd_km',values='skr_per_qtrx_mbps').loc[wavelengths,qtnds]
    wm,qm=np.meshgrid(np.array(wavelengths,float),np.array(qtnds,float),indexing='ij')
    xpos=wm.ravel(); ypos=qm.ravel(); dz=matrix.to_numpy().ravel(); zpos=np.zeros_like(dz)
    dx=np.full_like(dz,7.8); dy=np.full_like(dz,3.7)
    norm=Normalize(vmin=0.0,vmax=60.0); cmap=plt.get_cmap('jet')
    fig=plt.figure(figsize=(6,5),dpi=200)
    ax=fig.add_subplot(111,projection='3d')
    ax.bar3d(xpos-dx/2,ypos-dy/2,zpos,dx,dy,dz,color=cmap(norm(dz)),shade=False)
    ax.set_xlabel('Wavelength [nm]')
    ax.set_ylabel('QTND [km]')
    ax.set_zlabel('SKR/Q-TRx [Mbit/s]')
    ax.view_init(elev=25,azim=-55)
    fig.tight_layout()
    fig.savefig(out/'fig5b_snets_skr_per_qtrx.png',dpi=200)
    plt.close(fig)
    print('Generated physical reference plots in',out)

if __name__=='__main__':
    main()
