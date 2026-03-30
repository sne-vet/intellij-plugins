# snevet tools

A JetBrains IDE plugin that adds a keyboard shortcut to quickly diff your working copy against the merge base of your current branch.

## Features

- **Diff with merge base** — Press `Cmd+Shift+M` (Mac) or `Ctrl+Shift+M` (Windows/Linux) to see all changes since your branch diverged from the base branch
- **Configurable base branch** — Set the branch to compute the merge base from per-project in `Settings > Tools > snevet tools` (defaults to `master`)
- **Works with any JetBrains IDE** — IntelliJ IDEA, WebStorm, PyCharm, GoLand, etc.

## Building

```bash
./gradlew build
```

## Running (development)

```bash
./gradlew runIde
```

## Installation

Build the plugin, then install the resulting ZIP from `build/distributions/` via `Settings > Plugins > Install Plugin from Disk`.
