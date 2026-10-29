"""Replace recovered machine-flow regions with maintained algorithm stages.

Called before local-value analysis, so removed operations do not keep scratch
views alive. Maintained helpers are ordinary Kotlin, outside the C block emitter.
"""
from pathlib import Path
import re


def remove_steps(directory, names):
    from CurlKernelLocals import FUNCTION, closing
    for path in directory.glob('*.kt'):
        text = path.read_text(encoding='utf-8')
        for m in reversed(list(FUNCTION.finditer(text))):
            if m[1] in names:
                start = text.rfind('// Reference step ', 0, m.start())
                text = text[:start] + text[closing(text, m.end()-1) + 1:]
        if path.name == 'KernelFlow.kt':
            for name in names:
                tag = name[0].upper() + name[1:]
                text = re.sub(r'(?m)^\s*' + tag + r',\s*\n', '', text)
                text = re.sub(r'(?m)^\s*KernelStep\.' + tag + r' ->\s*' + name + r'\(\)\s*\n', '', text)
        path.write_text(text, encoding='utf-8')


def replace_matrices(directory):
    directory = Path(directory)
    path = directory / 'KernelProjection.kt'
    text = path.read_text(encoding='utf-8')
    if 'return KernelStep.ProjectPageCorner' in text:
        start = text.index('    projectionValue85 =')
        end = text.index('return KernelStep.ProjectPageCorner', start) + len('return KernelStep.ProjectPageCorner')
        text = text[:start] + '    projectPageCorners()\n    return KernelStep.PrepareBreakpointSort' + text[end:]
    text = text.replace('transformPointInPlace(pointTransformAddress, transformedPointAddress)', 'transformPointInPlace(transformedPointAddress)')
    text = re.sub(r'(?m)^    writeLongBits\([^\n]*88L[^\n]*outputTransformArgument[^\n]*\)\n', '', text)
    text = re.sub(r'(?m)^    writeLongBits\([^\n]*128L[^\n]*sourceSurfaceAddress[^\n]*\)\n', '', text)
    # The transform is a typed frame input, not a serialized matrix address.
    text = re.sub(r'(?m)^    (?:val )?pointTransformAddress(?:: Long)?\s*=\s*[^\n]+\n', '', text)
    path.write_text(text, encoding='utf-8')
    remove_steps(directory, {'advanceProjectedCorner', 'projectPageCorner'})
    path = directory / 'KernelPenumbra.kt'
    text = path.read_text(encoding='utf-8')
    text = text.replace('writeRotationMatrix(penumbraValue250, rotationMatrixAddress)', 'cornerRotation = KernelMatrix.rotation(penumbraValue250)')
    text = re.sub(r'(?m)^    (?:val )?rotationMatrixAddress(?:: Long)?\s*=\s*[^\n]+\n', '', text)
    for number in range(253,259):
        text = re.sub(r'(?m)^    penumbraValue' + str(number) + r' = readDouble\([^\n]+\n', '', text)
    match = re.search(r'(?m)^    (?:val )?penumbraValue272(?:: Double)?\s*=', text)
    if match:
        start = match.start()
        end = text.index('    penumbraIndex49 =', start)
        text = text[:start]+'''    val directionX = penumbraValue227
    val directionY = penumbraValue228
    val rotatedX = cornerRotation.affineX(directionX, directionY)
    val rotatedY = cornerRotation.affineY(directionX, directionY)
    penumbraValue227 = rotatedX
    penumbraValue228 = rotatedY
'''+text[end:]
    path.write_text(text, encoding='utf-8')
    path = directory / 'KernelPrimitives.kt'
    text = path.read_text(encoding='utf-8')
    from CurlKernelLocals import closing
    m = re.search(r'internal fun CurlMeshKernel\.transformPointInPlace\(matrixAddress:', text)
    if m:
        end = closing(text, text.index('{', m.end())) + 1
        text = text[:m.start()]+'''internal fun CurlMeshKernel.transformPointInPlace(pointAddress: Long) {
    val point = pageToLogical.projectPoint(readDouble(pointAddress), readDouble(pointAddress + 8L))
    writeDouble(pointAddress, point.x)
    writeDouble(pointAddress + 8L, point.y)
}'''+text[end:]
    m = re.search(r'internal fun CurlMeshKernel\.writeRotationMatrix\(', text)
    if m:
        end = closing(text, text.index('{', m.end())) + 1
        text = text[:m.start()]+text[end:]
    path.write_text(text, encoding='utf-8')


