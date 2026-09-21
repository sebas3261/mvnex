# Scenario coverage (task 12.1)

Every scenario in `specs/` mapped to the test that verifies it. "unit" tests run in `mvn test`; "IT" tests are maven-invoker-plugin projects under `src/it` run by `mvn verify`. Items marked *manual* or *pending* are not automated.

## dependency-add (37 scenarios)

| Requirement | Scenario | Verified by |
|---|---|---|
| Add goal parameters | Multiple dependencies | IT `add-multiple`; unit `AddUseCaseTest#mixedResultAddsInRequestOrderAndValidatesOnce` |
| Add goal parameters | Missing dependency list | unit `ParsingAndGuardTest#usageAndSingleDependencyOptions` |
| Single-dependency options | Version with two dependencies | unit `ParsingAndGuardTest#usageAndSingleDependencyOptions` |
| Scope validation | Invalid scope | unit `ParsingAndGuardTest#usageAndSingleDependencyOptions` |
| Dependency expression grammar | Search term with inline version | unit `ParsingAndGuardTest#grammar` |
| Dependency expression grammar | Coordinate detection by dot | unit `ParsingAndGuardTest#grammar` |
| Dependency expression grammar | Dotless groupId is treated as a search term (preserved quirk) | unit `ParsingAndGuardTest#dotlessGroupIsASearchTermPreservedQuirk` |
| Dependency expression grammar | Too many parts | unit `ParsingAndGuardTest#invalidFormats` |
| Dependency expression grammar | Empty part | unit `ParsingAndGuardTest#invalidFormats` |
| Dependency expression grammar | Matching duplicate version allowed | unit `ParsingAndGuardTest#versions` |
| Dependency expression grammar | Conflicting versions | unit `ParsingAndGuardTest#versions` |
| Locating the target POM | Run from a nested directory without a POM | IT `add-nested-dir`; unit `PomProjectDependencyRepositoryTest#nearestPomAboveTheExecutionRoot` |
| Locating the target POM | No POM anywhere | IT `add-no-pom` |
| Resolution and scope attachment | Scope attached | IT `add-basic`; unit `AddUseCaseTest#requestedScopeIsAttachedAfterResolution` |
| Resolution and scope attachment | Second dependency not found | unit `AddUseCaseTest#laterResolutionFailureLeavesThePomUnchanged` |
| Interactive disambiguation | Pick a candidate | unit `AddFlowTest#pickACandidate` |
| Interactive disambiguation | Search again | unit `AddFlowTest#searchAgainWithANewTerm` |
| Interactive disambiguation | Search again with empty answer | unit `AddFlowTest#searchAgainWithEmptyAnswerRepeatsTheSearch` |
| Interactive disambiguation | Input closed at the selector | unit `AddFlowTest#inputClosedAtTheSelectorCancels` |
| Non-interactive disambiguation | Ambiguous term in CI | IT `add-ambiguous-batch`; unit `AddFlowTest#batchAmbiguityFailsWithCandidatesAndHint` |
| Duplicate detection | Already declared | IT `add-duplicate` |
| Duplicate detection | Managed-only dependency is added for real | unit `PomProjectDependencyRepositoryTest#managedOnlyDependencyIsAddedForReal` |
| Duplicate detection | Commented-out dependency does not count | unit `PomProjectDependencyRepositoryTest#commentedOutDuplicateDoesNotCountAndCommentedTagsAreNotTargets` |
| Duplicate detection | Duplicated in one invocation | IT `add-multiple`; unit `AddUseCaseTest#duplicateWithinOneInvocationIsSkipped` |
| Format-preserving POM insertion | Existing dependencies section | golden `add/existing-deps` (unit) |
| Format-preserving POM insertion | Only dependencyManagement present | golden `add/dependency-management-only` (unit) |
| Format-preserving POM insertion | Project-level dependencies after dependencyManagement | unit `PomProjectDependencyRepositoryTest#projectLevelDependenciesAfterDependencyManagement` |
| Format-preserving POM insertion | Self-closing dependencies element | unit `PomProjectDependencyRepositoryTest#selfClosingDependencies` |
| Format-preserving POM insertion | No dependencies section | IT `add-basic`; golden `add/fresh` |
| Format-preserving POM insertion | CRLF POM | IT `add-crlf-pom` |
| Format-preserving POM insertion | Non-UTF-8 POM | golden `add/iso-8859-1` (unit) |
| Format-preserving POM insertion | Malformed POM | unit `PomProjectDependencyRepositoryTest#missingProjectCloseTag` |
| Post-edit validation | Valid result | IT `add-basic` |
| Post-edit validation | Invalid result | IT `add-broken-pom-subdir-workaround` |
| Add output | Mixed result | unit `AddFlowTest#mixedResultOutput` |
| Execution directory POM must be loadable | Unresolvable parent | IT `add-broken-pom` (malformed variant); unresolvable parent verified manually |
| Execution directory POM must be loadable | Workaround from a subdirectory | IT `add-broken-pom-subdir-workaround` |

