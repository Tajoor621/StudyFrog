"""Build a bounded, videoctx 0.1-style index once, without any LLM calls."""
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
from PIL import Image
from image_ingest import bounded_command, local_environment

MIME = {"video/mp4", "video/quicktime", "video/webm"}
MAX_SECONDS = 3600
MAX_FRAMES = 48
MAX_SEGMENTS = 2500


def clock(seconds):
    hours, rest = divmod(int(seconds), 3600)
    minutes, seconds = divmod(rest, 60)
    return f"{hours:02}:{minutes:02}:{seconds:02}"


def subtitles(text, duration):
    segments = []
    for start, end, body in re.findall(
            r"(\d\d:\d\d:\d\d[,.]\d{3}) --> (\d\d:\d\d:\d\d[,.]\d{3})[^\n]*\n(.*?)(?=\n\s*\n|\Z)",
            text, re.S):
        def seconds(stamp):
            hours, minutes, seconds = stamp.replace(",", ".").split(":")
            return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
        a, b = max(0, seconds(start)), min(duration, seconds(end))
        body = re.sub(r"</?(?:i|b|u|font)(?:\s+[^>]*)?>", "", body, flags=re.I).strip()
        if a < b and body:
            segments.append(dict(start_s=a, end_s=b, text=body[:1500]))
        if len(segments) > MAX_SEGMENTS:
            raise ValueError("Too many subtitle cues; split the lecture into shorter clips")
    return sorted(segments, key=lambda x: x["start_s"])


def offline_helper(action, path, directory, duration, extra=()):
    env = dict(local_environment(), HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1",
               OPENCV_FFMPEG_CAPTURE_OPTIONS="protocol_whitelist;file,pipe")
    import sys
    return json.loads(bounded_command(
        [sys.executable, str(Path(__file__).resolve()), action, str(path), str(duration), *extra],
        directory, 1800 if action == "whisper" else 240, limit=4_000_000, env=env))


def boundaries(path, directory, duration):
    try:
        detected = offline_helper("scenes", path, directory, duration)
        times, method = detected["times"], detected["method"]
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
        # Demux only I-frame packets. Keep periodic samples for long static lectures.
        try:
            raw = bounded_command(
                ["ffprobe", "-v", "error", "-protocol_whitelist", "file,pipe",
                 "-select_streams", "v:0", "-show_packets", "-show_entries", "packet=pts_time,flags",
                 "-of", "csv=p=0", str(path)], directory, 40, 16_000_000)
            times = [float(match[1]) for line in raw.splitlines()
                     if (match := re.match(r"(-?\d+(?:\.\d+)?),K", line))]
            method = "ffmpeg-key-packets"
        except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
            times, method = [], "interval-samples"
    interval = max(15, math.ceil(duration / MAX_FRAMES))
    grid = range(0, math.ceil(duration), interval)
    valid = sorted({round(t, 3) for t in times if type(t) in (float, int) and 0 < t < duration})
    selected = {0.0}
    for point in grid:
        # Preserve evenly distributed anchors even when the beginning has many cuts.
        nearby = min(valid, key=lambda t: abs(t - point), default=point)
        selected.add(round(nearby if point and abs(nearby - point) < interval / 3 else point, 3))
    if valid:
        remaining = [x for x in valid if x not in selected]
        slots = MAX_FRAMES - len(selected)
        if slots > 0 and remaining:
            selected.update(remaining[i * len(remaining) // min(slots, len(remaining))]
                            for i in range(min(slots, len(remaining))))
    return sorted(selected), method


def build(path, directory, name):
    from video_remix import probe
    source = probe(path)
    duration = source["duration"]
    if duration > MAX_SECONDS:
        raise ValueError("Videos are limited to one hour")
    info = json.loads(bounded_command(
        ["ffprobe", "-v", "error", "-protocol_whitelist", "file,pipe",
         "-show_streams", "-of", "json", str(path)], directory, 15, 1_000_000))
    stream = next(s for s in info["streams"] if s["index"] == source["stream_index"])
    fps_num, fps_den = (int(n) for n in stream.get("avg_frame_rate", "0/0").split("/"))
    ffmpeg = ["ffmpeg", "-nostdin", "-v", "error", "-protocol_whitelist", "file,pipe"]
    segments, warnings, language, transcription = [], [], None, "none"
    for subtitle in (s for s in info["streams"] if s["codec_type"] == "subtitle"):
        try:
            raw = bounded_command(ffmpeg + ["-i", str(path), "-map", f"0:{subtitle['index']}",
                                           "-f", "srt", "-"], directory, 45, 4_000_000)
            segments = subtitles(raw, duration)
        except (RuntimeError, subprocess.TimeoutExpired):
            continue
        if segments:
            language = subtitle.get("tags", {}).get("language")
            transcription = "embedded-subtitles"
            break
    model = os.environ.get("STUDYFORGE_WHISPER_MODEL_DIR")
    if not segments and source["audio"] and model and Path(model).is_dir():
        wav = directory / "audio.wav"
        try:
            bounded_command(ffmpeg + ["-i", str(path), "-map", "0:a:0", "-ac", "1",
                                      "-ar", "16000", str(wav)], directory, 150)
            result = offline_helper("whisper", wav, directory, duration, (str(Path(model).resolve()),))
            segments, language = result["segments"], result["language"]
            transcription = "faster-whisper:" + Path(model).resolve().name
        except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
            warnings.append("Local transcription failed; saved frames are still available.")
        finally:
            wav.unlink(missing_ok=True)
    if not segments:
        warnings.append("No transcript. Add embedded text subtitles or configure a local Whisper model for speech.")
    starts, detector = boundaries(path, directory, duration)
    root = directory / "video.vctx"
    (root / "frames").mkdir(parents=True)
    shots = []
    for i, (start, end) in enumerate(zip(starts, starts[1:] + [duration])):
        relative = f"frames/shot{i:04}.jpg"
        frame = root / relative
        bounded_command(ffmpeg + ["-ss", str(start), "-i", str(path), "-map", f"0:{source['stream_index']}",
                                  "-frames:v", "1", "-vf",
                                  "scale=640:640:force_original_aspect_ratio=decrease",
                                  "-threads", "1", "-q:v", "3", str(frame)], directory, 25)
        keyframes = []
        if frame.is_file():
            with Image.open(frame) as im:
                keyframes.append(dict(t_s=start, file=relative, width=im.width, height=im.height))
        shots.append(dict(id=i, start_s=start, end_s=end, keyframes=keyframes))
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(65536), b""):
            digest.update(block)
    manifest = dict(format="vctx", format_version="0.1", created_at=datetime.now(timezone.utc).isoformat(),
                    producer={"name": "StudyFrog", "version": "1"},
                    params={"detector": detector, "whisper_model": transcription, "frame_max_edge": 640,
                            "max_frames": MAX_FRAMES},
                    source={"filename": name, "sha256": digest.hexdigest(), "size_bytes": path.stat().st_size})
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    index = dict(video=dict(duration_s=duration, width=source["width"], height=source["height"],
                            fps=fps_num / fps_den if fps_den else 0, has_audio=source["audio"]),
                 shots=shots, transcript=dict(language=language, model=transcription, segments=segments))
    save_index(root, index)
    return index, warnings


