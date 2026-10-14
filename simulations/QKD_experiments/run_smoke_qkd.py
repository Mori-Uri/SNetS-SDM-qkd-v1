#!/usr/bin/env python3
"""Run a quick integration test without modifying the real simulations."""
from __future__ import print_function
import json, shutil, subprocess, sys
from pathlib import Path
HERE=Path(__file__).resolve().parent; SIM=HERE.parent
jar=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else SIM/'SNetS-SDM-QKD.jar'
if not jar.is_file(): raise SystemExit('JAR not found: %s'%jar)
work=HERE/'smoke_work'
if work.exists(): shutil.rmtree(str(work))
work.mkdir()
for name in ['NSFNet_C1_Classical7','NSFNet_C2_QKD_NoXT','NSFNet_C3_QKD_XT']:
    src=SIM/name; dst=work/name; shutil.copytree(str(src),str(dst))
    p=dst/'simulation'; cfg=json.loads(p.read_text(encoding='utf-8'))
    cfg['requests']=10;cfg['loadPoints']=1;cfg['replications']=1;cfg['threads']=1
    if name!='NSFNet_C1_Classical7': cfg['qkdProtectedRequestProbability']=0.20
    p.write_text(json.dumps(cfg,indent=2)+'\n',encoding='utf-8')
    print('Running smoke:',name)
    subprocess.check_call(['java','-jar',str(jar),str(dst)])
print('Smoke test finished. Temporary outputs:',work)