## dependency-resolution (32 scenarios)

| Requirement | Scenario | Verified by |
|---|---|---|
| Resolution providers | One provider offline | unit `CompositeDependencyResolverTest#coordinateWithVersionRacesTheExistenceCheck` |
| Repository lookups go through the user's mirrors | Company mirror | IT `add-via-mirror` |
| Repository lookups go through the user's mirrors | No mirror | IT `init-wrapper` (wrapper URL); repository default is the `CENTRAL_URL` constant |
| Proxy selection | Environment proxy like the C++ tool | IT `add-env-proxy`; unit `ProxyChooserTest#environmentProxyLikeTheCppTool` |
| Proxy selection | Settings proxy wins | unit `ProxyChooserTest#settingsProxyWins` |
| Proxy selection | Lowercase precedence and default port | unit `ProxyChooserTest#lowercaseTakesPrecedenceAndPortDefaultsTo1080` |
| Proxy selection | NO_PROXY | unit `ProxyChooserTest#noProxyExcludesMatchingHostsAndSubdomains` |
| Proxy selection | SOCKS proxy in the environment | IT `add-socks-rejected`; unit `ProxyChooserTest#socksProxyIsRejectedWithTheSpecMessage` |
| Untrusted certificate hint | TLS-inspecting proxy | unit `ResolverHttpClientTest#untrustedCertificateAddsTheHint` |
| Concurrent first-result resolution | Fast provider decides the candidate set | unit `CompositeDependencyResolverTest#fastProviderDecidesTheCandidateSet` |
| Concurrent first-result resolution | SOCKS failure is not masked by deps.dev | unit `CompositeDependencyResolverTest#lookupNotPossibleAbortsEvenIfAnotherProviderSaysNotFound`; IT `add-socks-rejected` |
| Concurrent first-result resolution | All providers fail | unit `CompositeDependencyResolverTest#notFoundTakesPrecedenceOverUnavailable` |
| Search-term resolution | Exact artifactId filter | unit `ProvidersWireMockTest#freeTextFallbackAndExactArtifactIdFilter` |
| Search-term resolution | Single match with missing version | unit `CompositeDependencyResolverTest#requestedVersionIsVerifiedByTheWinningProvider` |
| Canonical version selection | Latest is a milestone | unit `CanonicalVersionSelectorTest#latestMilestoneIsSkipped` |
| Canonical version selection | Same answer for term and coordinate | unit `CompositeDependencyResolverTest#searchTermAndCoordinateGetTheSameVersion` |
| Canonical version selection | Independent of the race winner | unit `CompositeDependencyResolverTest#versionIsIndependentOfTheRaceWinner` |
| Canonical version selection | Version ordering instead of document order | unit `CanonicalVersionSelectorTest#mavenOrderingNotDocumentOrder` |
| Canonical version selection | Internal build on a company mirror is skipped | unit `CanonicalVersionSelectorTest#mirrorOnlyInternalBuildsAreSkipped` |
| Canonical version selection | Qualified public versions are kept | unit `CanonicalVersionSelectorTest#qualifiedPublicVersionsAreKept` |
| Canonical version selection | Central version list unavailable | unit `CanonicalVersionSelectorTest#confirmationFailureFallsBackToHighestStable` |
| Canonical version selection | Confirmation independent of the race winner | IT `add-canonical-version` |
| Canonical version selection | Stale search index is not used | IT `add-canonical-version` |
| Canonical version selection | No version confirmed | unit `CanonicalVersionSelectorTest#nothingConfirmedFallsBackToHighestStable` |
| Canonical version selection | Only pre-releases exist | unit `CanonicalVersionSelectorTest#onlyPreReleasesChoosesTheHighestConfirmed` |
| Candidate ranking | Known group first | unit `RankingAndOrderingTest#knownGroupFirst` |
| Pre-release detection | Markers | unit `RankingAndOrderingTest#preReleases` |
| Coordinate resolution | Explicit coordinate and version | IT `add-basic`; unit `ProvidersWireMockTest#versionConfirmedByTheGavIndex` |
| Coordinate resolution | Non-existent version | unit `ProvidersWireMockTest#missingVersion` |
| Coordinate resolution | deps.dev without a default version | unit `ProvidersWireMockTest#depsDevWithoutDefaultUsesHighestStableNotOldest` |
| Query encoding | Coordinate query encoding | unit `LookupUrlsTest#coordinateQueryEncoding` |
| HTTP error classification | Server error | unit `ProvidersWireMockTest#searchHttpErrorIsAResolutionError`; unit `CompositeDependencyResolverTest#notFoundTakesPrecedenceOverUnavailable` |

