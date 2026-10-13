# iReadView

iReadView is an embeddable Android View Widget library with a demo host application. Its default entry point is `org.peyilo.ireadview.widget.ReaderView`, usable from XML or code.

## Project status

The current source implements the Widget, shared paged/continuous text layout, navigation transactions, seven effect presets, selection/annotations, explicit whole-book indexing, TXT/EPUB text sources and public host extension examples. The Demo includes Kotlin and Java consumers. Debug, Java compilation and Release AAR assembly succeeded for the latest changes; this does not prove runtime acceptance.

| Area | Current implementation | Remaining evidence or work |
|---|---|---|
| Navigation/layout | Shared commit path, anchors, mode changes, styles/insets and page index | Interaction, cancellation/reentry, recovery and device layouts |
| Content sources | Bounded TXT memory/disk parsing, identities/normalization; EPUB 2/3 text and navigation | Source/cancellation behavior; opt-in TXT cross-process reuse is implemented but unverified |
| Accessibility/selection | Visible and offscreen source traversal, same-chapter extension, TextContentBlock source text, shared geometry/actions | Complex local custom semantics are incomplete; TalkBack/SwitchAccess and Unicode/device behavior remain unverified |
| Effects/resources | Six presets, gestures, bitmap/cache budgets and resource ownership | Appearance, cleanup, performance and full device matrix |
| Delivery | Release AAR, local Maven repository ZIP with sources/dependency metadata, Kotlin/Java examples | Independent Kotlin/Java/XML Debug and shrunk Release host builds; runtime and complete acceptance evidence remain; online publication is not configured |

Use the [host integration guide](docs/08_HOST_INTEGRATION_GUIDE.md) for current API usage, the [implementation plan](docs/05_WIDGET_IMPLEMENTATION_PLAN.md) for actual class responsibilities, and the [requirement audit](docs/07_REQUIREMENT_IMPLEMENTATION_AUDIT.md) for the complete requirement/evidence inventory. Earlier design and progress entries are historical and must be read with their later revisions. Product decisions such as full EPUB media support or double-page mode remain explicitly pending.

## Modules

- `:app` — demo host application. It should exercise the library only through its public API.
- `:app:ireadview` — Android library containing the reusable reading component.

## Documentation

- [Product requirements](docs/01_PRODUCT_REQUIREMENTS.md)
- [Architecture and project structure](docs/02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md)
- [Implementation handoff](docs/03_IMPLEMENTATION_HANDOFF.md)
- [Widget classes and API design](docs/04_WIDGET_API_DESIGN.md)
- [Widget implementation plan](docs/05_WIDGET_IMPLEMENTATION_PLAN.md)
- [Implementation progress and design adjustments](docs/06_IMPLEMENTATION_PROGRESS.md)
- [Requirements and acceptance evidence](docs/07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)
- [Host integration guide](docs/08_HOST_INTEGRATION_GUIDE.md)
- [Release JVM API inventory](docs/09_RELEASE_JVM_API_REFERENCE.md)
- [Current page curl and release animation](docs/23_SHADER_PAGE_TURN_ROUTE.md)
- [Arbitrary View page decorations](docs/24_PAGE_DECORATIONS.md)

## Local text example

The Demo's “打开文件” button uses Android's document picker, then lets the reader choose UTF-8, UTF-16, UTF-16LE, UTF-16BE or GB18030. Cancelling either dialog keeps the current book. The host supplies a `TextBookSource` through `ReaderInput.fromUri`; the Widget does not request file permissions. Read failures offer a retry. Successful URI/encoding selections are saved in Activity state for reconstruction; cold launches restore the most recent input and its per-book ReaderSnapshot from host preferences. The Demo now offers a bounded recent-books list with source/encoding and progress hints, reopen and record removal. The host tracks newly acquired persisted read grants and retires unused grants after history removal/eviction; existing app grants remain outside its ledger. TextBookSource supports bounded memory snapshots or a host-supplied cache directory for disk chapter records. The Demo uses disk indexing and closes retired sources; EpubTextBookSource uses the same ReaderInput and a host cache directory for its private archive. Chapters are extracted on demand; the host closes the source. CSS/media presentation is not implemented; current navigation entries can be resolved into positions using extracted element IDs, and the Demo EPUB picker is implemented. EPUB entries skip charset selection and persist their source type. The catalogue offers extraction notes for metadata and chapters read so far.

