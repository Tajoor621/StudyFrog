"""Deterministic synthetic media for native CI; no user or third-party footage."""
import argparse
import json
from pathlib import Path
import subprocess

parser=argparse.ArgumentParser();parser.add_argument('directory',type=Path);args=parser.parse_args()
args.directory.mkdir(parents=True,exist_ok=True)
for silent in (False,True):
    command=['ffmpeg','-v','error','-y','-f','lavfi','-i','color=c=green:s=160x96:r=30:d=1.5']
    if not silent: command+=['-f','lavfi','-i','sine=frequency=440:sample_rate=48000:duration=1.5']
    if not silent: command+=['-map','1:a:0','-map','0:v:0']
    command+=['-vf',"drawbox=x=60:y=20:w=40:h=56:color=red:t=fill,drawbox=x=8:y=8:w=24:h=16:color=yellow:t=fill:enable='gte(t,0.5)'",'-c:v','libx264','-threads','1','-pix_fmt','yuv420p']
    if not silent: command+=['-af',"volume='if(gte(t,0.5),1,0)':eval=frame",'-c:a','aac','-shortest']
    subprocess.run(command+[str(args.directory/('phone-silent.mp4' if silent else 'phone-source.mp4'))],check=True,timeout=30)

help_text=subprocess.check_output(['ffmpeg','-hide_banner','-h','full'],stderr=subprocess.DEVNULL,text=True)
rotation=['-display_rotation:v:0','90','-i',str(args.directory/'phone-source.mp4'),'-c','copy'] if '-display_rotation' in help_text else ['-i',str(args.directory/'phone-source.mp4'),'-c','copy','-metadata:s:v:0','rotate=90']
subprocess.run(['ffmpeg','-v','error','-y',*rotation,str(args.directory/'phone-portrait.mp4')],check=True,timeout=30)
metadata=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_streams','-of','json',str(args.directory/'phone-portrait.mp4')]))
assert any(abs(item.get('rotation',0))==90 for item in metadata['streams'][0].get('side_data_list',[])), 'Portrait fixture must contain a real rotation matrix'
