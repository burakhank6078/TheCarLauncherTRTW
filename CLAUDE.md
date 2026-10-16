# TheCarLauncherTRTW — fork notes

- Fork of LazarC11/TheCarLauncher (`upstream`), Turkish translation maintained here (`origin`).
- UI texts use `s("Serbian", "English")`. Turkish lives only in `mib/Tr.java`, keyed by the English text; never edit the upstream call sites for translation (keeps merges clean). Texts with dynamic parts or different Turkish word order use `Mib.t(ctx, sr, en, tr)`.
- Stay inside the VW MIB 2 look; do not restyle the interface.
- After every upstream release/merge: run `tools/check-translations.ps1`, add missing strings to `Tr.java`, write the Turkish bulletin in `CHANGELOG-TR.md`, and tell the user which new features arrived together with the new/changed strings.
- Files use CRLF line endings; keep them.
