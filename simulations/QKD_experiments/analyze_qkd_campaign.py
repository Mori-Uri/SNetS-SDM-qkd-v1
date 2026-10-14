#!/usr/bin/env python3
"""Analyze SNetS C1/C2/C3 and QTND-sweep results.

Outputs CSV summaries with mean, sample standard deviation and 95% Student-t
confidence intervals. If matplotlib is installed, also exports PNG figures.
No pandas/scipy dependency is required.
"""
from __future__ import print_function
import csv, json, math, re, statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
SIMULATIONS = HERE.parent
CFG = json.loads((HERE / 'campaign_config.json').read_text(encoding='utf-8'))
OUT = HERE / CFG['analysisDirectory']
OUT.mkdir(parents=True, exist_ok=True)

# 97.5% two-sided Student-t critical values for df 1..30.
_T975 = {
 1:12.706,2:4.303,3:3.182,4:2.776,5:2.571,6:2.447,7:2.365,8:2.306,9:2.262,
 10:2.228,11:2.201,12:2.179,13:2.160,14:2.145,15:2.131,16:2.120,17:2.110,
 18:2.101,19:2.093,20:2.086,21:2.080,22:2.074,23:2.069,24:2.064,25:2.060,
 26:2.056,27:2.052,28:2.048,29:2.045,30:2.042
}

def summarize(vals):
    vals = [float(x) for x in vals if x is not None]
    if not vals:
        return ('','','',0)
    n=len(vals); mean=statistics.mean(vals)
    if n==1:
        return (mean,0.0,0.0,n)
    sd=statistics.stdev(vals)
    t=_T975.get(n-1,1.96)
    ci=t*sd/math.sqrt(n)
    return (mean,sd,ci,n)

def find_one(folder, suffix):
    files=list(folder.glob('*'+suffix))
    return files[0] if files else None

def read_blocking(folder, suffix):
    path=find_one(folder,suffix)
    out={}
    if not path: return out
    with path.open(newline='',encoding='utf-8-sig') as f:
        rd=csv.reader(f); header=next(rd,None)
        for row in rd:
            if not row or len(row)<7: continue
            try: lp=int(row[1])
            except Exception: continue
            vals=[]
            for v in row[6:]:
                try: vals.append(float(v))
                except Exception: pass
            if vals: out[lp]=vals
    return out

def read_qkd(folder):
    path=find_one(folder,'_QKDStatistics.csv')
    if not path: return []
    with path.open(newline='',encoding='utf-8-sig') as f:
        return list(csv.DictReader(f))

def qtnd_from_name(name):
    m=re.search(r'_QTND_(\d+)km$',name)
    return int(m.group(1)) if m else ''

def scenario_family(name):
    if name.startswith('NSFNet_C1_Classical7'): return 'C1'
    if name.startswith('NSFNet_C2_QKD_NoXT'): return 'C2'
    if name.startswith('NSFNet_C3_QKD_XT'): return 'C3'
    return name

def candidate_folders():
    result=[]
    for name in CFG['baseScenarios']:
        p=SIMULATIONS/name
        if p.is_dir(): result.append(p)
    generated=HERE/CFG['generatedDirectory']
    if generated.is_dir(): result.extend(sorted(p for p in generated.iterdir() if p.is_dir()))
    return result

def main():
    detailed=[]
    for folder in candidate_folders():
        bp=read_blocking(folder,'_BlockingProbability.csv')
        bb=read_blocking(folder,'_BitRateBlockingProbability.csv')
        qrows=read_qkd(folder)
        q_by_lp={}
        for r in qrows:
            try: q_by_lp.setdefault(int(r['LoadPoint']),[]).append(r)
            except Exception: pass
        loadpoints=sorted(set(bp)|set(bb)|set(q_by_lp))
        for lp in loadpoints:
            row={'Scenario':folder.name,'Family':scenario_family(folder.name),'QTND_km':qtnd_from_name(folder.name),'LoadPoint':lp}
            for label,vals in [('BlockingProbability',bp.get(lp,[])),('BitRateBlockingProbability',bb.get(lp,[]))]:
                mean,sd,ci,n=summarize(vals)
                row[label+'_Mean']=mean; row[label+'_SD']=sd; row[label+'_CI95']=ci; row[label+'_N']=n
            qs=q_by_lp.get(lp,[])
            metrics=['TotalRequestAttempts','ProtectedAttempts','Accepted','Rejected','Released',
                     'ClassicalRejectionsByQKDImpact','PrecommitAdmissionChecks','PrecommitAdmissionRejections',
                     'ActivatedQChs','DeactivatedQChs','TDMReuses','CapacityFailures','PeakActiveQChs','PeakQTRxPairs',
                     'AcceptedDemand_Mbps','MeanAdmissionRawSKR_Mbps','MinimumAdmissionRawSKR_Mbps',
                     'MeanAdmissionQBER','MaximumAdmissionQBER','MaximumAdmissionPXT_W']
            for key in metrics:
                vals=[]
                for q in qs:
                    try: vals.append(float(q[key]))
                    except Exception: pass
                mean,sd,ci,n=summarize(vals)
                row[key+'_Mean']=mean; row[key+'_CI95']=ci; row[key+'_N']=n
            acc=[]; frac=[]
            for q in qs:
                try:
                    a=float(q['Accepted']); p=float(q['ProtectedAttempts']); t=float(q['TotalRequestAttempts'])
                    if p>0: acc.append(a/p)
                    if t>0: frac.append(p/t)
                except Exception: pass
            for key,vals in [('QKDAcceptanceRatio',acc),('ObservedProtectedFraction',frac)]:
                mean,sd,ci,n=summarize(vals)
                row[key+'_Mean']=mean; row[key+'_CI95']=ci; row[key+'_N']=n
            detailed.append(row)

    if not detailed:
        print('No result CSVs found. Run simulations first.'); return
    fields=[]
    for r in detailed:
        for k in r:
            if k not in fields: fields.append(k)
    summary=OUT/'qkd_campaign_summary_by_load.csv'
    with summary.open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(detailed)

    # QTND summary: aggregate each per-load mean across load points only for quick exploratory view.
    sweep=[r for r in detailed if r['QTND_km']!='']
    sweep_fields=['Scenario','Family','QTND_km','LoadPoint','BlockingProbability_Mean','BlockingProbability_CI95',
                  'QKDAcceptanceRatio_Mean','QKDAcceptanceRatio_CI95','PeakQTRxPairs_Mean','PeakQTRxPairs_CI95',
                  'MeanAdmissionRawSKR_Mbps_Mean','MeanAdmissionQBER_Mean','MaximumAdmissionPXT_W_Mean']
    sweep_file=OUT/'qkd_qtnd_summary.csv'
    with sweep_file.open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=sweep_fields);w.writeheader()
        for r in sweep: w.writerow({k:r.get(k,'') for k in sweep_fields})

    print('Wrote',summary)
    print('Wrote',sweep_file)
    try:
        make_plots(detailed)
    except ImportError:
        print('matplotlib not installed: CSV analysis completed, plots skipped.')