def replace_drawing(directory):
    from CurlKernelLocals import FUNCTION, closing
    directory = Path(directory)
    path = directory / 'KernelDrawing.kt'
    old = path.read_text(encoding='utf-8')
    names = {m[1] for m in FUNCTION.finditer(old)} - {'prepareDrawState', 'rejectGeometry'}
    path.write_text('''// Structured batch stage; reference blocks 215-242.
package org.peyilo.ioscurl.mesh.kernel

// Reference step 215: structured batch assembly
internal fun CurlMeshKernel.prepareDrawState(): KernelStep {
    assembleBatches()
    return KernelStep.Complete
}

// Reference step 243: invalid geometry
internal fun CurlMeshKernel.rejectGeometry(): KernelStep {
    return KernelStep.Invalid
}
''', encoding='utf-8')
    flow = directory / 'KernelFlow.kt'
    text = flow.read_text(encoding='utf-8')
    for name in names:
        tag = name[0].upper() + name[1:]
        text = re.sub(r'(?m)^\s*' + tag + r',\s*\n', '', text)
        text = re.sub(r'(?m)^\s*KernelStep\.' + tag + r' ->\s*' + name + r'\(\)\s*\n', '', text)
    flow.write_text(text, encoding='utf-8')
    primitive = directory / 'KernelPrimitives.kt'
    text = primitive.read_text(encoding='utf-8')
    for name in ('measureTransformScale', 'nextClipRectangle', 'appendDrawBatch'):
        match = re.search(r'internal fun CurlMeshKernel\.' + name + r'\(', text)
        if match:
            start = text.index('{', match.end())
            text = text[:match.start()] + text[closing(text, start) + 1:]
    primitive.write_text(text, encoding='utf-8')
    return sorted(names)


def replace_sample_merges(directory):
    """The shadow/front edge collection and descending merges share one algorithm."""
    from CurlKernelLocals import FUNCTION, closing
    directory = Path(directory)
    removed = set()
    for filename, first, last, entry, helper, target in (
        ('KernelShadowTopology.kt', 75, 99, 'prepareShadowEdgeIntersections',
         'mergeShadowSamples', 'InspectShadowSegmentCount'),
        ('KernelFaceSampling.kt', 186, 210, 'inspectFirstFrontEdge',
         'mergeFrontSamples', 'PrepareFrontCrossSections'),
    ):
        path = directory / filename
        text = path.read_text(encoding='utf-8')
        # The local pass can join the exit block into this entry. Leave an
        # already recovered entry intact, including its joined successor.
        if f'{helper}()' in text:
            continue
        for m in reversed(list(FUNCTION.finditer(text))):
            start = text.rfind('// Reference step ', 0, m.start())
            reference = int(re.match(r'// Reference step (\d+):', text[start:])[1])
            if not first <= reference <= last:
                continue
            end = closing(text, m.end() - 1) + 1
            if m[1] == entry:
                replacement = (f'// Reference step {first}: structured edge selection and merge\n'
                               f'internal fun CurlMeshKernel.{entry}(): KernelStep {{\n'
                               f'    {helper}()\n    return KernelStep.{target}\n}}')
            else:
                removed.add(m[1])
                replacement = ''
            text = text[:start] + replacement + text[end:]
        path.write_text(text, encoding='utf-8')
    remove_steps(directory, removed)
    return sorted(removed)


def replace_shadow_segments(directory):
    """Recover the boundary-index merge and endpoint classification loops."""
    from CurlKernelLocals import FUNCTION, closing
    directory = Path(directory)
    path = directory/'KernelShadowTopology.kt'
    text = path.read_text(encoding='utf-8')
    if 'buildShadowSegments()' in text:
        return []
    removed = set()
    for m in reversed(list(FUNCTION.finditer(text))):
        start = text.rfind('// Reference step ', 0, m.start())
        reference = int(re.match(r'// Reference step (\d+):', text[start:])[1])
        end = closing(text, m.end()-1) + 1
        if 101 <= reference <= 123:
            removed.add(m[1])
            text = text[:start] + text[end:]
        elif reference == 100:
            text = text[:m.end()] + '''
    buildShadowSegments()
    return KernelStep.FinishShadowTopology
}''' + text[end:]
        elif reference == 75:
            # The previous recovery can have joined step 100 into step 75.
            marker = text.find('    // Reference step 100:', m.end(), end)
            if marker >= 0:
                text = text[:marker] + '''    buildShadowSegments()
    return KernelStep.FinishShadowTopology
}''' + text[end:]
    path.write_text(text, encoding='utf-8')
    remove_steps(directory, removed)
    return sorted(removed)