## plugin-distribution (17 scenarios)

| Requirement | Scenario | Verified by |
|---|---|---|
| Plugin coordinates and goal prefix | Fully qualified invocation | all ITs |
| Plugin coordinates and goal prefix | Prefix invocation | IT `setup-new-file`; IT `setup-existing-file` |
| Goal surface | Help goal lists goals | IT `help-smoke` |
| Goal surface | Detailed help | IT `help-smoke` |
| Help and version replace CLI flags | Version visible | IT `help-smoke` |
| Runtime baselines | Older JDK | descriptor declares `requiredJavaVersion` 17 (task 2.3); enforced by Maven's prerequisite checker — not run (no JDK 11 in CI) |
| Runtime baselines | Older Maven | manual: Maven 3.9.0 → `requires Maven version 3.9.1` |
| Runtime baselines | Floor verified | CI job `maven-floor` (Maven 3.9.1) green in run 35609718787 |
| Runtime baselines | Maven 3.9.0 is rejected | manual: Maven 3.9.0 → `requires Maven version 3.9.1` |
| Failure reporting | Error surfaced as build failure | IT `init-invalid-name` |
| Network configuration | Offline mode | IT `add-offline` |
| Unknown parameter detection | Typo fails | IT `add-unknown-param`; unit `ParsingAndGuardTest#typoFailsWithSuggestion` |
| Unknown parameter detection | Shared configuration tolerated | IT `add-shared-config` |
| Unknown parameter detection | Test overrides accepted | unit `ParsingAndGuardTest#internalAndForeignKeysAreIgnored`; every WireMock-backed IT |
| Automated verification | CI run | GitHub Actions run 35609718787: plugin job green on Linux/macOS/Windows × Java 17/21 (Windows 36/36 ITs incl. `init-windows-eol`), `maven-floor` green on Maven 3.9.1 |
| Continuous integration and release | Tag release | release `v0.2.0` published by run 35612915506 (prerelease with jar, POM, sources, javadoc); both install paths verified from the published assets |
| Native CLI removed | Repository state after the change | C++ sources, CMake build and native CI job deleted (task 12.2) |

## plugin-setup (35 scenarios)

