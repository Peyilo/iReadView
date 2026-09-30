# iRead Implementation Handoff

## Project intent

Build an embeddable Android reading component. The Demo App is a real host/example, not the owner of core reader behavior. Host applications provide and receive chapter/content, reading-position, bookmark, and highlight data through explicit public APIs.

## Read these documents first

1. [Product requirements](01_PRODUCT_REQUIREMENTS.md) — behavioral baseline and acceptance scenarios.
2. [Architecture and project structure](02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md) — inheritance responsibilities, the scrolling decision, module tree, and delivery order.

## Confirmed architectural direction

- Keep the legacy project’s nested module organization: Demo `:app`, library `:app:libreadview`.
- The base reader container provides shared container/navigation behavior and hosts pluggable page-turn effects. It does not prescribe chapter content layout.
- Reading subclasses choose how content is presented.
- Paged presentation and continuous scrolling are different presentation strategies. Scrolling owns cross-chapter continuity and visible-content anchoring; it is not a page-turn effect.
- Bookmark and highlight data belongs to the host. The library accepts, displays, edits through reader interaction, and returns changes using stable content anchors; it does not require a persistence backend.
- Common customization is configuration/delegation; structural/content-flow customization is supported through inheritance.
- The current directory is a scaffold only. No reader feature or production API has been implemented.

## Scope discipline

- Preserve existing behavior listed as baseline in the requirements, but do not copy legacy class structure or rendering algorithms as a constraint.
- Treat incomplete EPUB extraction, image rendering, double-page mode, and persistent annotation storage as unresolved product scope unless the requirements are updated.
- Do not let Demo code access library internals. If the Demo cannot exercise a capability through the public API, the API boundary is incomplete.
- Agree measurable performance targets and target devices before claiming performance improvements.

## Existing local change to preserve

The legacy workspace at `F:\User\Documents\ChatGPT\libreadview` had a pre-existing uncommitted change in `app/libreadview/src/main/java/org/peyilo/libreadview/basic/page/ReadBody.kt` when the work began. Do not overwrite or fold it into iRead without the user's explicit direction.

## Scaffold validation

The initial Android app assembles successfully with `:app:assembleDebug` on the configured local Android SDK. No test suite was added or run. This validates only the Gradle/module scaffold, not reader functionality.

## Suggested first implementation milestone

Design and document the public immutable models and host-facing APIs for book/content input, reading position, bookmark, highlight, style, and custom effects. Then implement the base container and a minimal paged presentation, and prove the contract from the Demo host before adding all effects and the scroll presentation.
