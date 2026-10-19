# Photo guidance and reviewed notes — 0.7.0

These features run on your own computer, without a paid API, account, mandatory
cloud service or automatic model download. Android connects through `adb reverse`;
iOS can use a trusted local network; the web app uses its same-origin local bridge.
They supplement the existing on-device image generation and editor.

## Automatic photo guidance

The image endpoint accepts two additional `control` values:

| Mode | Preparation | Required local configuration |
| --- | --- | --- |
| `depth_photo` | CPU relative inverse-depth estimation, finite-value checks, normalized grayscale map | `STUDYFORGE_DEPTH_ESTIMATOR` plus `STUDYFORGE_CONTROLNET_DEPTH` |
| `pose_photo` | CPU RT-DETR person detection, up to eight people, COCO-17 ViTPose, synthesized neck and OpenPose-style body rendering | `STUDYFORGE_PERSON_DETECTOR`, `STUDYFORGE_POSE_ESTIMATOR` plus `STUDYFORGE_CONTROLNET_POSE` |

Prepared `depth`/`pose` maps, Canny and IP-Adapter remain supported. Estimation is
performed **before** loading SD, and CPU estimator weights are released after
use. Photos are aspect-fitted/center-cropped to output dimensions. Choose an
appropriate aspect ratio to keep the subject in frame. Web/API jobs expose width/height; native computer-image clients currently use the engine’s 768×432 default, so frame their photos accordingly or use the web/API for portrait output. Pose output has body
keypoints, not detailed hand/face tracking; only confident in-bounds joints are
rendered. Missing models, people or usable depth variation produce clear errors.
There is no silent substitution of a photo for a conditioning map.

Install the pinned free model dependencies in a virtual environment:

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r engine/requirements-models.txt
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
export STUDYFORGE_MODEL=/absolute/path/to/sd1-diffusers
export STUDYFORGE_CONTROLNET_DEPTH=/absolute/path/to/sd1-depth-controlnet
export STUDYFORGE_DEPTH_ESTIMATOR=/absolute/path/to/inverse-depth-estimator
export STUDYFORGE_CONTROLNET_POSE=/absolute/path/to/sd1-openpose-controlnet
export STUDYFORGE_PERSON_DETECTOR=/absolute/path/to/rtdetr-coco
export STUDYFORGE_POSE_ESTIMATOR=/absolute/path/to/vitpose-coco17
export STUDYFORGE_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
python engine/server.py
```

Supply complete Transformers config/processor/safetensors directories yourself.
Choose an inverse-depth model supported by Transformers 4.49 (for example a
compatible Depth Anything architecture), a COCO RT-DETR with a `person` label,
and a COCO-17 VitPose model. Model licenses and use restrictions must permit your
intended use. The project does not distribute these weights or assert that every
checkpoint works. Loads specify `local_files_only`, `trust_remote_code=False`
and safetensors. Paths come from the computer environment, never a request.
`GET /health` reports configured folders; configuration is not a compatibility test.

Android/iOS: import your photo, choose **estimate depth from photo** or **detect
body pose from photo**, set strength, then submit a computer image job. These
features require the configured computer; they do not run a phone-native detector.

## Photo text to a reviewed study prompt

Install free Tesseract 5 and the language files you need. For Debian/Ubuntu:

```bash
sudo apt-get update
sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-urd tesseract-ocr-ara
```

Use your platform's free Tesseract distribution on macOS/Windows. Verify
`tesseract --list-langs`; no language data is downloaded by Study Frog.

1. Import a clear photo of notes and crop it before import when needed.
2. Android/iOS: choose **Read/extract photo text · local computer**, select an
   installed English, Urdu or Arabic language and confirm the transfer.
   Web: choose installed language codes (for example `eng+urd`) and read canvas text.
3. Review and correct every extracted word, especially technical terms and labels.
4. Explicitly choose **Use reviewed notes**. The app replaces the prompt only at
   this step, and rejects combined prompts above 2000 characters without truncating.
5. Select an image-generation engine and generate as a separate action.

`POST /ocr` accepts `{ "image": "<base64 image>", "language": "eng" }` with the
same bearer token as generation. It returns `text`, `lines` (text, pixel `box`
`[left,top,right,bottom]`, mean confidence), OCR image `width`/`height`, and
`review_required: true`. It handles one OCR request at a time, limits the image
side to 2400px, permits at most three installed three-letter language codes,
kills Tesseract after 25 seconds, bounds output, and deletes temporary files on
success/failure/timeout. OCR does not create a saved engine job or a prompt log.
The existing canvas/editor downsampling still affects available OCR detail.

OCR is transcription, **not scientific fact checking**. Text extraction and a
user review step do not establish an accurately labeled or clinically reviewed
diagram. Relative depth is not metric distance or medical imaging. Real weights,
clinical review and physical-device performance remain separate validation work.

## Verification

```bash
python -m pip install -r engine/requirements-test.txt
(cd engine && python -m unittest -v)
python -m unittest discover -s web/tests -v
node web/tests/service-worker.test.cjs
```

Tests include actual Tesseract recognition of rendered notes and actual OpenCV
Canny, depth normalization, pose-map confidence/geometry, missing local model
failure, HTTP proxy authorization/redirect rejection and private-file isolation.
The depth/pose estimators have not been evaluated with actual pretrained weights.

API references: [Transformers 4.49 ViTPose](https://huggingface.co/docs/transformers/v4.49.0/en/model_doc/vitpose),
[depth estimation](https://huggingface.co/docs/transformers/v4.49.0/en/model_doc/depth_anything),
and [Tesseract TSV](https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html).
