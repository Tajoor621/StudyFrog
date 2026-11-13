# Validation

The [Android and engine workflow](https://github.com/Tajoor621/StudyFrog/actions/runs/42997141882) passed Android lint, JVM tests, release APK/AAB assembly and split verification, and local engine and browser checks for the Media Learning revision. The [Windows workflow](https://github.com/Tajoor621/StudyFrog/actions/runs/42997142120) also passed.

The separate Android emulator run for the phone render journal passed its new journal and remix cases but failed the Science Explorer window-focus test. A follow-up test-readiness change did not resolve that failure. See the [emulator run](https://github.com/Tajoor621/StudyFrog/actions/runs/42012904164). Physical-device performance and independent reviews remain pending.

The Creative Studio has its own [build and browser workflow](.github/workflows/studio-next.yml). Its local verification covered 22 tests, including real FFmpeg WebAssembly stitching and simulated model and Supabase responses. A clean build does not establish live inference quality or hosted Community readiness.

Previous verified release: [0.11.0 validation](https://github.com/Tajoor621/StudyFrog/blob/3bf60562c9119e0d52463cfdd9d957213185d26e/VALIDATION.md).