| Requirement | Scenario | Verified by |
|---|---|---|
| Setup goal | First-time setup | IT `setup-new-file` |
| Setup goal | Runs once in a reactor | IT `setup-in-reactor` |
| Settings file location | Default location | IT `setup-new-file` |
| Settings file location | Custom settings file | IT `setup-existing-file`; IT `uninstall-roundtrip` |
| Settings file location | Custom settings file that does not exist | IT `setup-existing-file` |
| Settings file location | Symlinked settings | unit `SettingsRegistrationTest#symlinkedSettingsUpdateTheTargetAndKeepTheLink` |
| Create settings file when missing | No .m2 directory | IT `setup-new-file`; unit `SettingsRegistrationTest#createsTheFileAndMissingDirectories` |
| Detect existing registration | Already registered | unit `SettingsRegistrationTest#alreadyRegisteredIsANoOp` |
| Detect existing registration | Running setup twice | IT `setup-idempotent` |
| Detect existing registration | Group only present in a comment | fixture `settings/setup-comment-only` (unit) |
| Add pluginGroup to an existing file | Maven's default settings template | IT `setup-existing-file`; fixture `settings/setup-maven-default` |
| Add pluginGroup to an existing file | Existing groups | fixture `settings/setup-tabs` (unit) |
| Add pluginGroup to an existing file | No pluginGroups element | fixture `settings/setup-no-plugin-groups` (unit) |
| Add pluginGroup to an existing file | Single-line pluginGroups | fixture `settings/setup-single-line` (unit) |
| Add pluginGroup to an existing file | Windows line endings | fixtures `settings/setup-crlf`, `uninstall-among-groups-crlf` (unit) |
| Safe modification | Malformed settings | unit `SettingsRegistrationTest#malformedSettingsAreLeftUntouched` |
| Safe modification | Backup created | unit `SettingsRegistrationTest#setupMatchesTheExpectedBytes` |
| Safe modification | Restricted permissions preserved | unit `SettingsRegistrationTest#restrictedPermissionsArePreserved` |
| Completion hint | Hint after setup | unit `SettingsRegistrationTest#setupMatchesTheExpectedBytes` |
| Setup hint from other goals | Fully qualified init without setup | IT `add-multiple` (add goal) |
| Setup hint from other goals | Prefix already works | IT `setup-new-file` |
| Uninstall goal | Remove after setup | IT `uninstall-roundtrip` |
| Uninstall goal | Custom settings file | IT `setup-existing-file`; IT `uninstall-roundtrip` |
| Uninstall removes only this plugin's entry | Only this plugin's line is removed | fixture `settings/uninstall-among-groups` (unit) |
| Uninstall removes only this plugin's entry | Round trip with setup | IT `uninstall-roundtrip`; unit `SettingsRegistrationTest#uninstallUndoesSetupExactlyWhenPluginGroupsExisted` |
| Uninstall removes only this plugin's entry | Similar group names untouched | fixture `settings/uninstall-similar-group` (unit) |
| Uninstall removes only this plugin's entry | Commented entry untouched | fixture `settings/uninstall-commented-and-active` (unit) |
| Uninstall removes only this plugin's entry | Inline element | fixture `settings/uninstall-inline` (unit) |
| Uninstall removes only this plugin's entry | Duplicate entries | fixture `settings/uninstall-duplicates` (unit) |
| Uninstall removes only this plugin's entry | CRLF file | fixture `settings/uninstall-among-groups-crlf` (unit) |
| Uninstall no-op cases | Nothing to remove | unit `SettingsRegistrationTest#notConfiguredIsANoOp` |
| Uninstall no-op cases | No settings file | IT `uninstall-missing-file` |
| Safe removal | Malformed settings | unit `SettingsRegistrationTest#malformedSettingsAreLeftUntouched` |
| Safe removal | Model otherwise unchanged | fixture `settings/uninstall-full-settings` (unit) |
| Uninstall completion message | Group also in global settings | unit `SettingsRegistrationTest#globalRegistrationIsReported` |

## project-init (38 scenarios)

