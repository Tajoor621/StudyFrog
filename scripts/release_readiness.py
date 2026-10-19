"""Check version-bound hardware/review evidence. Empty records mean pending, never approval."""
from __future__ import annotations
import argparse
from datetime import datetime
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
HEX = re.compile(r'[0-9a-f]{64}')
PLATFORMS = ('android','ios','windows')
MANUAL_CHECKS = ('screen_reader','font_scaling','contrast','low_memory_recovery','encoder_repeat')


def safe_path(root, name):
    if not isinstance(name,str) or len(name)>512: raise ValueError('Invalid evidence path')
    path = (root/name).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file(): raise ValueError('Evidence must be an existing file inside the checkout')
    return path


def subject_digest(root, paths):
    if not isinstance(paths,list) or not paths or len(paths)!=len(set(paths)): raise ValueError('Subjects need distinct tracked source paths')
    items = {name:hashlib.sha256(safe_path(root,name).read_bytes()).hexdigest() for name in sorted(paths)}
    return hashlib.sha256(json.dumps(items,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def reference(root, item):
    if not isinstance(item,dict) or not HEX.fullmatch(item.get('sha256','')): raise ValueError('Evidence needs its complete SHA-256')
    path = safe_path(root,item.get('path'))
    if path.stat().st_size>16_000_000: raise ValueError('Evidence record exceeds 16 MB')
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest()!=item['sha256']: raise ValueError('Evidence checksum mismatch')
    return data


def human(value):
    return isinstance(value,str) and bool(value.strip()) and value.strip().lower() not in ('codex','assistant','chatgpt','openai')


def https(value):
    return isinstance(value,str) and urlsplit(value).scheme=='https' and bool(urlsplit(value).netloc)


def dated(value):
    try: return isinstance(value,str) and datetime.fromisoformat(value.replace('Z','+00:00')).tzinfo is not None
    except ValueError: return False


def assess(root=ROOT, manifest=None):
    manifest = manifest or root/'validation/release-evidence.json'
    data = json.loads(manifest.read_text(encoding='utf-8'))
    if data.get('schema')!=1: raise ValueError('Unsupported evidence register')
    version = re.search(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text())[1]
    digests = {kind:subject_digest(root,data['subjects'][kind]) for kind in ('science','family')}
    issues = []
    def verified(item, json_record=True):
        try:
            raw = reference(root,item)
            return json.loads(raw) if json_record else raw
        except (OSError,ValueError,TypeError,KeyError) as error:
            issues.append(str(error));return None
    reviews = {}
    for kind in ('science','family'):
        reviews[kind] = False
        for record in data.get(kind+'_reviews',[]):
            if not isinstance(record,dict): issues.append('Invalid review record');continue
            evidence = verified(record.get('evidence',{}),False)
            if (record.get('subject_digest')==digests[kind] and record.get('outcome')=='approved'
                and record.get('independent') is True and human(record.get('reviewer'))
                and isinstance(record.get('qualifications'),str) and len(record['qualifications'].strip())>=10
                and evidence and len(evidence)>=80):
                reviews[kind] = True
    models = {}
    for model in data.get('models',[]):
        key = model.get('sha256','')
        if not HEX.fullmatch(key) or not model.get('name') or not https(model.get('source_url')) or not https(model.get('license_url')):
            raise ValueError('Each measured model needs a name, exact digest and source/license HTTPS URLs')
        if key in models: raise ValueError('Duplicate model provenance digest')
        models[key] = model
    benchmark_models = {platform:set() for platform in PLATFORMS}
    for item in data.get('benchmarks',[]):
        report = verified(item)
        if not isinstance(report,dict): continue
        platform = report.get('platform');digest = report.get('model_sha256',report.get('model_bundle_sha256'))
        attempts = report.get('attempts',[])
        if (platform in PLATFORMS and digest in models and report.get('build_version')==version and report.get('status')=='completed'
            and report.get('physical_device') is True and isinstance(attempts,list) and len(attempts)>=2
            and all(isinstance(row,dict) for row in attempts)
            and all(row.get('status')=='passed' and type(row.get('elapsed_ms')) in (int,float) and 0<row['elapsed_ms']<3_600_000 for row in attempts)
            and {'botany','product'}.issubset({row.get('case') for row in attempts})):
            benchmark_models[platform].add(digest)
    manual = {platform:False for platform in PLATFORMS}
    for item in data.get('device_sessions',[]):
        session = verified(item)
        if not isinstance(session,dict): continue
        platform = session.get('platform');checks = session.get('checks',{})
        attachments = [verified(asset,False) for asset in session.get('attachments',[])]
        if (platform in PLATFORMS and session.get('physical_device') is True and session.get('build_version')==version
            and human(session.get('operator')) and dated(session.get('recorded_at')) and isinstance(session.get('hardware'),str) and bool(session['hardware'].strip()) and attachments and all(attachments)
            and all(checks.get(check)=='passed' for check in MANUAL_CHECKS)):
            manual[platform] = True
    pending = [kind+' independent review' for kind,complete in reviews.items() if not complete]
    for platform in PLATFORMS:
        if len(benchmark_models[platform])<2: pending.append(platform+' representative-model measurements (two model digests required)')
        if not manual[platform]: pending.append(platform+' physical accessibility/memory/encoder session')
    return {'schema':1,'build_version':version,'requested_validation_complete':not pending and not issues,'subject_digests':digests,
            'independent_reviews':reviews,'measured_models':{key:sorted(value) for key,value in benchmark_models.items()},
            'manual_sessions':manual,'pending':pending,'invalid_records':issues,
            'trust_boundary':'Human identity, qualifications, independence and physical execution are operator attestations, not cryptographic certification. This gate does not establish full master-spec completion.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__);parser.add_argument('--require-ready',action='store_true');args = parser.parse_args()
    try: result = assess()
    except (OSError,ValueError,KeyError,TypeError) as error: print(json.dumps({'error':str(error)}));return 1
    print(json.dumps(result,indent=2));return 2 if args.require_ready and not result['requested_validation_complete'] else 0

if __name__=='__main__': raise SystemExit(main())
