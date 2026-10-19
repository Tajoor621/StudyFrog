"""Transient, local Tesseract OCR with bounded input, runtime and concurrency."""
import csv
import io
import math
import re
import shutil
import subprocess
import tempfile
import threading
from pathlib import Path
from PIL import ImageOps

GATE=threading.BoundedSemaphore(1)
class BusyError(RuntimeError): pass

def parse_tsv(tsv,width,height):
    lines={};words=0
    for row in csv.DictReader(io.StringIO(tsv),delimiter="\t",quoting=csv.QUOTE_NONE):
        if row.get("level")!="5": continue
        try:
            confidence=float(row["conf"]);x,y,w,h=(int(row[key]) for key in ("left","top","width","height"))
            key=tuple(int(row[k]) for k in ("page_num","block_num","par_num","line_num"))
        except (KeyError,TypeError,ValueError): continue
        text="".join(c for c in row.get("text","") if c.isprintable()).strip()
        if not text or not math.isfinite(confidence) or confidence<0: continue
        if not (0<=x<width and 0<=y<height and w>0 and h>0 and x+w<=width and y+h<=height): continue
        words+=1
        if words>4000: raise RuntimeError("Too much text; crop the page and retry")
        line=lines.setdefault(key,{"words":[],"box":[x,y,x+w,y+h],"scores":[]})
        line["words"].append(text);line["scores"].append(min(100,confidence))
        box=line["box"];line["box"]=[min(x,box[0]),min(y,box[1]),max(x+w,box[2]),max(y+h,box[3])]
    result=[{"text":" ".join(line["words"]),"box":line["box"],"confidence":round(sum(line["scores"])/len(line["scores"]),1)} for line in lines.values()]
    text="\n".join(line["text"] for line in result)
    if len(text)>20000: raise RuntimeError("Too much text; crop the page and retry")
    return {"text":text,"lines":result,"width":width,"height":height,"review_required":True}

def extract(image,language="eng"):
    if not isinstance(language,str) or not re.fullmatch(r"[a-z]{3}(?:\+[a-z]{3}){0,2}",language):
        raise ValueError("Use installed three-letter OCR language codes, for example eng or eng+urd")
    executable=shutil.which("tesseract")
    if not executable: raise RuntimeError("Install free Tesseract and the selected language data on your computer")
    if not GATE.acquire(blocking=False): raise BusyError("OCR is already running")
    try:
        source=ImageOps.exif_transpose(image).convert("RGB");source.thumbnail((2400,2400))
        with tempfile.TemporaryDirectory(prefix="studyforge-ocr-") as temporary:
            path=Path(temporary)/"page.png";source.save(path)
            output=Path(temporary)/"recognized"
            # File output keeps subprocess stdout bounded; all files are private/transient.
            with tempfile.TemporaryFile() as errors:
                try:
                    process=subprocess.run([executable,str(path),str(output),"-l",language,"--psm","3","tsv"],stdout=subprocess.DEVNULL,stderr=errors,timeout=25,check=False)
                except subprocess.TimeoutExpired as exc: raise RuntimeError("OCR timed out; crop the photo and retry") from exc
            tsv=output.with_suffix(".tsv")
            if process.returncode or not tsv.is_file(): raise RuntimeError("OCR failed; check installed language data and photo clarity")
            if tsv.stat().st_size>2_000_000: raise RuntimeError("OCR output exceeds the page limit; crop the photo")
            return parse_tsv(tsv.read_text(encoding="utf-8"),source.width,source.height)
    finally: GATE.release()