| Requirement | Scenario | Verified by |
|---|---|---|
| Init goal runs without an existing project | Run in an empty directory | IT `init-wrapper` |
| Init goal runs without an existing project | Run inside a multi-module project | IT `init-in-reactor` |
| Init parameters | Fully specified non-interactive init | unit `InitFlowTest#fullySpecifiedBatchInit` |
| Project name validation | Valid hyphenated name | unit `ProjectValidatorTest#validHyphenatedNameDerivesPackageSegment` |
| Project name validation | Uppercase rejected | unit `ProjectValidatorTest#invalidNamesUseGenericMessage`; IT `init-invalid-name` |
| Project name validation | Leading digit, double hyphen, or trailing hyphen rejected | unit `ProjectValidatorTest#invalidNamesUseGenericMessage` |
| Project name validation | Name that collapses to a keyword | unit `ProjectValidatorTest#nameCollapsingToKeywordNamesTheKeyword` |
| Java identifier segment rules | Contextual keyword rejected | unit `ProjectValidatorTest#contextualKeywordRejected` |
| Java identifier segment rules | Underscore allowed only on Java 8 | unit `ProjectValidatorTest#underscoreSegmentAllowedOnlyOnJava8` |
| GroupId validation | Empty segment | unit `ProjectValidatorTest#emptyGroupSegments` |
| GroupId validation | Uppercase segment | unit `ProjectValidatorTest#invalidGroupSegments` |
| Package validation | Invalid explicit package | unit `InitFlowTest#invalidExplicitPackageNamesThePackage` |
| Java version validation | Unsupported version | unit `ProjectValidatorTest#unsupportedJavaVersions` |
| Early validation of provided values | Bad groupId fails before prompting | unit `InitFlowTest#badGroupIdFailsBeforeAnyPrompt` |
| Early validation of provided values | Underscore segment with Java 8 chosen by prompt | unit `InitFlowTest#underscoreSegmentFailsEarlyAssumingJava21` |
| Default package derivation | Derived package | unit `ProjectValidatorTest#derivedPackage` |
| Interactive collection of missing values | All values prompted with defaults accepted | unit `InitFlowTest#allPromptedWithDefaultsAccepted`; IT `interactive-init` |
| Interactive collection of missing values | Prompted value fails final validation | unit `InitFlowTest#promptedValueFailsFinalValidation` |
| Interactive collection of missing values | Input closed | IT `interactive-init`; unit `InitFlowTest#inputClosedAtGroupIdCancels` |
| Maven Wrapper choice | Fully specified via parameters | unit `InitFlowTest#fullySpecifiedParametersSkipTheWrapperPrompt` |
| Maven Wrapper choice | Partially specified | unit `InitFlowTest#partiallySpecifiedPromptsForTheRestThenTheWrapper` |
| Maven Wrapper choice | Explicit opt-out | unit `InitFlowTest#explicitOptOutNeverAsksAboutTheWrapper` |
| Batch-mode behavior | Batch mode without name | IT `init-batch-missing-name` |
| Batch-mode behavior | Batch mode with defaults | IT `init-wrapper` |
| Existing directory protection | Directory exists | IT `init-existing-dir` |
| Generated project layout | Directory tree | unit `ProjectGeneratorTest#matchesTheCppGoldens` |
| Generated Main.java content | Main content | IT `init-wrapper`; unit `ProjectGeneratorTest#matchesTheCppGoldens` |
| Generated pom.xml content | POM content | IT `init-wrapper`; IT `init-no-wrapper` |
| Maven Wrapper generation | Default repository and running Maven version | IT `init-wrapper` |
| Maven Wrapper generation | Company mirror | IT `init-wrapper-mirror` |
| Maven Wrapper generation | MVNW_REPOURL wins over mirrors | IT `init-wrapper-repourl`; unit `ProjectGeneratorTest#propertiesMatchTheRecordedEnvironments` |
| Maven Wrapper generation | Mirror of a specific repository is ignored | IT `init-wrapper-mirror-central-only` |
| Maven Wrapper generation | Windows line separators | IT `init-windows-eol` (Windows CI); unit `ProjectGeneratorTest#windowsLineSeparators` |
| Maven Wrapper generation | Wrapper files present and runnable | IT `init-wrapper` (runs `./mvnw -v` on Unix) |
| Maven Wrapper generation | Wrapper skipped | IT `init-no-wrapper` |
| Init output | Success output without wrapper | IT `init-no-wrapper`; unit `InitFlowTest#summaryAndSuccessOutput` |
| Execution directory POM must be loadable | Broken POM in the execution directory | IT `init-broken-pom` |
| Execution directory POM must be loadable | Workaround | every init IT runs without a pom.xml |
