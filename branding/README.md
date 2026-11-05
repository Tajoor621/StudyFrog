# StudyFrog supplied brand artwork

The original attached image is retained unchanged as studyfrog-original.png.
Launcher assets are deterministic crops of its frog/graduation artwork, resized
without generating or redrawing the character. The complete supplied artwork
appears in the app and repository documentation. Android legacy/adaptive/round
icons and iOS AppIcon sizes are supplied. The Android white notification small
icon stays monochrome as required by the notification UI; its large icon uses
the supplied frog artwork. The application name and package are unchanged.

Regenerate from the committed original with `python scripts/generate_branding.py`
after installing `engine/requirements.txt`. The crop is fixed for this supplied image.

`studyfrog-icon-1024.png` is that crop at 1024 pixels and matches the iOS marketing icon.
`studyfrog-icon-192.png` and `studyfrog-icon-512.png` are the same crop for the web
manifest and favicon. `studyfrog-social.jpg` is a JPEG of the banner for link previews.
The web app does not use the full-size original PNG.
