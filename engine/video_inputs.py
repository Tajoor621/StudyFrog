"""Bounded, transient local uploads. No filenames or client filesystem paths accepted."""
import re
import os
import shutil
import threading
import time
import uuid
from pathlib import Path
from video_remix import MAX_UPLOAD, probe

UPLOAD_LOCK=threading.Lock()
ID=re.compile(r'[0-9a-f]{32}')

def path(root, identifier):
    if not isinstance(identifier,str) or not ID.fullmatch(identifier): raise ValueError('Invalid video input identifier')
    return Path(root)/(identifier+'.upload')

def available(root, identifier):
    source=path(root,identifier)
    try:
        stat=source.stat()
        if time.time()-stat.st_mtime>=1800:
            source.unlink(missing_ok=True)
            raise ValueError('Video input expired; upload again')
    except FileNotFoundError as exc:
        raise ValueError('Video input expired or already used; upload again') from exc
    return source,stat.st_size

def cleanup(root):
    now=time.time()
    for item in Path(root).glob('*.upload*'):
        if re.fullmatch(r'[0-9a-f]{32}\.upload(?:\.part)?',item.name) and now-item.stat().st_mtime>1800:
            item.unlink(missing_ok=True)

def receive(root, stream, length):
    if type(length) is not int or not 1<=length<=MAX_UPLOAD: raise ValueError('Video upload must be 1–128 MB')
    if not UPLOAD_LOCK.acquire(blocking=False): raise RuntimeError('Another video is uploading; retry shortly')
    target=path(root,uuid.uuid4().hex);stage=target.with_suffix('.upload.part')
    try:
        cleanup(root)
        if len(list(Path(root).glob('*.upload')))>=4: raise RuntimeError('Too many unused uploads; remove them or wait 30 minutes')
        if shutil.disk_usage(root).free<length+512_000_000: raise RuntimeError('Not enough space for video upload and processing')
        with os.fdopen(os.open(stage,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'wb') as out:
            remaining=length
            while remaining:
                chunk=stream.read(min(65536,remaining))
                if not chunk: raise ValueError('Incomplete video upload')
                out.write(chunk);remaining-=len(chunk)
        info=probe(stage)
        stage.replace(target)
        return dict(id=target.stem,bytes=length,**info)
    finally:
        stage.unlink(missing_ok=True);UPLOAD_LOCK.release()
