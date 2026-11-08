## ADDED Requirements

### Requirement: Rail tiles tell sibling projects apart
In the compact rail, each project's tile SHALL show two uppercase letters: the initials of its first two words when its name has several words, otherwise its first two letters. When two projects at the same level of the sidebar would show the same letters, each SHALL use its first letter followed by a later letter of its name that the others don't have, when there is one. The tile's tooltip SHALL keep naming the project.

#### Scenario: Different first letters
- **WHEN** the rail shows the projects `api` and `web`
- **THEN** their tiles read `AP` and `WE`

#### Scenario: Same first two letters
- **WHEN** the rail shows the projects `app` and `api`
- **THEN** their tiles read `AP` and `AI`

#### Scenario: Several words
- **WHEN** the rail shows the project `main-repo`
- **THEN** its tile reads `MR`
