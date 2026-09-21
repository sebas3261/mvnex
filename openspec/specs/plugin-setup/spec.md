# plugin-setup Specification

## Purpose
TBD - created by archiving change port-to-maven-plugin. Update Purpose after archive.
## Requirements
### Requirement: Setup goal
The plugin SHALL provide a `setup` goal, invoked with the fully qualified form `mvn com.sebas3261:ex-maven-plugin:<version>:setup`, that registers the plugin group `com.sebas3261` in the user's Maven settings so the `ex` prefix resolves (`mvn ex:init`, `mvn ex:add`). The goal SHALL NOT require a project, SHALL run once per invocation (aggregator), SHALL NOT prompt, and SHALL behave identically in interactive and batch mode.

#### Scenario: First-time setup
- **WHEN** a user with no `~/.m2/settings.xml` runs `mvn com.sebas3261:ex-maven-plugin:0.2.0:setup` in any directory
- **THEN** the goal succeeds and a subsequent `mvn ex:help` resolves to this plugin

#### Scenario: Runs once in a reactor
- **WHEN** the goal is run from the root of a multi-module project
- **THEN** the settings file is processed exactly once

### Requirement: Settings file location
The `setup` and `uninstall` goals SHALL target the user settings file that Maven itself is using for the current invocation: the file passed with `-s`/`--settings` when given, otherwise `<user.home>/.m2/settings.xml`. Maven itself rejects a `-s` file that does not exist (`The specified user settings file does not exist`) before any goal runs, so a settings file can only be *created* at the default location; with `-s` the file must already exist and is updated. If the target path is a symbolic link, the goal SHALL read and write the link's target and leave the link in place. The global settings file (`<maven.home>/conf/settings.xml`) SHALL NOT be modified.

#### Scenario: Default location
- **WHEN** no `-s` option is given
- **THEN** the goal targets `<user.home>/.m2/settings.xml`

#### Scenario: Custom settings file
- **WHEN** the user runs the goal with `-s /work/ci-settings.xml` and that file exists
- **THEN** `/work/ci-settings.xml` is updated, and `~/.m2/settings.xml` is untouched

#### Scenario: Custom settings file that does not exist
- **WHEN** the user runs the goal with `-s /work/missing.xml`
- **THEN** Maven fails with `The specified user settings file does not exist` before the goal runs, and nothing is created

#### Scenario: Symlinked settings
- **WHEN** `~/.m2/settings.xml` is a symlink to `~/dotfiles/maven-settings.xml`
- **THEN** `~/dotfiles/maven-settings.xml` is updated and `~/.m2/settings.xml` remains a symlink

### Requirement: Create settings file when missing
When the target file does not exist, the goal SHALL create any missing parent directories and write (line separators = the platform line separator, UTF-8):
```
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <pluginGroups>
    <pluginGroup>com.sebas3261</pluginGroup>
  </pluginGroups>
</settings>
```
and log `Created <path> with plugin group com.sebas3261.`

#### Scenario: No .m2 directory
- **WHEN** `<user.home>/.m2/` does not exist
- **THEN** the directory and `settings.xml` are created with exactly the content above

### Requirement: Detect existing registration
The goal SHALL parse the existing file as Maven settings and SHALL treat the plugin as registered only when an active (non-commented) `<pluginGroup>` element whose trimmed value is `com.sebas3261` exists. When registered, the goal SHALL NOT modify the file (no rewrite, no backup, unchanged timestamp) and SHALL log `Plugin group com.sebas3261 is already configured in <path>. No changes made.`

#### Scenario: Already registered
- **WHEN** the settings contain `<pluginGroup>com.sebas3261</pluginGroup>` among other groups
- **THEN** the file is byte-identical and unmodified after the goal, and the already-configured message is logged

#### Scenario: Running setup twice
- **WHEN** the goal is run twice in a row
- **THEN** the second run makes no changes and logs the already-configured message