## Architecture boundary

The implemented inheritance chain is `BaseReadView<L>` → `BookReadView` → `ReaderView`. The base container owns common navigation, input routing, effect lifecycle, and the host area for content presentation. Book presentation subclasses decide how content is organized. Paged reading and continuous scrolling are separate presentation implementations, explicitly switchable within the default Widget. Host applications provide content and own persistence through public APIs; internal coordinators are managed by the Widget.

Layered body viewport/container/content insets and per-block/title padding share geometry across paged and scrolling modes. Title letter spacing, XML initialization and View state restoration are supported; the Demo spacing editor uses the public style transaction. Runtime/device checks remain outstanding.

The Demo now stores cold-launch reading preferences separately from book progress: reconstructible style/font/background descriptions, input and turn options, built-in mode/effect and template choices. Activity reconstruction prioritizes View state; a per-book snapshot subsequently restores its mode/effect. Four host-owned light/dark themes use public style transactions. Only successfully applied styles are saved; runtime factories and typefaces are supplied by the host. Device restoration and visual checks remain outstanding.

The Demo typography panel now groups page/body insets, body/title size and tracking, line/paragraph spacing, heading alignment and header/footer settings. Editors patch the latest public ReaderStyle, validate inputs and close when the Activity stops. Device UI and lifecycle checks remain outstanding.

Center taps toggle a host control overlay without resizing the reader. It offers contents, typography/settings, adjacent chapters and chapter-page slider/numeric navigation. Targets are checked against the active book/chapter/layout before dispatch; lifecycle changes cancel host requests. Device touch, keyboard/accessibility and window checks remain outstanding.

The effect menu includes a host-owned wipe example implemented solely with PageTurnEffect/PageSurface. Its factory creates fresh renderers; the Widget drives progress, cancellation and commits. The host reconstructs its known custom ID from preferences/Activity state; custom instances remain outside Widget snapshots. Gesture, lifecycle and visual checks remain outstanding.

“打开书籍与演示 → 独立容器效果演示” opens a separate five-card BaseReadView<Int> host. Its adapter uses public generic presentation contracts and PageSurface, with no book/chapter model; gestures and effects share the Widget controller. Activity recreation restores committed card and known factory ID. Runtime and device checks remain outstanding.

“打开书籍与演示 → 网络章节来源演示” accepts an explicit HTTPS catalogue URL. The app-owned BookSource reads bounded catalogue JSON and same-origin UTF-8 chapters; examples are in docs/examples/network. It closes requests on stop and restores a lightweight snapshot on resume. No external service was requested during implementation; network/provider/lifecycle checks remain outstanding.

## Selection toolbar example

The Demo's input settings include a selection toolbar toggle. `DemoSelectionToolbarFactory` demonstrates the public SPI with copy, highlight and clear actions, and releases listeners in `unbind`. Call `reader.setSelectionToolbarFactory(null)` to restore the native toolbar. The host saves a choice flag and recreates the factory; Widget snapshots do not serialize factory objects. Interaction, recreation and accessibility behavior still require device validation.

## Custom content example

Open the Demo's book menu and choose the custom content block demonstration. `DemoDividerBlock` and `DemoDividerLayoutProvider` use only the public content SPI: immutable host data, worker measurement, stable anchors, retained-size estimates and shared paged/scroll drawing. The graphic exposes an accessibility description and does not pretend to be selectable source text. The separate host Activity demonstrates mode changes, navigation, retries and lifecycle cleanup. Device behavior remains unverified.

Accessibility text movement and same-chapter selection extension have an asynchronous offscreen path in both presentations. Built-in targets are resolved from source anchors on a worker; the virtual-node cursor continues after a normal navigation commit. Ordinary movement can search across empty chapters, while extension stops at chapter or unsupported-content boundaries. Whole-block custom descriptions can opt in to skip repeated fragments. Source focus remains while preparation runs. Complex local custom semantics remain incomplete; TalkBack/SwitchAccess behavior is not device-verified.

## TXT content identities

TextBookOptions.contentIdProvider accepts a public TextContentIdProvider. Override chapterId or blockId to supply host identities from immutable title/line contexts; the defaults keep chapter-N and line-N. Both memory and disk builds validate and freeze the IDs. Disk chapter reads reuse stored IDs without invoking the policy again. Policies run on IO, must be deterministic, and must return nonblank Unicode IDs unique within the book (chapters) or chapter (blocks). Stable identities still require normal revision/quote validation for positions and annotations. Runtime/source checks remain outstanding.

