# Desktop packages

The `desktop-package` Maven profile stages application JARs and platform-specific
JavaFX runtime dependencies, launch scripts, and a standalone README. The packaging
script uses the build machine's JDK `jlink` and `jpackage` tools to produce an
application image with its own Java runtime.

## Build on the target platform

Requirements: a full JDK 17+, Maven 3.9+, and Python 3.9+ (3.12 is used in CI).
Set `JAVA_HOME` to that JDK. Build each OS/architecture on a matching host:

```sh
python scripts/package_desktop.py
```

Outputs in `target/releases/`:

- `worldtemps-<platform>-bundled.zip`: an app image with a linked Java runtime;
  no separately installed Java or Maven is needed.
- `worldtemps-<platform>-portable.zip`: application JARs and dependencies;
  Java 17+ is required, Maven is not.
- `SHA256SUMS-<platform>.txt`: SHA-256 checksums for both archives.

Platforms are labeled by OS and architecture, such as `linux-x64`, `windows-x64`,
or `macos-arm64`. These are not interchangeable. Native images are unsigned and
macOS notarization is not included. Linux still needs GTK 3/X11 desktop libraries;
on Ubuntu 24.04 install `libgtk-3-0t64 libxtst6`.

For an existing desktop-profile build, `--skip-build` skips Maven and verifies the
staging area and native JavaFX classifier before packaging. The native runtime
explicitly includes desktop, HTTP, and elliptic-curve cryptography support.
Dependencies, not user caches or downloaded weather data, are packaged.

## Run

Extract a bundled ZIP and launch:

| OS | Launcher |
| --- | --- |
| Windows | `WorldTemps/WorldTemps.exe` |
| macOS | `WorldTemps.app` |
| Linux | `WorldTemps/bin/WorldTemps` |

Portable distributions use `start-worldtemps.cmd` or `./start-worldtemps.sh`.
Launchers accept `--demo` for immediate offline startup, or one quoted CSV path.
The default opens historical weather search. `--version` reports the packaged
version. The portable core JAR also supports `java -jar worldtemps-core.jar` from
inside the extracted distribution.

## Validation

The packaging script checks the native image and both extracted ZIPs, including
launch scripts in paths containing spaces. A headless smoke command verifies
JavaFX classes, styles, CSV/JSON parsing, bundled demo analysis, HTTP support, and
the elliptic-curve signature provider. A report confirms the native image and
extracted native image use their own runtime rather than an external installation.

Packages include `PROVENANCE.json` with version, platform, source commit, and dirty
working-tree status. The recorded native `java.home` is checked during the build;
private weather caches are never included. Linux JavaFX behavior is exercised by
the UI integration suite. Windows/macOS CI performs package/headless checks;
those do not replace manual visual testing on those operating systems.

## CI artifacts and draft release

Every PR runs the test suite and a Windows/macOS/Linux package matrix. Packages
and checksums appear as artifacts in its GitHub Actions run. Access follows the
repository's current visibility; packaging does not change sharing.

After a merge to `main`, and only after all platform jobs succeed, CI creates a
**draft** release named from the Maven version (initially `v1.0.0`) with ZIPs,
checksums, and `docs/release-notes.md`. It does not publish the release. An existing
release for that version is left unchanged. Review the draft and its assets before
publishing; bump the version for subsequent releases rather than silently
replacing an existing release.