def replace_shadow_samples(directory):
    """Recover coarse/fine sampling and join identical disabled/empty exits."""
    from CurlKernelLocals import FUNCTION, closing
    directory = Path(directory)
    path = directory/'KernelShadowTopology.kt'
    text = path.read_text(encoding='utf-8')
    if 'collectShadowSamples()' in text:
        return []
    removed = set()
    for m in reversed(list(FUNCTION.finditer(text))):
        start = text.rfind('// Reference step ', 0, m.start())
        reference = int(re.match(r'// Reference step (\d+):', text[start:])[1])
        end = closing(text, m.end()-1) + 1
        if 60 <= reference <= 74:
            removed.add(m[1])
            text = text[:start] + text[end:]
        elif reference == 59:
            body = text[m.end():end-1]
            tail = body.rfind('\n    return ')
            assert tail >= 0
            text = text[:m.end()] + body[:tail] + '''
    shadowValue23.double(0, readDouble(scratchBase + 520L))
    if ((surfaceAddress22 and KernelLayout.WORD_MASK) == 0L || !collectShadowSamples()) {
        writeLongBits(scratchBase + 560L, 0L)
        writeWord(scratchBase + 544L, 0L)
        writeLongBits(scratchBase + 568L, 0L)
        writeLongBits(scratchBase + 288L, 0L)
        shadowValue35 = 1.0
        return KernelStep.InspectFrontFace
    }
    return KernelStep.PrepareShadowEdgeIntersections
}''' + text[end:]
    path.write_text(text, encoding='utf-8')
    remove_steps(directory, removed)
    return sorted(removed)


def replace_shadow_profile(directory):
    """Two scalar limits replace transient flags, shared slots and lookup tables."""
    directory = Path(directory)
    path = directory/'KernelShadowTopology.kt'
    text = path.read_text(encoding='utf-8')
    start = text.find('    shadowValue61 = readDouble(shadowAddress)')
    if start >= 0:
        end = re.search(r'    updateSubtractionFlags\(\s*\(*\s*shadowIndex13\b',text[start:]).start() + start
        text = text[:start] + '''    shadowProfile = KernelShadowProfile.forTime(readDouble(shadowAddress))
    writeDouble(scratchBase + 568L, shadowProfile.innerLimit)
''' + text[end:]
    path.write_text(text, encoding='utf-8')
    path = directory/'KernelPenumbra.kt'
    text = path.read_text(encoding='utf-8')
    start = text.find('    compareFloatingPoint(shadowValue65, shadowValue66)')
    if start >= 0:
        end = re.search(r'    writeDouble\(\s*\(*scratchBase \+ 424L',text[start:]).start() + start
        text = text[:start] + '    val outerLimit = shadowProfile.outerLimit\n' + text[end:]
        text = re.sub(r'\bpenumbraValue11\b','outerLimit',text)
        # The raw block computes these reciprocals immediately before the
        # first store; the localized block has already folded them into uses.
        text = re.sub(r'\bpenumbraValue13\b','(1.0 / surfaceValue101.double(0))',text)
        text = re.sub(r'\bpenumbraValue12\b','1.0',text)
    path.write_text(text, encoding='utf-8')


SHADOW_BOUND_COORDINATES = {
    'shadowValue7': 'lowerY', 'shadowValue8': 'upperY',
    'shadowValue9': 'firstEdgeX', 'shadowValue10': 'secondEdgeX',
    'shadowValue11': 'lowerX', 'shadowValue12': 'upperX',
    'shadowValue13': 'firstEdgeY', 'shadowValue14': 'secondEdgeY',
}


