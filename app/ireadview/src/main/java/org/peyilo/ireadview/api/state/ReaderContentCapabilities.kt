package org.peyilo.ireadview.api.state

/** Unknown means a custom engine has not declared this feature, never an inferred promise. */
enum class ReaderFeatureSupport { Supported, Unsupported, Unknown }
enum class ReaderSelectionModel { Text, Document, None, Unknown }

/** Current engine features, not operation readiness or a guarantee about every content node.
 * Read ReaderState.phase/navigationState and the operation result for readiness and failures.
 * Custom engines declare selection/link/annotation support through BookPresentation.
 * Widget derives index/effect support from the actual source, engine and committed mode.
 */
data class ReaderContentCapabilities @JvmOverloads constructor(
    val selectionModel: ReaderSelectionModel = ReaderSelectionModel.Unknown,
    val links: ReaderFeatureSupport = ReaderFeatureSupport.Unknown,
    val rangedAnnotations: ReaderFeatureSupport = ReaderFeatureSupport.Unknown,
    val wholeBookPageIndex: ReaderFeatureSupport = ReaderFeatureSupport.Unknown,
    val pageTurnEffects: ReaderFeatureSupport = ReaderFeatureSupport.Unknown
)
