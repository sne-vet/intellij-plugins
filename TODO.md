# snevet-tools TODO

## Multi-root repository support

**Impact:** High | **Effort:** Medium

Only the first Git repository is used (`repositories.first()` in `DiffWithMergeBaseAction.actionPerformed`). In monorepos or projects with submodules, changes from other roots are silently ignored.

**Recommended action:** Use `GitRepositoryManager.getRepositoryForFileQuick()` to find the repo for the currently focused file, or iterate all repositories and aggregate changes.

## TOCTOU: two separate `git diff` calls

**Impact:** Medium | **Effort:** High

`--name-status` and `--numstat` are run as two separate `git diff` processes against the same merge base. If files change between the calls, the outputs can be inconsistent. Practically negligible for a personal tool.

**Recommended action:** Use `git diff --raw -M -z` for a single-pass approach that provides status, paths, and binary detection. Requires rewriting the parsing logic.

## Quoted path parsing breaks on special characters

**Impact:** Medium | **Effort:** Medium

`git diff --name-status`, `git diff --numstat`, and `git ls-files --others` can emit quoted/C-escaped paths for filenames containing tabs, newlines, and other special characters. The current parsing logic splits on tabs and then treats the quoted string as a literal filesystem path, which produces invalid `FilePath` instances for those files.

**Recommended action:** Switch to `-z` output for all Git commands that return paths and parse NUL-delimited records instead of line-oriented text.

## No test coverage

**Impact:** Medium | **Effort:** High

There are zero automated tests. The `computeChanges` parsing logic (status codes, rename handling, untracked files, binary detection) and `expandBracePath` would benefit from unit tests with mocked git output.

## `SimpleAsyncChangesBrowser` shutdown is missing

**Impact:** Medium | **Effort:** Low

`SimpleAsyncChangesBrowser` documents that `shutdown()` must be called when the browser is no longer needed. `showChanges` creates a new browser each time and removes old contents, but there is no disposal hook that shuts the browser down when the tool window content is replaced or disposed.

**Recommended action:** Register a content disposer or other lifecycle hook that calls `browser.shutdown()` when the content is removed.

## Stale `gradle.properties` values

**Impact:** Low | **Effort:** Low

`pluginGroup`, `pluginName`, `pluginVersion`, `platformType`, and `platformVersion` in `gradle.properties` are leftover from Gradle IntelliJ Plugin v1. They are not read by v2 and could mislead future maintainers.

**Recommended action:** Remove or consume them explicitly via `providers.gradleProperty(...)`.

## Redundant `id("java")` plugin

**Impact:** Low | **Effort:** Low

`plugins/snevet-tools/build.gradle.kts` applies both `id("java")` and `kotlin("jvm")`. The latter already applies the Java plugin.

**Recommended action:** Remove `id("java")`.

## Hardcoded tool window ID

**Impact:** Low | **Effort:** Low

`"snevet tools"` appears as a string literal in both `DiffWithMergeBaseAction.kt` and `plugin.xml`. A typo in either silently breaks the lookup (the `?: return` swallows the failure).

**Recommended action:** Extract to a companion object constant.

## `findMergeBase` error message

**Impact:** Low | **Effort:** Low

Error message says "branch not found or repository has no commits" but `git merge-base` can also fail when branches have no common ancestor (orphan branches).

**Recommended action:** Update message: "Ensure the branch exists and shares history with HEAD."

## `@Suppress("UnstableApiUsage")` is broader than needed

**Impact:** Low | **Effort:** Low

The suppression is on the entire `actionPerformed` method but only `withBackgroundProgress` and `SimpleAsyncChangesBrowser` need it.

**Recommended action:** Narrow the suppression to the specific call sites.

## Untracked files included with no opt-out

**Impact:** Low | **Effort:** Medium

Untracked files are always shown as additions. Users may not expect this in a "diff with merge base" operation.

**Recommended action:** Add a settings checkbox to include/exclude untracked files.

## Redundant Mac OS X keymap entry in plugin.xml

**Impact:** Low | **Effort:** Low

Both `Mac OS X` and `Mac OS X 10.5+` keymaps define the same `meta shift M` shortcut. The `Mac OS X 10.5+` keymap extends `Mac OS X`, making the parent entry redundant.

**Recommended action:** Remove the `Mac OS X` shortcut line, keep only `Mac OS X 10.5+`.

## `settings` computed property in SnevetToolsConfigurable

**Impact:** Low | **Effort:** Low

`SnevetToolsConfigurable.settings` uses `get() = SnevetToolsSettings.getInstance(project)`, re-resolving the service on every access. Functionally safe but wasteful.

**Recommended action:** Change to a plain `val` (no custom getter).

## `kotlin.stdlib.default.dependency=false` set globally

**Impact:** Low | **Effort:** Low

Set in `gradle.properties` rather than per-subproject. If new subprojects produce standalone JARs, they will silently lack the stdlib.

**Recommended action:** Move to subproject-specific configuration if non-plugin modules are added.