TextBookOptions.normalization defaults to TextNormalization.PRESERVE. TRIM_LINE_END removes trailing line whitespace; TRIM_LINE_EDGES removes whitespace at both ends. All modes retain blank lines. Normalization runs once before chapter parsing and identity callbacks, so offsets point into the emitted text. Nondefault modes receive a distinct revision to prevent reusing positions as if the text were unchanged. Memory and disk snapshots now share the same streaming chapter parser and line rules; runtime/source equivalence remains unverified.

## TXT configuration demo

Open “打开书籍与演示 → TXT 来源配置演示” to switch memory/disk storage, line whitespace normalization and sequential/content IDs. The isolated sample includes BOM, CRLF, padded text and blank lines. Its host closes sources in the background, recreates them on resume and supplies its saved lightweight snapshot. Nonblank sample lines use content hashes; repeated blank lines use ordinal IDs. This illustrates host policy and does not guarantee identities for arbitrary duplicate text. Configuration changes can trigger the Widget's normal restoration fallback. Recreation, cancellation and resource behavior remain device-unverified.

The default footer now shows chapter page progress together with the current local time, as required by FR-VIEW-01. ChromeContent.PAGE_PROGRESS_AND_TIME (XML page_progress_and_time) selects that combination explicitly; TIME and PAGE_PROGRESS retain their separate meanings. Minute updates keep the progress prefix. Runtime, font-scale and narrow-window layout checks remain outstanding. See [the requirement audit](docs/07_REQUIREMENT_IMPLEMENTATION_AUDIT.md) for current evidence and remaining scope.

TXT snapshot revision v2 combines the original byte digest with emitted chapter/block identities, types, titles and text, plus normalization and book title. Different parsing results no longer share a revision merely because their input bytes match. Memory and disk use the same incremental parser digest. This deliberately changes older raw-byte revisions; saved positions and annotations follow the existing mismatch/fallback rules. Runtime equivalence, update behavior and old-data restoration remain unverified.

### Java 宿主示例

首页“打开书籍与演示 → Java 宿主接入演示”打开 `DemoJavaActivity.java`。示例直接使用公开 Widget、内容模型、内存来源和回调接口，展示取消请求、保存位置与重建恢复；无需在 Java Activity 中使用协程。`ReaderListenerAdapter` 和 `ReadViewListenerAdapter<L>` 提供空回调实现，Java 子类只覆盖需要的事件。

示例已通过 Java 编译；运行时取消/恢复与恰好一次回调尚未据此验证。默认参数不保证全部具有 Java 重载，请参考示例中完整模型构造与实际 callback 重载。
### Library AAR

Build the library with `./gradlew :app:ireadview:assembleRelease` (`gradlew.bat` on Windows). The current artifact is `app/ireadview/build/outputs/aar/ireadview-release.aar`. The library Manifest declares minSdk21 and no Demo Activities or network permission; the Demo host owns its INTERNET permission.

Prefer the project dependency while developing: `implementation(project(":app:ireadview"))`. A standalone AAR does not bundle the Gradle dependency graph; a file-based consumer must supply Kotlin/AndroidX/coroutines dependencies from this module's build configuration and version catalogue. A local Maven repository ZIP is generated by `:app:ireadview:bundleReleaseRepository`, with development coordinate `org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT`; see the host guide section 11. Online publication is not configured. Release packaging and the standalone Maven host Kotlin/Java/XML APK build have passed; see [the example](examples/maven-host/README.md). The standalone host also assembles a code/resource-shrunk unsigned Release APK; runtime inflation/recovery and device behavior remain unverified.

The Widget's coroutine scope is internal. Host subclasses use public navigation/request interfaces and lifecycle hooks; Java module-suffixed synthetic accessors and internal packages are not supported API.
Java input factories now support `ReaderInput.fromFile/fromAsset/fromResource/fromUri/fromStream`; `openStream` declares `IOException`. TXT/EPUB provide default-option constructors for Java. The Java demo can switch between in-memory content models and a TXT stream source, retiring the previous source and restoring its chosen source type on recreation. Compilation has passed; lifecycle/source behavior remains unverified.