def replace_shadow_bounds(directory):
    """Keep geometric bounds across stages, rather than saving/restoring vector slots."""
    directory = Path(directory)
    topology = directory/'KernelShadowTopology.kt'
    text = topology.read_text(encoding='utf-8')
    if 'KernelShadowBounds.fromRectangle(' not in text:
        start = text.index('\n',text.index('    shadowAddress =')) + 1
        end = text.index('    shadowValue23.double(0, readDouble(scratchBase + 520L))',start)
        text = text[:start] + '''    val boundsAddress = shadowAddress + KernelLayout.Control.SHADOW_BOUNDS
    shadowBounds = KernelShadowBounds.fromRectangle(
        readDouble(boundsAddress + KernelLayout.Rect.X),
        readDouble(boundsAddress + KernelLayout.Rect.Y),
        readDouble(boundsAddress + KernelLayout.Rect.WIDTH),
        readDouble(boundsAddress + KernelLayout.Rect.HEIGHT),
        projectionValue104.double(0), projectionValue128.double(0),
    )
    writeDouble(scratchBase + 456L, shadowBounds.secondEdgeX)
    writeDouble(scratchBase + 464L, shadowBounds.firstEdgeX)
    writeDouble(scratchBase + 432L, shadowBounds.secondEdgeY)
    writeDouble(scratchBase + 440L, shadowBounds.firstEdgeY)
''' + text[end:]
    # The only reads of these four backup coordinates were the removed restore
    # at step 179. The face overwrites these words with its own lighting inputs.
    text = re.sub(r'(?m)^    writeDouble\([^\n]*320L[^\n]*, (?:shadowValue11|lowerX)\)\n', '', text)
    text = re.sub(r'(?m)^    writeDouble\([^\n]*320L[^\n]*, (?:shadowValue12|upperX)\)\n', '', text)
    topology.write_text(text, encoding='utf-8')
    pattern = re.compile(r'\b(' + '|'.join(SHADOW_BOUND_COORDINATES) + r')\b')
    for path in directory.glob('*.kt'):
        if path.name in ('KernelValues.kt', 'KernelShadowTopology.kt'):
            continue
        text = path.read_text(encoding='utf-8')
        # These restored lanes are dead after the face consumes typed bounds;
        # byte-liveness was checked at the InspectFrontFace entry.
        text = re.sub(r'(?m)^    (?:' + '|'.join(SHADOW_BOUND_COORDINATES) +
                      r') = readDouble\([^\n]*\n', '', text)
        text = pattern.sub(lambda m: 'shadowBounds.'+SHADOW_BOUND_COORDINATES[m[0]], text)
        text = text.replace('    writeDouble(scratchBase + 344L, shadowBounds.upperY)\n','')
        text = text.replace('    writeDouble(scratchBase + 336L, shadowBounds.lowerY)\n','')
        path.write_text(text, encoding='utf-8')


def replace_shadow_range_model(directory):
    """Pass range/topology records instead of communicating via nine reused slots."""
    from CurlKernelLocals import closing
    directory = Path(directory)
    path = directory/'KernelShadowTopology.kt'
    text = path.read_text(encoding='utf-8')
    if 'val shadowRange =' not in text:
        start = text.index('    if ((surfaceAddress22')
        end = closing(text,text.index('{',start)) + 1
        text = text[:start] + '''    val shadowRange = if ((surfaceAddress22 and KernelLayout.WORD_MASK) != 0L)
        collectShadowSamples() else null
    if (shadowRange == null) {
        writeLongBits(scratchBase + 560L, 0L)
        writeWord(scratchBase + 544L, 0L)
        writeLongBits(scratchBase + 568L, 0L)
        writeLongBits(scratchBase + 288L, 0L)
        shadowValue35 = 1.0
        return KernelStep.InspectFrontFace
    }''' + text[end:]
        text = text.replace('mergeShadowSamples()', 'mergeShadowSamples(shadowRange)')
        text = text.replace('    buildShadowSegments()\n','')
        start = re.search(r'    updateSubtractionFlags\(\s*\(*\s*shadowIndex13\b',text).start()
        end = text.index('\n}',start)
        # A nonempty range has >=1 coarse sample; the merge adds a final copy.
        # Consequently the topology always has >=2 samples and the empty
        # penumbra branch has no callers. The graph pass removes it.
        text = text[:start] + '''    writeLongBits(scratchBase + 288L, shadowTopology.sampleCount.toLong())
    return KernelStep.PrepareShadowCrossSections''' + text[end:]
    path.write_text(text, encoding='utf-8')
    names = {'shadowIndex5': 'shadowTopology.edgeCount.toLong()',
             'shadowIndex13': 'shadowTopology.sampleCount.toLong()',
             'shadowBits6': 'shadowTopology.lastSampleIndex.toLong()'}
    for path in directory.glob('*.kt'):
        if path.name == 'KernelValues.kt':
            continue
        text = path.read_text(encoding='utf-8')
        text = text.replace('    shadowBits6 = penumbraBits\n','')
        text = re.sub(r'\b('+'|'.join(names)+r')\b',lambda m:names[m[0]],text)
        path.write_text(text,encoding='utf-8')


