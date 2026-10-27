# Page curl core

Portable Kotlin transcription of the verified iOS12.5.7 (16H81) page-curl algorithms. Production sources use Kotlin standard types, arithmetic and managed arrays only. There are no Android, JVM library, Widget, EPUB, JNI, C/C++, firmware, Unicorn or WebAssembly dependencies in this module's source code. The current Gradle target produces a JVM JAR for Android consumption.

| Package | Responsibility |
| --- | --- |
| `model` | Logical points, rectangles, six binding positions, physical directions and filter values |
| `gesture` | Pan/tap recognizers, original sampling, hysteresis, direction and release decisions |
| `control` | Touch-to-filter conversion, endpoints, durations and independent CA timing tracks |
| `math` | Recovered math polynomials, software fused operations and half conversion |
| `layout` | Page sources, recto/verso passes, mirrors and underlay order |
| `transition` | Optional original compatibility, scheduling, layer events and FIFO cleanup |
| `mesh` | Typed material inputs and draw packets |
| `mesh/kernel` | Source-mapped numeric implementation and reusable managed scratch workspace |

`CurlMesh` is single-owner and reusable. Returned draw batches own their buffers and survive subsequent builds. Positions use logical points; UVs use source pixel coordinates; colors are premultiplied half4. A platform renderer consumes the packets without knowing book navigation or gestures. A book host maps `CurlPageSource` to its own page/snapshot objects.

The generated kernel contains ordinary Kotlin expressions and fixed algorithm control-flow blocks. Its block number is a branch label within this one function; it does not represent an ARM program counter. No instruction bytes, opcode decoder or CPU emulator ship. Vector lanes and little-endian scratch offsets retain the C transcription's layout for traceability and numerical parity. UI or Widget code must not depend on these internal details.

Normal builds and tests need only Gradle/JDK. The checked-in compressed test fixtures contain inputs, expected numeric values, indices and hashes of shader-consumed attributes, not firmware or native libraries. The 64-byte vertex record has unused padding, which is deliberately excluded from parity hashes; position, UV and half-color bytes are compared exactly.

Optional regeneration against the research C port:

```powershell
python tools/Port-CurlKernel.py --source <research>/cport
python tools/Generate-KotlinCurlFixtures.py --source <research>/cport
./gradlew.bat :app:pagecurl-core:test
```

The generator needs Python/pycparser only when regenerating the checked-in Kotlin. Fixture generation additionally needs the previously verified C reference DLL; these tools are never part of the Android build or runtime. See `docs/IOS_CURL_KOTLIN.md` for Android integration, single-page scope and spread extension APIs.
