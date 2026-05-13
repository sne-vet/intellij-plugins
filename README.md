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

## Local development install

On macOS, build the plugin and install it into a local JetBrains IDE:

```bash
./gradlew install
```

This Gradle task is only supported on macOS. It discovers installed JetBrains
IDEs, prompts for the target IDE when more than one is found, runs
`:plugins:snevet-tools:buildPlugin`, and installs the newest ZIP from
`plugins/snevet-tools/build/distributions/` into that IDE's local plugins
directory. On other operating systems, the task exits with an error instead of
attempting an install.

Useful options:

```bash
./gradlew :plugins:snevet-tools:listLocalJetBrainsIdes
./gradlew install -PlocalIdeIndex=1
./gradlew install -PlocalIdePath="/Applications/IntelliJ IDEA.app"
```

## Installation

Build the plugin, then install the resulting ZIP from `plugins/snevet-tools/build/distributions/` via `Settings > Plugins > Install Plugin from Disk`.