def make_plots(rows):
    import matplotlib.pyplot as plt
    # Base scenarios: blocking probability versus SNetS load point.
    base=[r for r in rows if r['QTND_km']=='']
    fig,ax=plt.subplots(figsize=(8,5))
    for fam in ('C1','C2','C3'):
        rr=sorted([r for r in base if r['Family']==fam],key=lambda x:x['LoadPoint'])
        if rr:
            ax.errorbar([r['LoadPoint'] for r in rr],[r['BlockingProbability_Mean'] for r in rr],
                        yerr=[r['BlockingProbability_CI95'] for r in rr],marker='o',capsize=3,label=fam)
    ax.set_xlabel('Load point');ax.set_ylabel('Blocking probability');ax.set_title('C1 x C2 x C3 - blocking probability')
    ax.grid(True,alpha=.25);ax.legend();fig.tight_layout();fig.savefig(OUT/'base_blocking_probability.png',dpi=200);plt.close(fig)

    fig,ax=plt.subplots(figsize=(8,5))
    for fam in ('C2','C3'):
        rr=sorted([r for r in base if r['Family']==fam and r.get('QKDAcceptanceRatio_Mean','')!=''],key=lambda x:x['LoadPoint'])
        if rr:
            ax.errorbar([r['LoadPoint'] for r in rr],[r['QKDAcceptanceRatio_Mean'] for r in rr],
                        yerr=[r['QKDAcceptanceRatio_CI95'] for r in rr],marker='o',capsize=3,label=fam)
    ax.set_xlabel('Load point');ax.set_ylabel('QKD acceptance ratio');ax.set_ylim(0,1.05);ax.set_title('QKD acceptance - C2 x C3')
    ax.grid(True,alpha=.25);ax.legend();fig.tight_layout();fig.savefig(OUT/'base_qkd_acceptance.png',dpi=200);plt.close(fig)

    fig,ax=plt.subplots(figsize=(8,5))
    for fam in ('C2','C3'):
        rr=sorted([r for r in base if r['Family']==fam and r.get('PeakQTRxPairs_Mean','')!=''],key=lambda x:x['LoadPoint'])
        if rr:
            ax.errorbar([r['LoadPoint'] for r in rr],[r['PeakQTRxPairs_Mean'] for r in rr],
                        yerr=[r['PeakQTRxPairs_CI95'] for r in rr],marker='o',capsize=3,label=fam)
    ax.set_xlabel('Load point');ax.set_ylabel('Peak Q-TRx pairs');ax.set_title('Peak Q-TRx - C2 x C3')
    ax.grid(True,alpha=.25);ax.legend();fig.tight_layout();fig.savefig(OUT/'base_peak_qtrx.png',dpi=200);plt.close(fig)

    # QTND plots: one curve per load point, separately for C2/C3.
    for fam in ('C2','C3'):
        rr=[r for r in rows if r['Family']==fam and r['QTND_km']!='']
        if not rr: continue
        for metric,ylabel,filename in [
            ('PeakQTRxPairs_Mean','Peak Q-TRx pairs','qtnd_peak_qtrx'),
            ('QKDAcceptanceRatio_Mean','QKD acceptance ratio','qtnd_qkd_acceptance'),
            ('BlockingProbability_Mean','Blocking probability','qtnd_blocking_probability')]:
            fig,ax=plt.subplots(figsize=(8,5))
            for lp in sorted(set(r['LoadPoint'] for r in rr)):
                g=sorted([r for r in rr if r['LoadPoint']==lp and r.get(metric,'')!=''],key=lambda x:x['QTND_km'])
                if g: ax.plot([r['QTND_km'] for r in g],[r[metric] for r in g],marker='o',label='LoadPoint %s'%lp)
            ax.set_xlabel('QTND [km]');ax.set_ylabel(ylabel);ax.set_title('%s - %s'%(fam,ylabel))
            ax.grid(True,alpha=.25);ax.legend();fig.tight_layout();fig.savefig(OUT/('%s_%s.png'%(filename,fam.lower())),dpi=200);plt.close(fig)
    print('Generated PNG plots in',OUT)

if __name__=='__main__':
    main()
