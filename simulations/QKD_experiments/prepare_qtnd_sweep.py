#!/usr/bin/env python3
"""Generate QTND sweep scenarios from the QKD base simulations.

The script keeps the original base simulations unchanged and writes generated
copies under simulations/QKD_experiments/generated_qtnd.
"""
from __future__ import print_function
import argparse, json, shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
SIMULATIONS = HERE.parent


def load_cfg():
    return json.loads((HERE / 'campaign_config.json').read_text(encoding='utf-8'))


def clean_old_results(folder):
    suffixes = (
        '_BlockingProbability.csv', '_BitRateBlockingProbability.csv',
        '_SpectrumUtilization.csv', '_ModulationUtilization.csv',
        '_PhysicalLayerStatistics.csv', '_QKDStatistics.csv'
    )
    for p in folder.iterdir():
        if p.is_file() and p.name.endswith(suffixes):
            p.unlink()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--replications', type=int, default=None,
                    help='Override simulation.replications in generated folders')
    ap.add_argument('--requests', type=int, default=None,
                    help='Override simulation.requests in generated folders')
    ap.add_argument('--threads', type=int, default=None,
                    help='Override simulation.threads in generated folders')
    ap.add_argument('--load-points', type=int, default=None,
                    help='Override simulation.loadPoints in generated folders')
    args = ap.parse_args()

    cfg = load_cfg()
    generated = HERE / cfg['generatedDirectory']
    if generated.exists():
        shutil.rmtree(str(generated))
    generated.mkdir(parents=True)

    count = 0
    for base_name in cfg['qkdSweepScenarios']:
        base = SIMULATIONS / base_name
        if not (base / 'network').is_file() or not (base / 'simulation').is_file():
            raise SystemExit('Missing base simulation: %s' % base)
        sim_base = json.loads((base / 'simulation').read_text(encoding='utf-8'))
        if sim_base.get('coreAndSpectrumAssignment', '').lower() != 'qabne':
            raise SystemExit('%s must use coreAndSpectrumAssignment=qabne' % base_name)
        for qtnd in cfg['qtndSweepKm']:
            dst = generated / ('%s_QTND_%02dkm' % (base_name, int(qtnd)))
            shutil.copytree(str(base), str(dst))
            clean_old_results(dst)

            npath = dst / 'network'
            network = json.loads(npath.read_text(encoding='utf-8'))
            qkd = network.setdefault('qkd', {})
            qkd['enabled'] = True
            qkd['qtndKm'] = float(qtnd)
            npath.write_text(json.dumps(network, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

            spath = dst / 'simulation'
            sim = json.loads(spath.read_text(encoding='utf-8'))
            sim['coreAndSpectrumAssignment'] = 'qabne'
            if args.replications is not None:
                sim['replications'] = args.replications
            if args.requests is not None:
                sim['requests'] = args.requests
            if args.threads is not None:
                sim['threads'] = args.threads
            if args.load_points is not None:
                sim['loadPoints'] = args.load_points
            spath.write_text(json.dumps(sim, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
            count += 1

    print('Generated %d QTND simulation folders in %s' % (count, generated))


if __name__ == '__main__':
    main()
