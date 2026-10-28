# Design

## Context

See proposal.md for the motivation. The desktop UI lives in `desktopApp/.../ui`. Forms use `OutlinedTextField` (about 30 call sites, plus the anchor of `DropdownSelector`), dialogs use `AlertDialog` or `BasicAlertDialog` with a footer written per screen, loading is shown with `InlineProgress`, empty states are written per screen (`AppShell.EmptyState`, `NothingActive`, `NoMatch`), and notices are stacked one `Notice` per event (`MachineResources.notices`). `WithTooltip` already works around a disabled button (it listens on its own wrapper, not the child), as "Clean up group" shows with its blocker. There are no `heading()` semantics anywhere.

Three changes of the epic run in parallel. `ui-v2-tokens` owns `ui/theme/**`, `Spacing.kt`, `Buttons.kt`, `Interactive.kt`, `StatusChip.kt` and the color and type substitutions on screens; `ui-v2-status-bar` owns the sidebar and the Machine summary; `orca-integration` (in progress) reserves the worktree detail pane, the agent picker, the open actions, the row context menu and the removal and cleanup dialogs. This change only adds components and adopts them on the remaining screens.

Design v2 rules applied (section 04 of the designer's document): "Every asynchronous surface defines its four states", "A disabled control shows and explains why", "Notices are grouped and summarized", "Trivial confirmations happen in place", "Titles are declared as titles", "One main action per view, in ink" (only the dialog's confirm is primary) and "No `Color(0x…)` or loose `.dp` on screens" (component-internal sizes are named constants).

## Goals / Non-Goals

**Goals:**
- Each new component lives in `ui/components`, uses only `MaterialTheme` and `ShogunTheme` roles, and keeps its sizes in named private constants, so the `ui-v2-tokens` restyle reaches it with no edit beyond swapping those constants for tokens.
- Screens change their call sites, not their behavior: same callbacks, same validation, same strings except the new ones.
- The reasons for disabled controls come from the ViewModels as typed values, so they can be unit-tested and translated.

