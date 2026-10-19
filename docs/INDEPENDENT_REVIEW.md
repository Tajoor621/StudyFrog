# Independent review and evidence gate

No independent scientist, safeguarding reviewer or physical-device operator has
reviewed this revision through this workflow. All production review/evidence
lists start empty. Automated tests and this assistant's code review are not
independent approvals. Volunteer qualified reviewers can use this pack without
a paid review service. No person has been contacted or assigned automatically.

## Science pack

Scope: the ten original conceptual diagrams in `content/study-catalog.json`,
their labels, body-to-cell relationships, mitosis sequence, source citations and
both native renderers. It does not include generated model output, a broad
medical atlas, diagnostic CT/MRI or clinical validity.

A reviewer with relevant biology/anatomy teaching or scientific expertise should:

1. Open every diagram on Android and iOS, with/without labels; inspect label
   placement at large fonts and confirm that simplifications/not-to-scale notices
   remain clear. Compare the statements and progression against the listed
   primary educational sources.
2. Record each node id, exact incorrect/misleading statement or geometry, its
   severity, source and correction. Treat omitted structures honestly; do not
   infer clinical accuracy from an attractive rendering.
3. Test prompt handoff/OCR correction and confirm generated refinements are not
   presented as independently verified science. Family study content must remain
   available without exposing general generation/private projects.
4. Provide a dated report with qualifications, conflicts/implementation
   involvement, covered revision, findings and approval/changes requested.

Fix blocking scientific errors before approval, then request review of the new
subject digest. A changed digest invalidates an earlier approval automatically.

## Family-control pack

Ask a reviewer independent of implementation, with safeguarding/security or
parental-control testing experience, to test a clean app and an existing library:

- Age acknowledgement and parent PIN enrollment, wrong PIN retry/cooldown and
  cooldown persistence across rotation/restart/process termination/device reboot.
- Restricted home routing from launcher, direct intents, recent-apps navigation,
  back stack, settings and restored activities. Check that prompts, photos,
  project history, import/export, computer connection and general generation
  are unavailable in restricted mode.
- Lock while generation, image import, video export or file pickers are active;
  inspect background results, notifications, stale callbacks, screenshots and
  reopening. Review what cannot be enforced against a rooted device, OS account
  owner, another browser or a separately running computer engine.
- PIN storage/KDF and missing/corrupt-state behavior, backup exclusions and
  accidental recovery/bypass paths. Document scope and known limits rather than
  declaring the app universally child-safe or tamper-proof.

Record reproducible failures and remediation before approval. The local
computer engine has no application-level parental PIN; restricted phone views
must not offer its general inputs. Independent review cannot certify all model
outputs or every OS/device configuration.

## Records and checking

`validation/release-evidence.json` contains subject source paths, model
provenance, diagnostic references, physical manual sessions and review records.
Run:

```bash
python3 scripts/release_readiness.py
python3 scripts/release_readiness.py --require-ready
```

The first command reports pending work without pretending a build failure is
model evaluation. The second returns exit code 2 until the requested evidence
is complete and current, and gates the protected owner-signed release job.
Normal APK/AAB/simulator development builds remain available for testing.

Science/family records require `reviewer`, `qualifications`, `independent: true`,
`outcome: "approved"`, the corresponding current `subject_digest` printed by
the command, and `evidence: {path, sha256}` pointing to the real review report.
Assistant identities are rejected. Do not add a report before its author has
actually reviewed and approved its recorded scope.

A model provenance entry needs `name`, `sha256`, `source_url` and `license_url`.
Use the actual file digest for native models or the computer benchmark's bundle
digest. Reference real JSON benchmark reports in `benchmarks` with `{path,
sha256}`. The current build needs at least two model digests per platform and
both public prompt cases; failed or preflight-only runs do not qualify.

Manual sessions follow [the session schema](../validation/device-session.schema.json)
and are referenced with `{path, sha256}` in `device_sessions`. Store reviewed
JSON and referenced screenshots/reports inside the checkout, for example under
ignored `device-evidence/`. All referenced bytes must match their recorded
SHA-256. Default CI does not upload these private local files. Share only
reviewed/redacted evidence. Public approved reports and minimal safe attachments
can be committed under `validation/evidence/`; originals stay ignored. Alternatively,
store checksum references/review reports in a
private owner-controlled distribution workflow. A clean hosted runner needs
access to those actual evidence files before a strict owner release can pass;
checksum references to absent local files deliberately fail.

Human identity, credentials, independence and actual physical execution remain
operator attestations. Hashes establish byte/revision integrity, not authenticity,
clinical certification or a “10/10” product rating. The gate covers this validation
workstream and does not claim that all master-spec features are implemented.
