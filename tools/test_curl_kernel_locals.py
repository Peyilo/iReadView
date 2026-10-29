import unittest
import tempfile
from pathlib import Path
from CurlKernelLocals import Binding, analyze, live_storage, observable_writes, clean_parentheses, shared_reads, shared_writes
from CurlKernelDeadCode import clean_scope, remove_unreachable_steps
from CurlKernelAlgorithms import replace_sample_merges, replace_shadow_range_model, replace_shadow_segments, replace_shadow_profile, replace_strip_section_calls, replace_curl_sample_calls, replace_front_strips, replace_penumbra


class PackedLaneLivenessTest(unittest.TestCase):
    def writes(self, replacement):
        values = {'temporary': Binding('temporary', 'Double', 0, 'double', ''),
                  'vector': Binding('vector', 'KernelVector', 0, 'vector', '')}
        body = ('\n    temporary = readDouble(1024L)\n    ' + replacement +
                '\n    vector.store(memory, 2048L)\n    return KernelStep.Complete\n')
        events, writes = analyze(body, values, {'temporary'})
        return next(iter(writes)), observable_writes(events, 0)

    def test_float_overwrite_still_needs_the_other_half_of_the_double(self):
        position, keep = self.writes('vector.float(0, 1f)')
        self.assertIn(position, keep)

    def test_double_overwrite_makes_the_old_local_storage_unobservable(self):
        position, keep = self.writes('vector.double(0, 1.0)')
        self.assertNotIn(position, keep)

    def test_a_loop_and_either_branch_may_observe_a_lane(self):
        events = {'entry': [], 'loop': [], 'draw': [(0, 'read', 0xf0, 'vector', 0)], 'skip': []}
        graph = {'entry': ['loop'], 'loop': ['loop', 'draw', 'skip'], 'draw': [], 'skip': []}
        self.assertEqual(0xf0, live_storage(events, graph)['entry'])

    def test_parenthesis_cleanup_keeps_multiplication_grouping_and_float_rounding(self):
        source = 'fma(a, ((b + c) * d).toFloat(), e)'
        self.assertEqual(source, clean_parentheses(source))
        self.assertEqual('readDouble(base + 8L)', clean_parentheses('readDouble((base + 8L))'))

    def test_dead_local_removal_keeps_unknown_calls_and_observable_lane_writes(self):
        source = '\n    val dead = 1L + 2L\n    val result = acquireResource()\n    val lane = retainDouble(0, value)\n'
        actual, count = clean_scope(source)
        self.assertEqual(2, count)
        self.assertNotIn('val dead', actual)
        self.assertIn('val result = acquireResource()', actual)
        self.assertIn('retainDouble(0, value)', actual)
        self.assertNotIn('val lane', actual)

    def test_dead_copy_within_a_nested_block_does_not_remove_its_source_mutation(self):
        source = '\n    run {\n        val spare = source.copy()\n        vector.float(0, 1f)\n    }\n'
        actual, count = clean_scope(source)
        self.assertEqual(1, count)
        self.assertNotIn('copy()', actual)
        self.assertIn('vector.float(0, 1f)', actual)

    def test_helper_outputs_do_not_keep_prior_slot_values_alive(self):
        values = {'input': Binding('input', 'Long', 0, 'integer', ''),
                  'output': Binding('output', 'Long', 1, 'integer', '')}
        self.assertEqual(values['input'].bits(), shared_reads('    output = input + 1L\n', values))
        self.assertEqual(values['output'].bits(), shared_reads('    output = output + 1L\n', values))

    def test_helper_vector_inputs_track_getter_lanes_without_reading_setters(self):
        values={'vector':Binding('vector','KernelVector',8,'vector','')}
        low=Binding('vector','Double',8,'double','').bits()
        high=Binding('vector','Double',8,'double','').bits(1)
        self.assertEqual(low,shared_reads('    consume(vector.double(0))\n',values))
        self.assertEqual(high,shared_reads('    consume(vector.longBits(1))\n',values))
        self.assertEqual(0,shared_reads('    vector.double(0, 1.0)\n',values))
        self.assertEqual(0,shared_reads('    vector.load(memory, 100L)\n',values))

    def test_only_straight_line_helpers_kill_previous_scalar_outputs(self):
        values = {'output': Binding('output', 'Long', 1, 'integer', '')}
        mask = values['output'].bits()
        self.assertEqual(mask, shared_writes('    output = 1L\n', values))
        self.assertEqual(0, shared_writes('    if (condition) {\n        output = 1L\n    }\n', values))
        self.assertEqual(0, shared_writes('    while (condition) {\n        output = 1L\n    }\n', values))
        body = '\n    output = 0L\n    helper()\n    consume(output)\n'
        events, _ = analyze(body, values, {}, {'helper': 0}, {'helper': mask})
        self.assertNotIn(5, observable_writes(events, 0))
        events, _ = analyze(body.replace('    helper()', '        helper()'), values, {},
                            {'helper': 0}, {'helper': mask})
        self.assertIn(5, observable_writes(events, 0))


