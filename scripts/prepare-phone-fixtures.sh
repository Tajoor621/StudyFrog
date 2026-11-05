#!/usr/bin/env bash
set -euo pipefail
# Explicit test-only download. Production apps never download weights implicitly.
output=${1:?Provide a fixture output directory}
mkdir -p "$output"
curl -fL --retry 3 https://media.githubusercontent.com/media/opencv/opencv_zoo/2027dd2f5a8a5746b5d4964900a0465afc6d3a53/models/human_segmentation_pphumanseg/human_segmentation_pphumanseg_2023mar.onnx -o "$output/phone-person.onnx"
python3 - "$output/phone-person.onnx" <<'PY'
import hashlib,sys
from pathlib import Path
p=Path(sys.argv[1])
assert p.stat().st_size==6163938
assert hashlib.sha256(p.read_bytes()).hexdigest()=='552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24'
PY
python3 scripts/create_phone_video_fixtures.py "$output"