#### Scenario: Group only present in a comment
- **WHEN** the file contains `<!-- <pluginGroup>com.sebas3261</pluginGroup> -->` and no active entry
- **THEN** the plugin is treated as not registered and an active entry is added

### Requirement: Add pluginGroup to an existing file
When the file exists and the plugin is not registered, the goal SHALL insert the entry textually, preserving every other byte of the file (comments, formatting, element order, encoding, line separators), using the first matching case:
1. **Active `<pluginGroups>` element with content** — insert a new line `<pluginGroup>com.sebas3261</pluginGroup>` immediately before the line containing the closing `</pluginGroups>` tag, indented like the last active `<pluginGroup>` child if one exists, otherwise like the closing tag plus two spaces. If the closing tag shares its line with other content (e.g. `<pluginGroups><pluginGroup>org.foo</pluginGroup></pluginGroups>`), the entry is inserted inline immediately before `</pluginGroups>`, without adding line breaks.
2. **Self-closing `<pluginGroups/>`** — replace it with an opening tag, the entry line (closing-tag indentation plus two spaces), and a closing tag at the original indentation.
3. **No active `<pluginGroups>` element** — insert a `<pluginGroups>` block containing the entry immediately before the closing `</settings>` tag's line (or, if that tag shares its line with other content, on a new line just before it), indented like the first child element of `<settings>`, with the entry one indentation level deeper, where the level is that child's indentation relative to `<settings>` (two spaces if `<settings>` has no children). A self-closing `<settings/>` is expanded into an open and a closing tag around the block, keeping its attributes.

Text inside XML comments and CDATA sections SHALL be ignored when locating elements. Inserted lines SHALL use the file's existing line separator (CRLF if the file's first line break is CRLF, otherwise LF). The file SHALL be read and written in the encoding declared by its XML declaration, defaulting to UTF-8.

#### Scenario: Maven's default settings template
- **WHEN** the file is Maven 3.9's `conf/settings.xml` copied verbatim, whose `<pluginGroups>` contains only a commented-out `<pluginGroup>com.your.plugins</pluginGroup>`
- **THEN** `    <pluginGroup>com.sebas3261</pluginGroup>` is inserted on its own line just before `  </pluginGroups>`, and the comment is unchanged

#### Scenario: Existing groups
- **WHEN** `<pluginGroups>` already contains `<pluginGroup>org.mortbay.jetty</pluginGroup>` indented with a tab
- **THEN** the new entry is added after it with the same tab indentation

#### Scenario: No pluginGroups element
- **WHEN** the file has `<settings>` with `<localRepository>` and `<mirrors>` children indented four spaces and no `<pluginGroups>`
- **THEN** a four-space-indented `<pluginGroups>` block with an eight-space-indented entry is inserted before `</settings>`

#### Scenario: Single-line pluginGroups
- **WHEN** the file contains `  <pluginGroups><pluginGroup>org.foo</pluginGroup></pluginGroups>`
- **THEN** that line becomes `  <pluginGroups><pluginGroup>org.foo</pluginGroup><pluginGroup>com.sebas3261</pluginGroup></pluginGroups>`

#### Scenario: Windows line endings
- **WHEN** the file uses CRLF line endings
- **THEN** every inserted line ends with CRLF and no LF-only line breaks are introduced

### Requirement: Safe modification
Before modifying an existing file, the goal SHALL:
1. Parse the original file; if it is not well-formed settings XML, fail with `Could not update <path>: <parser message>. No changes made.` and leave it untouched.
2. Parse the edited content in memory and confirm it is well-formed and that its plugin groups contain `com.sebas3261`; otherwise fail with the same message form and leave the original untouched.
3. Copy the original to `<file name>.<yyyyMMddHHmmss>.bak` in the same directory.
4. Write the new content to a temporary file in the same directory and atomically move it over the target (falling back to a non-atomic replace only when the filesystem does not support atomic moves).

On success it SHALL log `Added plugin group com.sebas3261 to <path>.` and `Backup saved to <backup path>.` File permissions of the original SHALL be preserved where the filesystem supports POSIX permissions.