def save_index(root, index):
    temporary = root / "index.tmp"
    temporary.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(root / "index.json")


def notes(index):
    events = [(seg["start_s"], 0, "Transcript", seg["text"]) for seg in index["transcript"]["segments"]]
    events += [(shot["start_s"], 1, "Visual description", shot["caption"])
               for shot in index["shots"] if shot.get("caption")]
    return "# Structured video notes\n\n" + (
        "\n\n".join(f"**[{clock(t)}] {label}**\n\n{text}" for t, _, label, text in sorted(events))
        or "This index has no transcript or saved visual descriptions.\n")


def _helper():
    import sys
    action, filename, duration, *args = sys.argv[1:]
    duration = float(duration)
    if action == "scenes":
        try:
            from videoctx.shots import detect_shots
            result = dict(times=[shot.start_s for shot in detect_shots(Path(filename), duration)],
                          method="videoctx-pyscenedetect")
        except (ImportError, AttributeError):
            from scenedetect import ContentDetector, SceneManager, open_video
            manager = SceneManager()
            manager.auto_downscale = False
            manager.downscale = 4
            manager.add_detector(ContentDetector())
            manager.detect_scenes(video=open_video(filename), frame_skip=2, show_progress=False)
            result = dict(times=[a.get_seconds() for a, _ in manager.get_scene_list()],
                          method="pyscenedetect")
    elif action == "whisper":
        from faster_whisper import WhisperModel
        model = WhisperModel(args[0], device="cpu", compute_type="int8", cpu_threads=4, local_files_only=True)
        items, info = model.transcribe(filename, beam_size=1, vad_filter=True)
        segments = []
        for segment in items:
            a, b = max(0, segment.start), min(duration, segment.end)
            if a < b and segment.text.strip():
                segments.append(dict(start_s=a, end_s=b, text=segment.text.strip()[:1500]))
            if len(segments) > MAX_SEGMENTS:
                raise ValueError("Too many speech segments; split the lecture")
        result = dict(language=info.language, segments=segments)
    else:
        raise ValueError("Unknown indexing helper")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    _helper()
