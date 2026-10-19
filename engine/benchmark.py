"""Opt-in local SD benchmark. No model download, server or telemetry upload."""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
import platform
import re
import statistics
import time
from pathlib import Path
from urllib.parse import urlsplit


def fingerprint(directory: Path) -> tuple[str, list[dict]]:
    directory = directory.resolve()
    if not (directory/'model_index.json').is_file():
        raise ValueError('Choose a complete local Diffusers SD 1.x folder')
    files = []
    for path in directory.rglob('*'):
        if not path.is_file() or any(part.startswith('.') for part in path.relative_to(directory).parts): continue
        if path.suffix.lower() not in ('.safetensors','.json','.txt','.model','.bpe','.tiktoken') and path.name not in ('LICENSE','LICENSE.md','NOTICE'): continue
        files.append(path)
        if len(files)>10_000: raise ValueError('Model inventory exceeds its file limit')
    files.sort(key=lambda path:path.relative_to(directory).as_posix())
    if not files or len(files) > 10_000 or not any(p.suffix == '.safetensors' for p in files):
        raise ValueError('A bounded local safetensors model bundle is required')
    inventory = []
    for path in files:
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for block in iter(lambda: source.read(1_048_576), b''):
                digest.update(block)
        inventory.append({'path':path.relative_to(directory).as_posix(),'bytes':path.stat().st_size,'sha256':digest.hexdigest()})
    encoded = json.dumps(inventory,sort_keys=True,separators=(',',':')).encode()
    return hashlib.sha256(encoded).hexdigest(),inventory


def summary(attempts: list[dict]) -> dict:
    passed = sorted(row['elapsed_ms'] for row in attempts if row['status'] == 'passed')
    return {'passed':len(passed),'failed':sum(row['status'] == 'failed' for row in attempts),
            'median_ms':statistics.median(passed) if passed else None,
            'p95_nearest_rank_ms':passed[math.ceil(len(passed)*0.95)-1] if passed else None,
            'percentile_note':'Descriptive only; small samples do not establish a population percentile'}


def memory() -> dict:
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        class Counters(ctypes.Structure):
            _fields_ = [('cb',wintypes.DWORD),('faults',wintypes.DWORD)]+[(name,ctypes.c_size_t) for name in ('peak_ws','ws','peak_paged','paged','peak_nonpaged','nonpaged','pagefile','peak_pagefile')]
        value = Counters();value.cb = ctypes.sizeof(value)
        function = ctypes.WinDLL('psapi').GetProcessMemoryInfo
        function.argtypes = [wintypes.HANDLE,ctypes.POINTER(Counters),wintypes.DWORD];function.restype = wintypes.BOOL
        if function(wintypes.HANDLE(-1),ctypes.byref(value),value.cb):
            return {'resident_bytes':value.ws,'process_lifetime_peak_resident_bytes':value.peak_ws}
        return {'resident_bytes':None,'measurement_available':False}
    import resource
    value = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return {'process_lifetime_peak_resident_bytes':value*(1 if platform.system() == 'Darwin' else 1024)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model-directory',type=Path,required=True)
    parser.add_argument('--model-id',required=True)
    parser.add_argument('--source-url',required=True);parser.add_argument('--license-url',required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--repeats',type=int,choices=range(1,4),default=2)
    parser.add_argument('--size',type=int,choices=(256,512),default=256)
    parser.add_argument('--run',action='store_true',help='Actually execute inference; without this only fingerprints/preflight are recorded')
    parser.add_argument("--physical-host",action="store_true",help="Operator attestation: run on physical hardware, not a VM")
    args = parser.parse_args(argv)
    if args.output.resolve().is_relative_to(args.model_directory.resolve()): parser.error('Write evidence outside the model directory')
    for value in (args.source_url,args.license_url):
        if urlsplit(value).scheme != 'https' or not urlsplit(value).netloc: parser.error('Record source/license HTTPS URLs; nothing is fetched')
    digest,inventory = fingerprint(args.model_directory)
    version_file = Path(__file__).resolve().parents[1]/'app/build.gradle.kts'
    version = re.search(r'versionName = "([^"]+)"',version_file.read_text())[1]
    report = {'schema':1,'build_version':version,'physical_device':args.physical_host,'platform':'windows' if os.name == 'nt' else platform.system().lower(),'model_id':args.model_id,'model_bundle_sha256':digest,'model_files':inventory,'source_url':args.source_url,'license_url':args.license_url,'hardware':{'system':platform.system(),'release':platform.release(),'machine':platform.machine(),'python':platform.python_version()},'quality_assessed':False,'status':'preflight_only','attempts':[]}
    def save():
        args.output.parent.mkdir(parents=True,exist_ok=True)
        temporary = args.output.with_suffix(args.output.suffix+'.part');temporary.write_text(json.dumps(report,indent=2),encoding='utf-8');temporary.replace(args.output)
    save()
    if not args.run:
        print('Model fingerprint recorded; no inference executed. Add --run to measure.');return 0
    os.environ['STUDYFORGE_MODEL'] = str(args.model_directory.resolve())
    os.environ['HF_HUB_OFFLINE'] = '1';os.environ['TRANSFORMERS_OFFLINE'] = '1'
    import server
    prompts = [('botany','A scientific botanical illustration of a green fern on white paper'),('product','A realistic red ceramic mug on a neutral table, soft daylight')]
    try:
        import torch
        cuda = torch.cuda.is_available();report['hardware']['accelerator'] = torch.cuda.get_device_name() if cuda else 'cpu'
        for case,prompt in prompts:
            for iteration in range(args.repeats):
                before = memory()
                if cuda: torch.cuda.synchronize();torch.cuda.reset_peak_memory_stats()
                attempt = {'case':case,'iteration':iteration,'pipeline_cache':'empty' if server.PIPE is None else 'reused','size':args.size,'steps':10,'seed':42,'before':before,'status':'running'}
                report['attempts'].append(attempt);save();started = time.perf_counter()
                try:
                    image = server.diffusion({'id':'benchmark','kind':'image','prompt':prompt,'negative':'blurry, distorted','width':args.size,'height':args.size,'steps':10,'seed':42,'control':'none'})
                    try:
                        if image.size != (args.size,args.size): raise RuntimeError('Unexpected output dimensions')
                        destination = args.output.parent/f'{args.output.stem}-{case}-{iteration}.png';image.save(destination)
                        attempt.update(status='passed',image=destination.name,image_sha256=hashlib.sha256(destination.read_bytes()).hexdigest())
                    finally: image.close()
                except BaseException as error:
                    attempt.update(status='cancelled' if isinstance(error,KeyboardInterrupt) else 'failed',error_type=type(error).__name__);raise
                finally:
                    if cuda:
                        try:
                            torch.cuda.synchronize();attempt['peak_cuda_allocated_bytes'] = torch.cuda.max_memory_allocated();attempt['peak_cuda_reserved_bytes'] = torch.cuda.max_memory_reserved()
                        except RuntimeError: attempt['cuda_metrics_unavailable'] = True
                    attempt.update(elapsed_ms=(time.perf_counter()-started)*1000,after=memory());report['summary'] = summary(report['attempts']);save()
        report['status'] = 'completed';save();return 0
    except KeyboardInterrupt:
        report.update(status='cancelled');save();return 130
    except Exception as error:
        report.update(status='failed',error_type=type(error).__name__);save();return 1
    finally:
        server.release_image_pipeline()

if __name__ == '__main__':
    raise SystemExit(main())
