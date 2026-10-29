# Spec Delta

## MODIFIED Requirements

### Requirement: Copy diagnostic report
The Help menu SHALL offer "Copy Diagnostic Report", which copies to the clipboard a plain-text report with the app version, the operating system with its version and architecture, the Java runtime version, the results of the installation check and the most recent entries of the diagnostics log, and confirms that it was copied. The report SHALL follow the same redaction as the log.

#### Scenario: Copy the report
- **WHEN** the user chooses "Copy Diagnostic Report"
- **THEN** the clipboard holds the report with the version, operating system, architecture, Java runtime, installation check results and recent log entries, and the app says it was copied

#### Scenario: Empty log
- **WHEN** the user chooses "Copy Diagnostic Report" and nothing has been recorded
- **THEN** the report holds the version, system information and installation check results, and says that no errors were recorded

#### Scenario: Installation problem in the report
- **WHEN** the hooks call a `shogun` that doesn't exist and the user copies the diagnostic report
- **THEN** the report's "Installation" section has the failed hooks check with its fix, in English

#### Scenario: Paths in the installation results
- **WHEN** a project's missing local file is at `/Users/jane/projects/main-repo/local.properties` and the report is copied
- **THEN** the report's installation section contains neither `/Users/jane` nor `main-repo`

#### Scenario: Check can't run
- **WHEN** the installation check throws while the report is built
- **THEN** the report is still copied, its installation section saying the check couldn't run and why