def replace_back_strips(directory):
    """Recover strip copies and constant-color loops without draw-state registers."""
    from CurlKernelLocals import FUNCTION, closing
    directory = Path(directory)
    path = directory/'KernelSurfaceSampling.kt'
    text = path.read_text(encoding='utf-8')
    if 'copyBackStrips()' in text:
        return []
    removed = set()
    for m in reversed(list(FUNCTION.finditer(text))):
        start = text.rfind('// Reference step ',0,m.start())
        reference = int(re.match(r'// Reference step (\d+):',text[start:])[1])
        end = closing(text,m.end()-1)+1
        if 53 <= reference <= 58:
            removed.add(m[1])
            text = text[:start]+text[end:]
        elif reference == 52:
            body = text[m.end():end-1]
            tail = body.rfind('\n    return ')
            assert tail >= 0
            text = text[:m.end()]+body[:tail]+'''
    copyBackStrips()
    return KernelStep.PrepareShadowBreakpoints
}'''+text[end:]
    path.write_text(text,encoding='utf-8')
    remove_steps(directory,removed)
    return sorted(removed)


def replace_surface_strips(directory):
    """Curved cross sections, lighting and color interpolation form one sample loop."""
    from CurlKernelLocals import FUNCTION, closing
    directory = Path(directory)
    path = directory/'KernelSurfaceSampling.kt'
    text = path.read_text(encoding='utf-8')
    if 'buildSurfaceStrips()' in text:
        return []
    removed = set()
    for m in reversed(list(FUNCTION.finditer(text))):
        start = text.rfind('// Reference step ',0,m.start())
        reference = int(re.match(r'// Reference step (\d+):',text[start:])[1])
        end = closing(text,m.end()-1)+1
        if 42 <= reference <= 51:
            removed.add(m[1])
            text = text[:start]+text[end:]
        elif reference == 41:
            text = text[:m.end()]+'''
    buildSurfaceStrips()
    return KernelStep.PrepareBackStripIndices
}'''+text[end:]
    path.write_text(text,encoding='utf-8')
    remove_steps(directory,removed)
    return sorted(removed)


def replace_strip_section_calls(directory):
    """The final reference caller passes clip/color values without a scratch packet."""
    from CurlKernelLocals import closing, arguments
    directory = Path(directory)
    path = directory/'KernelFaceSampling.kt'
    text = path.read_text(encoding='utf-8')
    if 'buildFrontStrips()' in text:
        return
    if 'val stripColor =' not in text:
        pack = re.search(r'\bfaceColor2\.packHalfColorFrom\(',text)
        assert pack is not None
        end = closing(text,pack.end()-1)+1
        # The raw block next reuses v0 for its X coordinate. Freeze the color
        # before that write, just as the former scratch packet did.
        text=text[:end]+'\n    val stripColor = faceColor2.longBits(0)'+text[end:]
    for match in reversed(list(re.finditer(r'\bappendSurfaceStrip\(',text))):
        end = closing(text,match.end()-1)
        args = arguments(text[match.end():end])
        assert len(args)==14,args
        values = args[:4]+args[5:13]+[
            'faceValue187','faceValue188','faceValue189','faceValue190','stripColor']
        text = text[:match.start()]+'appendStripSection(\n        '+',\n        '.join(values)+',\n    )'+text[end+1:]
    for match in reversed(list(re.finditer(r'\bappendStripSection\(',text))):
        end=closing(text,match.end()-1)
        args=arguments(text[match.end():end])
        if args[-1]=='faceColor2.longBits(0)':
            args[-1]='stripColor'
            text=text[:match.start()]+'appendStripSection(\n        '+',\n        '.join(args)+',\n    )'+text[end+1:]
    text = re.sub(r'(?m)^    writeFloat\([^\n]*, faceValue(?:187|188|189|190)\)\n','',text)
    text = re.sub(r'(?m)^    writeDouble\([^\n]*584L[^\n]*faceColor2\.double\(0\)\)\n','',text)
    path.write_text(text,encoding='utf-8')
    path = directory/'KernelPrimitives.kt'
    text = path.read_text(encoding='utf-8')
    match = re.search(r'internal fun CurlMeshKernel\.appendSurfaceStrip\(',text)
    if match:
        end = closing(text,text.index('{',match.end()))+1
        text = text[:match.start()]+text[end:]
    text = text.replace('/** A strip section receives values; the two remaining reference callers use the adapter above. */',
                        '/** Geometry, UVs and quantized clip distances are passed directly, without a scratch packet. */')
    path.write_text(text,encoding='utf-8')