**Non-Goals:**
- Restyling buttons, colors, type or radii (tokens' work). Buttons are used by their current names.
- `ShogunMenu`, `SplitButton`, `ActionBar`, `InspectorSection`, `DataRow` (later changes).
- Redesigning the right-hand summary panel beyond its loading state.
- The command palette: it's a popup with a search field and a list, not a confirmation dialog, and belongs to the action catalog's area.

## Decisions

### `Field` built on `BasicTextField` with a decoration box

`Field(value, onValueChange, label, modifier, help, error, placeholder, enabled, readOnly, singleLine, keyboardOptions, keyboardActions, trailing)` draws a `labelMedium` label above, a 32 dp input bordered with `outline` (`error` while there's an error, `primary` while focused) on the `extraSmall` shape, and `bodySmall` help or error text below. It calls `trackTextInputFocus()` itself, so screens drop that modifier. `PathField` adds a "Browse…" `SecondaryButton` after the input. `DropdownSelector` keeps its API and its menu, but its anchor becomes a read-only `Field` with the label above, which turns every dropdown in a form into the dropdown variant without touching its call sites. The error is passed as a string (or null), replacing `isError` plus `supportingText`, because every call site already has the message.

Alternative considered: `OutlinedTextField` with a fixed height and no label. Its decoration has a minimum height of 56 dp and its padding can't go down to 32 dp without clipping the text, so a custom decoration is simpler and smaller.

### `ShogunDialog` on `BasicAlertDialog`

`ShogunDialog(title, onDismissRequest, confirm, modifier, dismiss, size, notices, scrollable, content)` wraps `BasicAlertDialog` with `usePlatformDefaultWidth = false`, so it keeps Material's scrim, Escape and click-outside behavior. Inside, a `Surface` at the elevation level 2 described by the design (`surfaceContainerHigh`, a 1 dp `outlineVariant` border and a small shadow, with the dialog radius of 12 dp) holds a `SectionTitle` declared as a heading, the body in a `Column` that takes the remaining height and scrolls (`scrollable = false` for bodies that already have a lazy list), the optional `notices` slot, and the footer `Row` with `dismiss` then `confirm` aligned to the end. `DialogSize` is `Small(400)`, `Medium(520)` and `Large(680)`. The caller still chooses the buttons (`PrimaryButton` or `DangerButton` to confirm, `NeutralTextButton` to dismiss), because the destructive variant and the disabled state differ per dialog.

Alternative considered: `DialogWindow` (a separate OS window). It breaks the in-window scrim, the theme's composition locals and the existing headless UI tests.

### Titles declare their heading

`ViewTitle`, `SectionTitle` and `GroupTitle` are thin `Text` wrappers that add `Modifier.semantics { heading() }`. `ViewTitle` keeps today's view title style (`headlineSmall`), `SectionTitle` the form section style (`titleMedium`) and `GroupTitle` the group header style (`titleSmall`); tokens swaps the styles inside them. `SectionCard`, `ShogunDialog`, `MachineViewFrame`, the worktree view header and the list's group headers use them. The detail pane's section titles wait for `orca-integration` (reserved file) and later for `InspectorSection`.

### `DisabledReason` wraps the control

`DisabledReason(reason: String?, content)` renders `content` alone when `reason` is null, and otherwise wraps it in `WithTooltip(reason)` and sets the reason as the wrapper's `stateDescription`. Screens pass `reason` only when the control is disabled. The reasons are typed in the ViewModels: `WorktreeBoard.cleanUpBlock(projectId): CleanupBlock?` (`CLEANING_UP`, `NOTHING_TO_CLEAN_UP`) with `canCleanUp` defined as its absence, the header adding `NEEDS_REVIEW` for an untrusted multirepo; `MachineDaemon.stopBlock(all): DaemonStopBlock?` (`BUSY`, `STATE_UNKNOWN`, `GRADLE_BUSY` for Kotlin daemons) with `isStoppable` defined as its absence, and `MachineResources.stopIdleBlock` (`STOPPING`, `NONE_IDLE`). "Clean up group" already has its blocker; it moves to `DisabledReason` and gains `CLEANING_UP`.

Alternative considered: a `Modifier.disabledReason()`. A modifier can't wrap the content in `TooltipArea`, which is a composable.

### Notice grouping is a pure function

`groupNotices(items, key)` returns `NoticeGroup(key, items)` in order of first appearance, kept in `components/NoticeGroups.kt` with no Compose dependency so it's unit-tested. `GroupedNotice(kind, summary, details, onDismiss)` draws one `Notice` whose message is the summary, followed by the count ("×3") and a "Details" disclosure listing `details` when there's more than one. Machine builds the groups from its `PoolNotice`s keyed by the content type and resource kind: trims are summarized as the sum stopped within the last limit (`trimmedNotice(sum, limit)`), releases as "N … were released to make room" (new `releasedSummary`), and a single notice keeps its current text. The view shows one banner per resource with the group summaries one per line, and its dismiss calls a new `MachineResources.dismissNotices(resource)`. The manual "Stop idle daemons" report stays its own notice: it's the result of the action the user just took, replaced on each stop, so it never stacks.

### Four-state components

`EmptyState(icon, message, modifier, title, action, link)` centers an icon, an optional title (a `ViewTitle`-level heading only for the first-launch screen, a plain sentence otherwise), the sentence, a `PrimaryButton` or `SecondaryButton` action and a `NeutralTextButton` link. `ErrorState(message, actionLabel, onAction, modifier, technicalDetails)` shows the danger icon, the sentence, a `SecondaryButton` and the existing "Technical details" disclosure, extracted from `ErrorNotice` into a shared internal composable that takes plain text. `SkeletonRows(count, modifier)` draws rows of `surfaceContainerHigh` bars at the row height of a two-line list row, with `clearAndSetSemantics { contentDescription = strings.loading }`, and no animation (the design keeps motion low).

Adoption: a project group's `ProjectLoadState.Loading` (first read only, since `hasLoaded` already keeps the rows on reload) shows `SkeletonRows`; `NothingActive`, `NoMatch` and the first-launch screen become `EmptyState`; the summary panel shows `SkeletonRows` until every shown project has loaded once; Compose's `MachineLoad.Unavailable` becomes an `ErrorState` whose action calls a new `MachineResources.retryComposeProjects()` and whose details hold `composeUnavailableReason`.

### `InlineConfirm` as a state holder plus two controls

`rememberInlineConfirm(durationMillis = 1500)` returns an object with `confirmed` and `confirm()`, reset by a `LaunchedEffect`. `InlineConfirmButton(label, onClick)` (a `SecondaryButton`) and `InlineConfirmIconAction(icon, label, onClick)` show ✓ and `strings.copied` while confirmed. Adopted in the simulator snippet copy and the "Copy technical details" action; the path copy in the detail and in the row wait for `orca-integration`.

### Provisional constants until tokens merges

`FieldHeight = 32.dp`, `FieldHorizontalPadding = 10.dp`, `DialogShape = RoundedCornerShape(12.dp)`, `DialogShadow`, `RowPadding = 12.dp` (the future `Spacing.smd`) and `SkeletonRowHeight = 48.dp` are private constants in their components. When `ui-v2-tokens` merges into the epic, this branch merges the epic and swaps them for its tokens (task 9).

## Risks / Trade-offs

- [Merge conflicts with `ui-v2-tokens` on the same lines (`style = headlineSmall` in the worktree header, `bodySmall` inside buttons)] → keep edits to call sites minimal, merge the epic as soon as tokens lands, and resolve each conflict towards the component plus tokens' style.
- [Conflicts with `ui-v2-status-bar` in `AppShell.kt`] → touch only `EmptyState`, `DiscardChangesDialog` and `RemoveProjectDialog`, at the end of the file.
- [A custom field loses Material behavior (selection handles, IME padding)] → `BasicTextField` keeps text editing, selection and IME; the UI tests type into fields through the same semantics as before.
- [Tooltips don't show on keyboard focus of a disabled control, since disabled controls can't take focus] → the reason is also exposed as the state description, so a screen reader announces it.
- [Dialog footers lose Material's `FlowRow` wrapping of long actions] → the "Button labels stay on one line" UI tests run on the migrated dialogs in Spanish at the narrowest width.

## Migration Plan

No data or settings change. The change merges into `epic/ui-redesign-v2` after `ui-v2-tokens`; rollback is reverting its merge commit.

## Open Questions

- Whether the deferred dialogs (removal, cleanup, choose terminal) and the detail pane's copy action are migrated in a follow-up of this change or inside `ui-v2-action-catalog`, which also touches them; either way they're listed as pending in `tasks.md`.
