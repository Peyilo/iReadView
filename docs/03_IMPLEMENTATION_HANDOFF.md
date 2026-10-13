# iReadView Implementation Handoff

## Project intent

Build an embeddable Android View Widget library, distributed as an AAR. The default entry point is org.peyilo.ireadview.widget.ReaderView, usable in XML or code. The Demo is a real host application and provides content, configuration and persistence through public APIs.

## Read these documents first

1. [Product requirements](01_PRODUCT_REQUIREMENTS.md) — behavioral baseline and acceptance scenarios.
2. [Architecture](02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md) — inheritance, responsibilities and project structure.
3. [Widget API design](04_WIDGET_API_DESIGN.md) — target signatures, results, ownership and extension contracts.
4. [Implementation plan](05_WIDGET_IMPLEMENTATION_PLAN.md) — layout/rendering, cancellation and resource ownership.
5. [Implementation progress](06_IMPLEMENTATION_PROGRESS.md) — shipped scope, design adjustments and verification evidence. Target API listings do not prove implemented behavior.

6. [Requirement audit](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md) — full requirement and missing acceptance evidence.
7. [Host integration](08_HOST_INTEGRATION_GUIDE.md) — current usage contracts.
8. [Release JVM inventory](09_RELEASE_JVM_API_REFERENCE.md) — artifact signatures, including unsupported compiler members.

## Confirmed architectural direction

- Keep the nested modules: Demo :app and library :app:ireadview.
- The inheritance chain is BaseReadView<L> → BookReadView → ReaderView. The generic base does not depend on book parsing.
- BaseReadView owns navigation commits, input routing and lifecycle. BookReadView owns book operations and configuration. Presentations determine content organization.
- Paged and continuous scrolling are separate presentation strategies. Scrolling owns chapter continuity and visible anchoring; it is not a page-turn effect.
- Hosts use suspend or callback/request-handle operations on the Widget; they do not construct internal sessions, controllers or coordinators.
- Persist stable book/chapter/block anchors. Page numbers depend on layout. Whole-book indexing is explicit and cancellable; unknown totals remain unknown.
- Bookmark/highlight data and persistence belong to the host. The library validates, displays and returns changes through public APIs.
- Common customization uses configuration/delegation; structural changes use presentation inheritance. Effects use read-only surfaces; the container owns animation and commit/cancel decisions.

## Current implementation and verification

The Widget foundation, shared StaticLayout geometry, paged and continuous presentations, transactional mode switching, BookSource/MemoryBookSource, navigation, per-chapter loading/invalidation/retry, catalogue reload, annotations and selection now exist. Both presentations provide same-chapter selection edge extension. Explicit cancellable whole-book indexing, progress and global page navigation also exist. The effect SPI, shared animation clock and none/slide/cover/iBookSlide presets are implemented with cancellation and budgeted bitmap leases. Cross-chapter selection is unsupported.

The Demo uses public APIs only. It persists annotation snapshots as JSON in SharedPreferences, restores them on book opening, supports highlight color/delete actions and exposes reading mode switching. The library does not depend on that storage format. Only validated annotations with supported geometry are drawn.

The initial scaffold assembled successfully. Unit and Android integration suites were added later; document 06 separates historical results from later build-only changes. Recent scrolling, recovery, annotations and selection interactions have not been verified on devices. Build success does not prove runtime behavior or performance.

## Scope discipline and next milestones

Continue file sources, remaining input policies, customization and accessibility according to documents 04/05. All six effect factories now have initial implementations; curl geometry, reduced-resolution snapshots and effect cancellation and the new horizontal gesture commit/reset path still need device verification. Keep one position commit source and keep API/progress documentation synchronized. Preserve the behavioral baseline without copying legacy class structure as a constraint. Whole-book index interactions also need device verification.

Incomplete EPUB extraction, images, double-page mode and optional persistent storage adapters remain unresolved product scope unless requirements are updated. Agree target devices and measurable performance criteria before claiming improvements. No Demo imports from library internals.

## Existing local change to preserve

The legacy reference project had a pre-existing uncommitted change in its ReadBody.kt. Preserve that external work; do not overwrite or copy it into this project without explicit user direction.

## Git baseline

The repository was initialized on 2026-09-30. Initial scaffold/documentation commit: cf96c3b (chore: initialize iRead project repository). Design documents authored afterward are not evidence of implemented APIs or approval of unresolved scope.