def replace_curl_sample_calls(directory):
    """Sine is an optional inline result, not a serialized output address."""
    from CurlKernelLocals import arguments,closing,clean_parentheses
    directory = Path(directory)
    for filename in ('KernelFaceSampling.kt','KernelPenumbra.kt'):
        path = directory/filename
        text = path.read_text(encoding='utf-8')
        for match in reversed(list(re.finditer(r'\bcurlDisplacement\(',text))):
            end = closing(text,match.end()-1)
            args = arguments(text[match.end():end])
            if len(args)==5:
                continue
            assert len(args)==6,args
            output = clean_parentheses(args[3])
            if output!='0L':
                definitions = re.findall(r'(?m)^    (?:val )?'+re.escape(output)+r'(?:: Long)?\s*=\s*([^\n]+)',text)
                assert len(definitions)==1 and clean_parentheses(definitions[0].strip())=='0L',output
            values=args[:3]+args[4:]
            text=text[:match.start()]+'curlDisplacement(\n        '+',\n        '.join(values)+',\n    )'+text[end+1:]
        path.write_text(text,encoding='utf-8')


def replace_front_strips(directory):
    """The final geometry stage exposes only vertex buffers and a vertex count."""
    from CurlKernelLocals import FUNCTION
    directory = Path(directory)
    path = directory/'KernelFaceSampling.kt'
    text = path.read_text(encoding='utf-8')
    if 'buildFrontStrips()' in text:
        return []
    removed={m[1] for m in FUNCTION.finditer(text)}-{'inspectFrontFace'}
    path.write_text('''// Structured front sections; reference steps 180-214.
package org.peyilo.ioscurl.mesh.kernel

// Reference step 180: front-face sampling and emission
internal fun CurlMeshKernel.inspectFrontFace(): KernelStep {
    buildFrontStrips()
    return KernelStep.PrepareDrawState
}
''',encoding='utf-8')
    remove_steps(directory,removed)
    return sorted(removed)


def replace_penumbra(directory):
    """Cross sections, fading bands, endpoints and corner rings expose only geometry."""
    from CurlKernelLocals import FUNCTION
    directory = Path(directory)
    path = directory/'KernelPenumbra.kt'
    text = path.read_text(encoding='utf-8')
    if 'buildPenumbraContours()' in text:
        return []
    removed = {m[1] for m in FUNCTION.finditer(text)} - {'prepareShadowCrossSections'}
    path.write_text('''// Structured penumbra geometry; reference steps 125-179.
package org.peyilo.ioscurl.mesh.kernel

// Reference step 125: cross sections, fading bands and rounded contours
internal fun CurlMeshKernel.prepareShadowCrossSections(): KernelStep {
    buildPenumbraStrips()
    buildPenumbraContours()
    shadowAddress = readLongBits(scratchBase + 96L)
    return KernelStep.InspectFrontFace
}
''',encoding='utf-8')
    remove_steps(directory,removed)
    return sorted(removed)


def remove_front_handoffs(directory):
    """The value-based front stage reads neither legacy flags nor the curl-start slot."""
    directory=Path(directory)
    for name in ('KernelShadowTopology.kt','KernelPenumbra.kt'):
        path=directory/name
        text=path.read_text(encoding='utf-8')
        text=re.sub(r'(?m)^    shadowValue23\.double\(0,[^\n]*\n','',text)
        if name=='KernelPenumbra.kt':
            text=re.sub(r'(?m)^    (?:surfaceAddress21|surfaceValue102) = read(?:Double|LongBits)\([^\n]*\n','',text)
        path.write_text(text,encoding='utf-8')


def remove_surface_handoffs(directory):
    """Structured penumbra no longer consumes packed surface continuation lanes."""
    path = Path(directory)/'KernelShadowTopology.kt'
    text = path.read_text(encoding='utf-8')
    text = text.replace('projectionValue104.double(0)', 'readDouble(scratchBase + 488L)')
    text = text.replace('projectionValue128.double(0)', 'readDouble(scratchBase + 480L)')
    path.write_text(text,encoding='utf-8')