#### Scenario: Malformed settings
- **WHEN** the existing file has an unclosed `<mirrors>` element
- **THEN** the goal fails with `Could not update <path>: ...` and no backup or changes are made

#### Scenario: Backup created
- **WHEN** a valid file without the plugin group is updated
- **THEN** a timestamped `.bak` file with the original bytes exists next to it and the updated file parses as settings containing `com.sebas3261`

#### Scenario: Restricted permissions preserved
- **WHEN** the original file has mode `600`
- **THEN** the updated file also has mode `600`

### Requirement: Completion hint
After creating or updating the file (and when it was already configured), the goal SHALL log `You can now run: mvn ex:init, mvn ex:add, mvn ex:help`.

#### Scenario: Hint after setup
- **WHEN** the goal succeeds in any case
- **THEN** the log ends with the hint line

### Requirement: Setup hint from other goals
When `init` or `add` runs and the effective Maven settings (user and global combined) do not list `com.sebas3261` in their plugin groups, the goal SHALL log once, at info level: `Tip: run mvn com.sebas3261:ex-maven-plugin:<version>:setup to use the short form mvn ex:<goal>.` with the running plugin version substituted. This hint SHALL NOT affect the goal's outcome.

#### Scenario: Fully qualified init without setup
- **WHEN** a user without the plugin group runs `mvn com.sebas3261:ex-maven-plugin:0.2.0:init -Dex.name=demo`
- **THEN** the project is created and the log contains the setup tip with version `0.2.0`

#### Scenario: Prefix already works
- **WHEN** the plugin group is configured and the user runs `mvn ex:add -Dex.deps=lombok`
- **THEN** no setup tip is logged

### Requirement: Uninstall goal
The plugin SHALL provide an `uninstall` goal (`mvn ex:uninstall`, or `mvn com.sebas3261:ex-maven-plugin:<version>:uninstall`) that removes this plugin's registration from the user settings file chosen by the "Settings file location" rules. The goal SHALL NOT require a project, SHALL run once per invocation (aggregator), SHALL NOT prompt, and SHALL behave identically in interactive and batch mode. It SHALL only edit settings. Plugin artifacts in the local repository, projects, and the global settings file SHALL NOT be modified.

#### Scenario: Remove after setup
- **WHEN** a user who ran `setup` runs `mvn ex:uninstall`
- **THEN** the `com.sebas3261` plugin group is gone from `~/.m2/settings.xml`, and afterwards `mvn ex:help` fails with Maven's "No plugin found for prefix 'ex'" error while the fully qualified goals still work

#### Scenario: Custom settings file
- **WHEN** the user runs `mvn -s /work/ci-settings.xml ex:uninstall`
- **THEN** only `/work/ci-settings.xml` is changed

### Requirement: Uninstall removes only this plugin's entry
The goal SHALL remove every active (non-commented) `<pluginGroup>` element whose trimmed value is exactly `com.sebas3261`, and nothing else:
- When a removed element is the only non-whitespace content on its line, the whole line SHALL be removed, including its leading whitespace and its line separator.
- Otherwise only the element's own text from `<pluginGroup>` through `</pluginGroup>` SHALL be removed.
- The enclosing `<pluginGroups>` element SHALL be kept even if it becomes empty. This applies even when `setup` created it, and even when `setup` created the whole file.
- Comments (including commented-out `com.sebas3261` entries), CDATA, other plugin groups, other elements, whitespace, element order, encoding, the XML declaration, and line separators SHALL be preserved byte for byte.
- The file SHALL NOT be deleted, even if it only ever contained what `setup` wrote.

Text inside XML comments and CDATA sections SHALL be ignored when locating elements.

#### Scenario: Only this plugin's line is removed
- **WHEN** `<pluginGroups>` contains `org.mortbay.jetty`, `com.sebas3261`, and `org.codehaus.cargo` on separate lines
- **THEN** the output equals the input with exactly the `com.sebas3261` line removed