class SampleMergeRecoveryTest(unittest.TestCase):
    def test_front_recovery_has_only_a_vertex_output_stage_and_is_repeatable(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            path=directory/'KernelFaceSampling.kt'
            path.write_text('''// Reference step 180: old front entry
internal fun CurlMeshKernel.inspectFrontFace(): KernelStep {
    return KernelStep.InspectFrontSampleRange
}
// Reference step 181: old front branch
internal fun CurlMeshKernel.inspectFrontSampleRange(): KernelStep {
    return KernelStep.PrepareDrawState
}
''',encoding='utf-8')
            (directory/'KernelFlow.kt').write_text('''    InspectFrontSampleRange,
    KernelStep.InspectFrontSampleRange -> inspectFrontSampleRange()
''',encoding='utf-8')
            self.assertEqual(['inspectFrontSampleRange'],replace_front_strips(directory))
            text=path.read_text(encoding='utf-8')
            self.assertIn('buildFrontStrips()',text)
            self.assertNotIn('InspectFrontSampleRange',text)
            self.assertNotIn('InspectFrontSampleRange',(directory/'KernelFlow.kt').read_text(encoding='utf-8'))
            self.assertEqual([],replace_front_strips(directory))
            self.assertEqual(text,path.read_text(encoding='utf-8'))

    def test_strip_section_recovery_removes_the_reference_packet_and_adapter(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            face=directory/'KernelFaceSampling.kt'
            face.write_text('''    faceColor2.packHalfColorFrom(faceValue186)
    coordinate = oldVectorSlotReuse()
    writeFloat(scratchBase, faceValue187)
    writeFloat(scratchBase + 4L, faceValue188)
    writeFloat(scratchBase + 8L, faceValue189)
    writeFloat((scratchBase + 8L) + 4L, faceValue190)
    writeDouble((scratchBase + 584L), faceColor2.double(0))
    appendSurfaceStrip(center, low, high, index, colorAddress, x, y, u, v, dx, dy, du, dv, scratchBase)
''',encoding='utf-8')
            primitive=directory/'KernelPrimitives.kt'
            primitive.write_text('internal fun CurlMeshKernel.appendSurfaceStrip() { oldAdapter() }\n',encoding='utf-8')
            replace_strip_section_calls(directory)
            text=face.read_text(encoding='utf-8')
            self.assertIn('appendStripSection(',text)
            self.assertIn('faceColor2.longBits(0)',text)
            self.assertLess(text.index('val stripColor ='),text.index('coordinate ='))
            self.assertNotIn('writeFloat',text)
            self.assertNotIn('writeDouble',text)
            self.assertNotIn('oldAdapter',primitive.read_text(encoding='utf-8'))
            replace_strip_section_calls(directory)
            self.assertEqual(text,face.read_text(encoding='utf-8'))

    def test_only_known_unused_sine_output_addresses_are_removed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            face=directory/'KernelFaceSampling.kt'
            (directory/'KernelPenumbra.kt').write_text('',encoding='utf-8')
            face.write_text('''    val sineOutputAddress3: Long = 0L
    result = curlDisplacement(x, center, radius, sineOutputAddress3, lo, hi)
''',encoding='utf-8')
            replace_curl_sample_calls(directory)
            actual=face.read_text(encoding='utf-8')
            self.assertNotIn('radius, sineOutputAddress3',actual)
            replace_curl_sample_calls(directory)
            self.assertEqual(actual,face.read_text(encoding='utf-8'))
            face.write_text('    result = curlDisplacement(x, center, radius, 12L, lo, hi)\n',encoding='utf-8')
            with self.assertRaises(AssertionError):replace_curl_sample_calls(directory)

    def test_shadow_profile_recovery_keeps_reciprocals_from_the_raw_block(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory/'KernelShadowTopology.kt').write_text('',encoding='utf-8')
            source = '''    compareFloatingPoint(shadowValue65, shadowValue66)
    penumbraValue11 = oldLimit
    penumbraValue12 = 1.0
    penumbraValue13 = penumbraValue12 / surfaceValue101.double(0)
    writeDouble((scratchBase + 424L), penumbraValue13)
    writeDouble((scratchBase + 416L), penumbraValue11 - readDouble((scratchBase + 568L)))
    writeDouble((scratchBase + 400L), penumbraValue12 / penumbraValue7)
'''
            path = directory/'KernelPenumbra.kt'
            path.write_text(source,encoding='utf-8')
            replace_shadow_profile(directory)
            actual = path.read_text(encoding='utf-8')
            self.assertIn('writeDouble((scratchBase + 424L), (1.0 / surfaceValue101.double(0)))',actual)
            self.assertIn('writeDouble((scratchBase + 400L), 1.0 / penumbraValue7)',actual)
            self.assertNotIn('penumbraValue12',actual)
            self.assertNotIn('penumbraValue13',actual)

    def test_shadow_range_model_preserves_setup_and_is_repeatable(self):
        source = '''// Reference step 59: joined shadow stages
internal fun CurlMeshKernel.prepareShadowBreakpoints(): KernelStep {
    shadowValue23.double(0, readDouble(scratchBase + 520L))
    if ((surfaceAddress22 and KernelLayout.WORD_MASK) == 0L || !collectShadowSamples()) {
        return KernelStep.InspectFrontFace
    }
    mergeShadowSamples()
    buildShadowSegments()
    shadowProfile = KernelShadowProfile.forTime(readDouble(shadowAddress))
    writeDouble(scratchBase + 568L, shadowProfile.innerLimit)
    updateSubtractionFlags(
        shadowIndex13, 0L, 32L
    )
    return if (zeroResult) KernelStep.PrepareEmptyPenumbra else KernelStep.PrepareShadowCrossSections
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = directory/'KernelShadowTopology.kt'
            path.write_text(source, encoding='utf-8')
            replace_shadow_range_model(directory)
            actual = path.read_text(encoding='utf-8')
            self.assertIn('val shadowRange =', actual)
            self.assertIn('mergeShadowSamples(shadowRange)', actual)
            self.assertIn('shadowProfile = KernelShadowProfile.forTime', actual)
            self.assertNotIn('PrepareEmptyPenumbra', actual)
            self.assertNotIn('shadowIndex13', actual)
            replace_shadow_range_model(directory)
            self.assertEqual(actual, path.read_text(encoding='utf-8'))

    def test_shadow_segment_recovery_preserves_a_joined_successor(self):
        source = '''// Reference step 75: joined merge and topology
internal fun CurlMeshKernel.prepareShadowEdgeIntersections(): KernelStep {
    mergeShadowSamples()
    // Reference step 100: branch setup
    updateSubtractionFlags(shadowIndex8, 0L, 32L)
    return KernelStep.PrepareShadowSegmentIndices
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = directory/'KernelShadowTopology.kt'
            path.write_text(source, encoding='utf-8')
            replace_shadow_segments(directory)
            actual = path.read_text(encoding='utf-8')
            self.assertIn('mergeShadowSamples()\n    buildShadowSegments()', actual)
            self.assertIn('return KernelStep.FinishShadowTopology', actual)
            replace_shadow_segments(directory)
            self.assertEqual(actual, path.read_text(encoding='utf-8'))

    def test_invalid_terminal_is_kept_only_when_reachable(self):
        for terminal, expected in (('Complete', False), ('Invalid', True)):
            with self.subTest(terminal=terminal), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                (directory/'KernelFlow.kt').write_text('''internal enum class KernelStep {
    Complete,
    Invalid,
}
fun execute() = when (step) {
    KernelStep.Invalid -> error("Invalid curl geometry")
}
''', encoding='utf-8')
                (directory/'KernelProjection.kt').write_text(f'''// Reference step 0: entry
internal fun CurlMeshKernel.prepareMaterialsAndBounds(): KernelStep {{
    return KernelStep.{terminal}
}}
// Reference step 243: orphan
internal fun CurlMeshKernel.rejectGeometry(): KernelStep {{
    return KernelStep.Invalid
}}
''', encoding='utf-8')
                self.assertEqual(['rejectGeometry'], remove_unreachable_steps(directory))
                self.assertEqual(expected, 'Invalid' in (directory/'KernelFlow.kt').read_text(encoding='utf-8'))

    def test_recovery_retains_a_successor_joined_into_the_entry(self):
        source = '''// Reference step 75: structured edge selection and merge
internal fun CurlMeshKernel.prepareShadowEdgeIntersections(): KernelStep {
    mergeShadowSamples()
    // Reference step 100: merged successor
    updateSubtractionFlags(shadowIndex8, 0L, 32L)
    return KernelStep.InspectShadowSegment
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory/'KernelShadowTopology.kt').write_text(source, encoding='utf-8')
            (directory/'KernelFaceSampling.kt').write_text('', encoding='utf-8')
            self.assertEqual([], replace_sample_merges(directory))
            self.assertEqual(source, (directory/'KernelShadowTopology.kt').read_text(encoding='utf-8'))

    def test_fresh_recovery_replaces_only_the_shared_region(self):
        source = '''// Reference step 75: original entry
internal fun CurlMeshKernel.prepareShadowEdgeIntersections(): KernelStep {
    return KernelStep.UseEmptyShadowEdges
}
// Reference step 76: old branch
internal fun CurlMeshKernel.useEmptyShadowEdges(): KernelStep {
    return KernelStep.InspectShadowSegmentCount
}
// Reference step 100: following algorithm
internal fun CurlMeshKernel.inspectShadowSegmentCount(): KernelStep {
    updateSubtractionFlags(shadowIndex8, 0L, 32L)
    return KernelStep.Complete
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory/'KernelShadowTopology.kt').write_text(source, encoding='utf-8')
            (directory/'KernelFaceSampling.kt').write_text('', encoding='utf-8')
            self.assertEqual(['useEmptyShadowEdges'], replace_sample_merges(directory))
            actual = (directory/'KernelShadowTopology.kt').read_text(encoding='utf-8')
            self.assertIn('mergeShadowSamples()', actual)
            self.assertNotIn('useEmptyShadowEdges', actual)
            self.assertIn('updateSubtractionFlags(shadowIndex8, 0L, 32L)', actual)


    def test_penumbra_recovery_removes_joined_steps_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory/'KernelPenumbra.kt').write_text('''// Reference step 125: entry
internal fun CurlMeshKernel.prepareShadowCrossSections(): KernelStep {
    // Reference step 155: joined successor
    return KernelStep.PreparePenumbraCorners
}
// Reference step 173: loop
internal fun CurlMeshKernel.preparePenumbraCorners(): KernelStep {
    return KernelStep.PrepareShadowCrossSections
}
''',encoding='utf-8')
            (directory/'KernelFlow.kt').write_text('''internal enum class KernelStep {
    PrepareShadowCrossSections,
    PreparePenumbraCorners,
    InspectFrontFace,
}
fun execute() = when (step) {
    KernelStep.PreparePenumbraCorners -> preparePenumbraCorners()
}
''',encoding='utf-8')
            self.assertEqual(['preparePenumbraCorners'],replace_penumbra(directory))
            actual=(directory/'KernelPenumbra.kt').read_text(encoding='utf-8')
            self.assertIn('buildPenumbraStrips()',actual)
            self.assertIn('buildPenumbraContours()',actual)
            self.assertNotIn('PreparePenumbraCorners',(directory/'KernelFlow.kt').read_text(encoding='utf-8'))
            self.assertEqual([],replace_penumbra(directory))
            self.assertEqual(actual,(directory/'KernelPenumbra.kt').read_text(encoding='utf-8'))


    def test_final_recovery_requires_algorithms_before_removing_reference_files(self):
        from CurlKernelAlgorithms import recover
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            path=directory/'KernelValues.kt'
            path.write_text('original register views',encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'Missing maintained kernel algorithms'):
                recover(directory)
            self.assertEqual('original register views',path.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
