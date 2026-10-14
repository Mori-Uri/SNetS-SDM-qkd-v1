#!/usr/bin/env python3
from pathlib import Path
import argparse
import pandas as pd
import matplotlib.pyplot as plt


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('results_dir')
    ap.add_argument('--reference-root', default=str(Path(__file__).resolve().parents[1]))
    args=ap.parse_args()
    out=Path(args.results_dir)
    ref=Path(args.reference_root)
    stats=pd.read_csv(out/'snets_reference_statistics.csv')
    digitized=pd.read_csv(ref/'reference_python'/'reference_fig7_multifiber.csv')

    for topology in ['small_core','butterfly','metro']:
        s=stats[stats.topology==topology].sort_values('qtnd_km')
        d=digitized[digitized.topology==topology].sort_values('qtnd_km')
        fig, ax=plt.subplots(figsize=(8,5))
        ax.bar(s.qtnd_km, s.mean_qtrx, width=3.4, yerr=s.ci95_margin,
               capsize=3, label='SNetS-QKD (mean ± IC95%)')
        ax.plot(d.qtnd_km, d.qtrx_usage, marker='o', linewidth=1.5,
                label='Artigo-base (Fig. 7 digitalizada)')
        ax.set_xlabel('QTND [km]')
        ax.set_ylabel('Q-TRx pairs')
        ax.set_title(f'{topology}: SNetS-QKD vs. artigo-base')
        ax.set_xticks(s.qtnd_km)
        ax.grid(True, axis='y', alpha=0.25)
        ax.legend()
        fig.tight_layout()
        fig.savefig(out/f'fig7_{topology}_snets_vs_article.png', dpi=200)
        plt.close(fig)

    # One direct Java-vs-Python reconstruction check. They should overlap exactly.
    java_raw=pd.read_csv(out/'snets_reference_raw.csv')
    py_raw=pd.read_csv(ref/'reference_python'/'replications_raw.csv')
    merged=java_raw.merge(py_raw, on=['replication','seed','topology','qtnd_km'], suffixes=('_snets','_python'))
    cmp=(merged.groupby(['topology','qtnd_km'])[['qtrx_usage_snets','qtrx_usage_python']].mean().reset_index())
    cmp.to_csv(out/'snets_vs_python_mean_qtrx.csv', index=False)

    # Quantitative comparison equivalent to the qualification table (MAE/RMSE/optimum).
    joined=stats.merge(digitized,on=['topology','qtnd_km'],how='inner')
    summary=[]
    for topology,g in joined.groupby('topology'):
        err=g.mean_qtrx-g.qtrx_usage
        mae=float(err.abs().mean())
        rmse=float((err.pow(2).mean())**0.5)
        opt_snets=int(g.loc[g.mean_qtrx.idxmin(),'qtnd_km'])
        opt_article=int(g.loc[g.qtrx_usage.idxmin(),'qtnd_km'])
        summary.append({'topology':topology,'mae_qtrx':mae,'rmse_qtrx':rmse,'qtnd_snets_km':opt_snets,'qtnd_article_km':opt_article})
    pd.DataFrame(summary).to_csv(out/'snets_vs_article_summary.csv',index=False)
    print('Generated Fig.7 comparison plots in', out)

if __name__=='__main__':
    main()