#### Scenario: Round trip with setup
- **WHEN** `setup` is run on any existing settings file that lacked the group, and then `uninstall` is run
- **THEN** the file is byte-identical to the original whenever `setup` used insertion case 1 (existing `<pluginGroups>` with content)
- **AND** for cases 2 and 3 the only remaining differences are the `<pluginGroups>` element that `setup` introduced or expanded, now empty

#### Scenario: Similar group names untouched
- **WHEN** the file contains `<pluginGroup>com.sebas3261.tools</pluginGroup>` and `<pluginGroup>com.sebas3261</pluginGroup>`
- **THEN** only the exact `com.sebas3261` entry is removed

#### Scenario: Commented entry untouched
- **WHEN** the file contains `<!-- <pluginGroup>com.sebas3261</pluginGroup> -->` and one active entry
- **THEN** the active entry is removed and the comment is byte-identical

#### Scenario: Inline element
- **WHEN** the file contains `<pluginGroups><pluginGroup>org.foo</pluginGroup><pluginGroup>com.sebas3261</pluginGroup></pluginGroups>` on one line
- **THEN** the line becomes `<pluginGroups><pluginGroup>org.foo</pluginGroup></pluginGroups>`

#### Scenario: Duplicate entries
- **WHEN** the file contains two active `com.sebas3261` entries
- **THEN** both are removed and nothing else changes

#### Scenario: CRLF file
- **WHEN** the file uses CRLF line endings
- **THEN** the removed line's CRLF is removed with it and every other line ending is unchanged

### Requirement: Uninstall no-op cases
The goal SHALL succeed without writing, creating a backup, or changing the file's timestamp when:
- the target file does not exist. It logs `No settings file at <path>. Nothing to uninstall.` and SHALL NOT create the file or its directory.
- the file has no active `com.sebas3261` entry. It logs `Plugin group com.sebas3261 is not configured in <path>. No changes made.`

#### Scenario: Nothing to remove
- **WHEN** `uninstall` is run twice in a row
- **THEN** the second run logs the not-configured message and leaves the file untouched

#### Scenario: No settings file
- **WHEN** `~/.m2/settings.xml` does not exist
- **THEN** the goal succeeds, logs the no-settings message, and no file or directory is created

### Requirement: Safe removal
Before modifying the file, the goal SHALL:
1. Parse the original. If it is not well-formed settings XML, fail with `Could not update <path>: <parser message>. No changes made.`
2. Confirm that the number of located active entries equals the number of `com.sebas3261` entries the parser reports. Otherwise fail with `Could not update <path>: unable to locate the plugin group entry safely. No changes made.`
3. Parse the edited content in memory and confirm it is well-formed, no longer contains `com.sebas3261`, and yields a settings model identical to the original's except for the removed plugin group entries. Otherwise fail with the same message form.
4. Make the timestamped backup, write atomically, and preserve permissions and symlinks exactly as `setup` does.

On success it SHALL log `Removed plugin group com.sebas3261 from <path>.` and `Backup saved to <backup path>.`

#### Scenario: Malformed settings
- **WHEN** the file is not well-formed XML
- **THEN** the goal fails, and no backup or change is made

#### Scenario: Model otherwise unchanged
- **WHEN** a file with mirrors, servers, proxies, profiles, and three plugin groups is processed
- **THEN** the resulting settings model equals the original with only `com.sebas3261` removed from the plugin groups

### Requirement: Uninstall completion message
After a removal, the goal SHALL log `The ex: prefix is no longer registered. Run goals as mvn com.sebas3261:ex-maven-plugin:<version>:<goal>, or run setup again.` with the running plugin version substituted. When the effective global settings still list `com.sebas3261`, it SHALL also log `Note: com.sebas3261 is also configured in the global settings (<path>); the ex: prefix will keep working. Global settings were not modified.`

#### Scenario: Group also in global settings
- **WHEN** an administrator configured `com.sebas3261` in `<maven.home>/conf/settings.xml` and the user runs `uninstall`
- **THEN** the user file entry is removed, the global file is untouched, and the note is logged