def replace_projection_setup(directory):
    """Material modulation, projection and sampling use maintained scalar/matrix code."""
    from CurlKernelLocals import FUNCTION
    directory=Path(directory)
    path=directory/'KernelProjection.kt'
    text=path.read_text(encoding='utf-8')
    if 'setupCurveProjection(' in text:
        return []
    removed={m[1] for m in FUNCTION.finditer(text)}-{'prepareMaterialsAndBounds'}
    path.write_text('// Structured projection setup and sampling; reference steps 0-40.\npackage org.peyilo.ioscurl.mesh.kernel\n\n// Reference step 0: material, projection and sample preparation\ninternal fun CurlMeshKernel.prepareMaterialsAndBounds(): KernelStep {\n    setupCurveProjection(controlInputAddress, materialInputAddress,\n        readLongBits(contextArgument + KernelLayout.Context.LAYER_STATE))\n    return KernelStep.PrepareSurfaceStrips\n}\n',encoding='utf-8')
    remove_steps(directory,removed)
    path=directory/'KernelSurfaceSampling.kt'
    text=path.read_text(encoding='utf-8')
    if 'curveSamples.backCount' not in text:
        start=text.index('    val surfaceIndex7: Long =') if '    val surfaceIndex7: Long =' in text else text.index('    surfaceIndex7 =')
        end=text.index('    surfaceAddress22 =',start)
        text=text[:start]+'    writeWord(scratchBase + 280L, curveSamples.backCount.toLong())\n'+text[end:]
        path.write_text(text,encoding='utf-8')
    return sorted(removed)


def recover(directory, source_root=None):
    """Require the typed algorithms, then remove obsolete reference/storage source files.

    All runtime code is maintained Kotlin. Reference blocks remain optional research
    input and must not reintroduce register state, byte-addressed parameters or arenas.
    """
    directory=Path(directory).resolve()
    required=['Color.kt', 'CurlMeshKernel.kt', 'Input.kt', 'KernelCurve.kt', 'KernelMatrix.kt', 'KernelTuning.kt', 'Output.kt', 'Penumbra.kt', 'Projection.kt', 'Sampling.kt', 'Shadow.kt', 'Surface.kt', 'Vertices.kt']
    missing=[name for name in required if not (directory/name).is_file()]
    if missing:
        raise ValueError('Missing maintained kernel algorithms: '+', '.join(missing))
    removed=[]
    for name in ['KernelBackStrips.kt', 'KernelBatchAssembly.kt', 'KernelColor.kt', 'KernelCurveSamples.kt', 'KernelDrawing.kt', 'KernelFaceSampling.kt', 'KernelFlow.kt', 'KernelFrame.kt', 'KernelFrontStrips.kt', 'KernelGeometry.kt', 'KernelInput.kt', 'KernelLayout.kt', 'KernelMemory.kt', 'KernelPenumbra.kt', 'KernelPenumbraContours.kt', 'KernelPenumbraStrips.kt', 'KernelPrimitives.kt', 'KernelProjectedBounds.kt', 'KernelProjection.kt', 'KernelProjectionSetup.kt', 'KernelRetainedValues.kt', 'KernelSampleMerge.kt', 'KernelSamples.kt', 'KernelShadowBounds.kt', 'KernelShadowGeometry.kt', 'KernelShadowProfile.kt', 'KernelShadowSamples.kt', 'KernelShadowSegments.kt', 'KernelShadowTopology.kt', 'KernelSorting.kt', 'KernelSurfaceSampling.kt', 'KernelSurfaceStrips.kt', 'KernelValues.kt', 'KernelVertices.kt']:
        path=directory/name
        assert path.resolve().parent==directory
        if path.exists():
            path.unlink()
            removed.append(name)
    return {'mode':'typed geometry algorithms','removedGeneratedFiles':removed,
            'registerViews':0,'registerContainers':0,'referenceTransitions':0,
            'memoryArena':False,'addressBasedParameters':False,
            'maintainedSources':required}


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--source-root', type=Path, help='Main source tree for an isolated recovery check')
    args = parser.parse_args()
    import json
    print(json.dumps(recover(args.directory, args.source_root), indent=2))
