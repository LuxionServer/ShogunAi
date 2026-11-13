## ADDED Requirements

### Requirement: Forget an unavailable multirepo from the command line
`shogun multirepo trust --revoke [folder]` SHALL forget a known multirepo whose folder is the given folder (or the current directory) and whose declaration can't be read, as the desktop app's "Remove" does, without reading the declaration and without looking for a declaration in enclosing folders. It SHALL say why the declaration can't be read, and SHALL leave whatever is in the folder as it is.

#### Scenario: Folder that can't be reached
- **WHEN** the known multirepo `~/projects/product` is on a volume that isn't mounted and the user runs `shogun multirepo trust ~/projects/product --revoke`
- **THEN** the multirepo is no longer known on this machine and the command says it forgot it because its folder can't be reached

#### Scenario: Declaration gone
- **WHEN** the known multirepo folder `~/projects/product` no longer has `.shogunai-multirepo.json` and the user runs `shogun multirepo trust --revoke` in it
- **THEN** the multirepo is no longer known on this machine, even when an enclosing folder holds another multirepo, which stays known and approved

#### Scenario: Invalid declaration
- **WHEN** the known multirepo `~/projects/product` has a `.shogunai-multirepo.json` that isn't valid JSON and the user runs `shogun multirepo trust ~/projects/product --revoke`
- **THEN** the multirepo is no longer known, the invalid file is left untouched and the command names the problem found in it

#### Scenario: JSON output
- **WHEN** the user runs `shogun multirepo trust ~/projects/product --revoke --json` for a known multirepo whose folder can't be reached
- **THEN** the output holds its folder, its declaration path, `approved` false, `changed` true and the reason `folder-unreachable`

#### Scenario: Unknown folder
- **WHEN** the user runs `shogun multirepo trust ~/projects/other --revoke` and no declaration can be read there or above
- **THEN** the command says no multirepo was found, exits with code 1 and changes nothing
