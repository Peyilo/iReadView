"""Recover block-local Kotlin values without dropping observable packed-vector bits.

The reference port intentionally reuses physical storage. A local scalar can
replace a generated property, but its write may still supply an unused lane of
a later vector operation. Backward byte-liveness keeps exactly those writes.
Arithmetic, narrowing, FMA calls and branch order are never reassociated.

This is a build-time source pass, not a runtime dependency. It also accepts an
already localized tree, making regeneration and review repeatable.
"""
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/ioscurl/src/main/kotlin/org/peyilo/ioscurl/mesh/kernel'
IDENT = re.compile(r'\b[A-Za-z_]\w*\b')
FUNCTION = re.compile(r'internal fun CurlMeshKernel\.(\w+)\(\): KernelStep\s*\{')
PROPERTY = re.compile(r'internal (?:inline )?(?:var|val) CurlMeshKernel\.(\w+): (\w+)')
SORT_NAMES = {
    'projectionAddress5': 'breakpointBaseAddress',
    'projectionAddress6': 'breakpointProbeAddress',
    'projectionIndex2': 'sortedBreakpointCount',
    'projectionIndex3': 'breakpointProbeOffset',
    'projectionValue125': 'breakpointKey',
    'projectionValue129': 'breakpointProbeValue',
    'projectionBits2': 'breakpointInsertionIndex',
}


def closing(text, opening):
    """Balanced Kotlin parentheses/braces in generated arithmetic expressions."""
    close = {'(': ')', '{': '}', '[': ']'}[text[opening]]
    depth = 1
    for p in range(opening + 1, len(text)):
        depth += (text[p] == text[opening]) - (text[p] == close)
        if depth == 0:
            return p
    raise ValueError(text[opening:opening + 100])


def arguments(text):
    start = depth = 0
    result = []
    for p, c in enumerate(text):
        depth += (c in '([{') - (c in ')]}')
        if c == ',' and depth == 0:
            result.append(text[start:p].strip())
            start = p + 1
    if text[start:].strip():
        result.append(text[start:].strip())
    return result


def statements(body):
    start = depth = 0
    for line in body.splitlines(keepends=True):
        depth += sum(line.count(c) for c in '([{') - sum(line.count(c) for c in ')]}')
        end = start + len(line)
        if depth == 0 and not line.rstrip().endswith(('=', '->')):
            yield end
        start = end


@dataclass
class Binding:
    name: str
    kind: str
    slot: int
    method: str
    source: str

    def bits(self, lane=0):
        size = {'integer': 8, 'double': 8, 'longBits': 8, 'float': 4,
                'wordBits': 4, 'halfBits': 2, 'vector': 16}[self.method]
        base = self.slot * 8 if self.method == 'integer' else 256 + self.slot * 16
        return ((1 << size) - 1) << (base + lane * size)

    def retained(self, expression):
        suffix = {'integer': 'Integer', 'double': 'Double', 'float': 'Float',
                  'longBits': 'LongBits', 'wordBits': 'WordBits', 'halfBits': 'HalfBits'}[self.method]
        return f'retain{suffix}({self.slot}, {expression})'

    def local_value(self, expression):
        # The former getter zero-extended packed integer lanes. Removing its
        # storage write must not remove that narrowing from the local value.
        mask = {'wordBits': 'WORD_MASK', 'halfBits': 'HALF_MASK'}.get(self.method)
        return f'({expression}) and KernelLayout.{mask}' if mask else expression


def bindings(text):
    found = list(PROPERTY.finditer(text))
    result = {}
    for i, m in enumerate(found):
        source = text[m.start():found[i + 1].start() if i + 1 < len(found) else len(text)]
        slot = re.search(r'(integerValue|vectorValue)\((\d+)\)(?:\.(\w+)\(0\))?', source)
        assert slot, source
        method = 'integer' if slot[1] == 'integerValue' else slot[3] or 'vector'
        result[m[1]] = Binding(m[1], m[2], int(slot[2]), method, source)
    return text[:found[0].start()] if found else text, result


def compact_steps(directory):
    """Join straight-line blocks; no branch conditions or arithmetic change."""
    files = {p: p.read_text(encoding='utf-8') for p in directory.glob('*.kt')}
    blocks = {}
    for path, text in files.items():
        for m in FUNCTION.finditer(text):
            end = closing(text, m.end() - 1)
            line = text.rfind('// Reference step ', 0, m.start())
            blocks[m[1]] = dict(path=path, start=line, end=end + 1,
                                note=text[line:m.start()].strip(), body=text[m.end():end])
    merged, removed = {}, set()
    while True:
        predecessors = defaultdict(set)
        for name, block in blocks.items():
            for target in re.findall(r'KernelStep\.(\w+)', block['body']):
                predecessors[target[0].lower() + target[1:]].add(name)
        found = False
        for name, block in blocks.items():
            target = re.search(r'\breturn KernelStep\.(\w+)\s*$', block['body'])
            if not target:
                continue
            successor = target[1][0].lower() + target[1][1:]
            other = blocks.get(successor)
            if not other or successor == name or other['path'] != block['path'] or predecessors[successor] != {name}:
                continue
            block['body'] = block['body'][:target.start()] + other['note'] + '\n' + other['body']
            merged[successor] = name
            removed.add(successor)
            del blocks[successor]
            found = True
            break
        if not found:
            break
    # Rebuild from original function spans: merged functions keep the first name
    # and reference labels of every joined block, so parity failures stay traceable.
    changes = defaultdict(list)
    for path, text in files.items():
        for m in FUNCTION.finditer(text):
            start = text.rfind('// Reference step ', 0, m.start())
            end = closing(text, m.end() - 1) + 1
            if m[1] in removed:
                replacement = ''
            else:
                block = blocks[m[1]]
                replacement = block['note'] + '\n' + text[m.start():m.end()] + block['body'] + '}'
            changes[path].append((start, end, replacement))
        for a, b, replacement in sorted(changes[path], reverse=True):
            text = text[:a] + replacement + text[b:]
        if path.name == 'KernelFlow.kt':
            for name in removed:
                tag = name[0].upper() + name[1:]
                text = re.sub(r'(?m)^\s*' + tag + r',\s*\n', '', text)
                text = re.sub(r'(?m)^\s*KernelStep\.' + tag + r' ->\s*' + name + r'\(\)\s*\n', '', text)
        path.write_text(text, encoding='utf-8')
    return len(removed)


def structure_breakpoint_sort(directory):
    """Recover the insertion-sort loop, preserving its reference state writes."""
    path = directory / 'KernelProjection.kt'
    text = path.read_text(encoding='utf-8')
    if 'internal fun CurlMeshKernel.beginBreakpointInsertion()' not in text:
        return 0
    removed = {'advanceBreakpointSort', 'beginBreakpointInsertion', 'compareBreakpointInsertion',
               'shiftBreakpointForInsertion', 'finishBreakpointInsertion',
               'selectBreakpointInsertion', 'storeSortedBreakpoint'}
    for m in reversed(list(FUNCTION.finditer(text))):
        if m[1] in removed:
            a = text.rfind('// Reference step ', 0, m.start())
            text = text[:a] + text[closing(text, m.end() - 1) + 1:]
    text = text.replace('return KernelStep.BeginBreakpointInsertion',
                        'sortProjectedBreakpoints()\n    return KernelStep.FinishBreakpointSort')
    path.write_text(text, encoding='utf-8')
    flow = directory / 'KernelFlow.kt'
    text = flow.read_text(encoding='utf-8')
    for name in removed:
        tag = name[0].upper() + name[1:]
        text = re.sub(r'(?m)^\s*' + tag + r',\s*\n', '', text)
        text = re.sub(r'(?m)^\s*KernelStep\.' + tag + r' ->\s*' + name + r'\(\)\s*\n', '', text)
    flow.write_text(text, encoding='utf-8')
    return len(removed)


def analyze(body, values, locals_, callee_reads=None, callee_writes=None):
    """Reads occur before writes, including aliasing arguments of vector methods."""
    ends = list(statements(body))
    writes = {}
    for m in re.finditer(r'(?m)^    (\w+)\s*=\s*(?!=)', body):
        if m[1] in values:
            end = next(p for p in ends if p > m.end())
            writes[m.start(1)] = (m, end)
    events = []
    for token in IDENT.finditer(body):
        name = token.group()
        if name not in values:
            continue
        value = values[name]
        if value.kind != 'KernelVector':
            if token.start() in writes:
                events.append((writes[token.start()][1], 'write', value.bits(), name, token.start()))
            elif name not in locals_:
                events.append((token.start(), 'read', value.bits(), name, token.start()))
            continue
        call = re.match(r'\.(\w+)\(', body[token.end():])
        if not call:
            events.append((token.start(), 'read', value.bits(), name, token.start()))
            continue
        opening = token.end() + call.end() - 1
        end = closing(body, opening)
        args = arguments(body[opening + 1:end])
        method = call[1]
        if method in ('double', 'float', 'longBits', 'wordBits', 'halfBits'):
            lane = args[0]
            known = lane.isdigit()
            member = Binding(name, value.kind, value.slot, method, '')
            mask = member.bits(int(lane)) if known else value.bits()
            if len(args) == 1:
                events.append((token.start(), 'read', mask, name, token.start()))
            elif known:
                events.append((end + 1, 'write', mask, name, token.start()))
            # Unknown indexed writes cannot kill any particular byte in liveness.
        elif method in ('set', 'clear', 'load', 'unpackHalfColorFrom', 'multiplyColorsFrom'):
            events.append((end + 1, 'write', value.bits(), name, token.start()))
        elif method == 'packHalfColorFrom':
            events.append((end + 1, 'write', Binding(name, value.kind, value.slot, 'longBits', '').bits(), name, token.start()))
        elif method in ('store', 'copy'):
            events.append((token.start(), 'read', value.bits(), name, token.start()))
        else:
            raise ValueError(f'Unknown vector operation: {method}')
    for name, mask in (callee_reads or {}).items():
        for match in re.finditer(r'\b' + name + r'\(\)', body):
            events.append((match.start(), 'read', mask, '', match.start()))
    for name, mask in (callee_writes or {}).items():
        # Only an unconditional standalone call kills incoming storage. Calls
        # inside a conditional/expression remain conservative.
        for match in re.finditer(r'(?m)^    ' + name + r'\(\)[ \t]*$', body):
            events.append((match.end(), 'write', mask, '', match.start()))
    return sorted(events, key=lambda e: (e[0], e[1])), writes


def live_storage(events, successors):
    incoming = dict.fromkeys(events, 0)
    changed = True
    while changed:
        changed = False
        for name in reversed(events):
            live = 0
            for target in successors[name]:
                live |= incoming[target]
            for _, kind, mask, *_ in reversed(events[name]):
                live = live | mask if kind == 'read' else live & ~mask
            if live != incoming[name]:
                incoming[name] = live
                changed = True
    return incoming


def shared_reads(body, values):
    """Conservative helper inputs; assigning an output is not an input read."""
    mask = 0
    events, _ = analyze(body,values,{})
    for _,kind,bits,*_ in events:
        if kind=='read':
            mask |= bits
    return mask


def shared_writes(body, values):
    """Definite scalar outputs of a straight-line helper, never conditional stores."""
    code = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/', '', body)
    if re.search(r'\b(if|else|when|for|while|do|return|try|catch|finally)\b|[{}]', code):
        return 0
    mask = 0
    for match in re.finditer(r'(?m)^    (\w+)\s*=\s*(?!=)', code):
        if match[1] in values and values[match[1]].kind != 'KernelVector':
            mask |= values[match[1]].bits()
    return mask


def observable_writes(events, live):
    keep = set()
    for _, kind, mask, _, pos in reversed(events):
        if kind == 'read':
            live |= mask
        else:
            if mask & live:
                keep.add(pos)
            live &= ~mask
    return keep


TEMPORARY = re.compile(r'(?:projection|surface|shadow|penumbra|face|draw)(?:Value|Address|Bits|Color)\d*$')
DECLARATION = re.compile(r'\s*val (\w+): (Double|Float|Long) = ([\s\S]+?)\s*$')


def simplify(body, values):
    """Remove identities and adjacent pure temporaries; never reassociate math."""
    aliases = dict((name, value.slot) for name, value in values.items() if value.kind == 'KernelVector')
    aliases.update((m[1], int(m[2])) for m in re.finditer(r'val (\w+) = vectorValue\((\d+)\)', body))
    def remove_copy(m):
        return '' if aliases.get(m[1], -1) == aliases.get(m[2], -2) else m[0]
    body = re.sub(r'(?m)^    (\w+)\.set\((\w+)\)\s*\n', remove_copy, body)
    changed = True
    while changed:
        body = clean_parentheses(body)
        changed = False
        ends = [0] + list(statements(body))
        chunks = [body[a:b] for a, b in zip(ends, ends[1:])]
        for i, chunk in enumerate(chunks):
            match = DECLARATION.fullmatch(chunk)
            if not match or not TEMPORARY.fullmatch(match[1]):
                continue
            name, kind, expression = match.groups()
            pattern = re.compile(r'\b' + name + r'\b')
            literal = re.fullmatch(r'-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fL]?', expression)
            uses = sum(len(pattern.findall(c)) for c in chunks) - 1
            if literal:
                # Preserve expected types of context-typed integer literals.
                if expression.lstrip('-').isdigit():
                    expression += {'Long': 'L', 'Double': '.0', 'Float': 'f'}[kind]
                for j in range(i + 1, len(chunks)):
                    chunks[j] = pattern.sub(expression, chunks[j])
            elif uses == 1 and i + 1 < len(chunks) and pattern.search(chunks[i + 1]):
                # Local copies, reads and math are pure. Retained writes may not
                # move across another read of the shared vector storage.
                expression = clean_parentheses(expression)
                if 'retain' in expression or len(re.sub(r'\s+', ' ', expression)) > 100 or '{' in expression:
                    continue
                chunks[i + 1] = pattern.sub('(' + expression + ')', chunks[i + 1])
            else:
                continue
            chunks[i] = ''
            body = ''.join(chunks)
            changed = True
            break
    # Put vector aliases next to their first actual operation, not in a large
    # register-like preamble. Resolving a stable vector reference reads no bits.
    declarations = list(re.finditer(r'(?m)^    val (\w+) = vectorValue\((\d+)\)\n', body))
    moved = []
    for m in declarations:
        moved.append((m[1], m[0]))
    for m in reversed(declarations):
        body = body[:m.start()] + body[m.end():]
    ends = [0] + list(statements(body))
    chunks = [body[a:b] for a, b in zip(ends, ends[1:])]
    for name, declaration in moved:
        for i, chunk in enumerate(chunks):
            if re.search(r'\b' + name + r'\b', chunk):
                chunks[i] = declaration + chunk
                break
    return clean_parentheses(''.join(chunks))


def clean_parentheses(text):
    """Drop redundant grouping only; retain binary expression evaluation order."""
    while True:
        stack = []
        changed = False
        for p, c in enumerate(text):
            if c == '(':
                stack.append(p)
            elif c == ')' and stack:
                start = stack.pop()
                before = text[:start].rstrip()
                after = text[p + 1:].lstrip()
                # Parentheses of calls, conditions and declarations are syntax.
                if before and (before[-1].isalnum() or before[-1] in '_)]'):
                    continue
                inner = text[start + 1:p].strip()
                atom = re.fullmatch(r'[A-Za-z_]\w*|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fL]?', inner)
                duplicate = inner.startswith('(') and closing(inner, 0) == len(inner) - 1
                call = re.match(r'[A-Za-z_][\w.]*\(', inner)
                whole_call = call and closing(inner, call.end() - 1) == len(inner) - 1
                argument = before.endswith(('(', ',')) and after.startswith((')', ','))
                rhs = before.endswith('=') and (p + 1 == len(text) or text[p + 1] == '\n')
                if atom or duplicate or whole_call or argument or rhs:
                    text = text[:start] + text[start + 1:p] + text[p + 1:]
                    changed = True
                    break
        if not changed:
            return text


def localize(directory=DEST):
    directory = Path(directory)
    names = re.compile(r'\b(' + '|'.join(SORT_NAMES) + r')\b')
    for path in directory.glob('*.kt'):
        text = path.read_text(encoding='utf-8')
        renamed = names.sub(lambda m: SORT_NAMES[m[0]], text)
        if text != renamed:
            path.write_text(renamed, encoding='utf-8')
    structured = structure_breakpoint_sort(directory)
    merged = compact_steps(directory)
    value_file = directory / 'KernelValues.kt'
    header, values = bindings(value_file.read_text(encoding='utf-8'))
    files = {p: p.read_text(encoding='utf-8') for p in directory.glob('*.kt') if p != value_file}
    # Structured helpers can read shared slots. Their union is conservative at
    # a call site, including loop-carried reads and partially overwritten lanes.
    callee_reads = {}
    callee_writes = {}
    for text in files.values():
        for m in re.finditer(r'internal fun CurlMeshKernel\.(\w+)\(\)(?:: \w+\??)?\s*\{', text):
            body = text[m.end():closing(text, m.end() - 1)]
            callee_reads[m[1]] = shared_reads(body, values)
            callee_writes[m[1]] = shared_writes(body, values)
    blocks = {}
    spans = {}
    owners = defaultdict(set)
    for path, text in files.items():
        claimed = []
        for m in FUNCTION.finditer(text):
            end = closing(text, m.end() - 1)
            name = m[1]
            blocks[name] = text[m.end():end]
            spans[name] = (path, m.end(), end)
            claimed.append((m.start(), end + 1))
            for token in IDENT.findall(blocks[name]):
                if token in values:
                    owners[token].add(name)
        external = text
        for a, b in reversed(claimed):
            external = external[:a] + external[b:]
        for token in IDENT.findall(external):
            if token in values:
                owners[token].add(None)
    local = {}
    for name, value in values.items():
        if len(owners[name]) != 1 or None in owners[name]:
            continue
        block = next(iter(owners[name]))
        body = blocks[block]
        first = re.search(r'\b' + name + r'\b', body)
        if value.kind == 'KernelVector':
            local[name] = block
        elif re.match(r'\s*=\s*(?!=)', body[first.end():]):
            # A loop-carried value read on its first RHS must stay shared.
            end = next(p for p in statements(body) if p > first.end())
            if not re.search(r'\b' + name + r'\b', body[first.end():end]):
                local[name] = block
    events, writes, successors = {}, {}, {}
    for name, body in blocks.items():
        events[name], writes[name] = analyze(body, values, local, callee_reads, callee_writes)
        successors[name] = [target[0].lower() + target[1:] for target in re.findall(r'KernelStep\.(\w+)', body)
                            if target not in ('Complete', 'Invalid')]
    incoming = live_storage(events, successors)
    replacements = defaultdict(list)
    retained = 0
    for name, body in blocks.items():
        live = 0
        for target in successors[name]:
            live |= incoming[target]
        keep = observable_writes(events[name], live)
        prefix = []
        for value, owner in local.items():
            if owner == name and values[value].kind == 'KernelVector':
                prefix.append(f'    val {value} = vectorValue({values[value].slot})')
        changes = []
        seen = set()
        counts = defaultdict(int)
        for m, _ in writes[name].values():
            counts[m[1]] += 1
        for pos, (m, end) in writes[name].items():
            value = m[1]
            if value not in local:
                continue
            expression = body[m.end():end].strip()
            if pos in keep:
                expression = values[value].retained(expression)
                retained += 1
            else:
                expression = values[value].local_value(expression)
            if value not in seen:
                declaration = 'var' if counts[value] > 1 else 'val'
                expression = f'{declaration} {value}: {values[value].kind} = {expression}'
                seen.add(value)
            else:
                expression = f'{value} = {expression}'
            changes.append((m.start(), end, '    ' + expression + '\n'))
        for a, b, replacement in reversed(changes):
            body = body[:a] + replacement + body[b:]
        if prefix:
            body = '\n' + '\n'.join(prefix) + body
        path, a, b = spans[name]
        replacements[path].append((a, b, simplify(body, values)))
    for path, text in files.items():
        for a, b, body in sorted(replacements[path], reverse=True):
            text = text[:a] + body + text[b:]
        path.write_text(text, encoding='utf-8')
    remaining = [v.source for n, v in values.items() if n not in local and owners[n]]
    header = header.replace('Semantic views of live values.', 'Shared views of values that cross stage boundaries.')
    value_file.write_text(header + ''.join(remaining), encoding='utf-8')
    # Emit only bridges actually needed by observable packed-lane writes.
    used = set()
    for path in directory.glob('*.kt'):
        if path.name != 'KernelRetainedValues.kt':
            used.update(re.findall(r'\bretain(Integer|Double|Float|LongBits|WordBits|HalfBits)\(', path.read_text(encoding='utf-8')))
    helpers = ['// Generated by tools/CurlKernelLocals.py.\npackage org.peyilo.ioscurl.mesh.kernel\n',
               '/** Preserve the few local writes whose packed lanes remain observable. */']
    for suffix in sorted(used):
        kind = suffix if suffix in ('Double', 'Float') else 'Long'
        method = suffix[0].lower() + suffix[1:]
        store = 'integerValue(slot, value)' if suffix == 'Integer' else f'vectorValue(slot).{method}(0, value)'
        result = f'vectorValue(slot).{method}(0)' if suffix in ('WordBits', 'HalfBits') else 'value'
        helpers.append(f'internal fun CurlMeshKernel.retain{suffix}(slot: Int, value: {kind}): {kind} {{\n    {store}\n    return {result}\n}}\n')
    (directory / 'KernelRetainedValues.kt').write_text('\n'.join(helpers), encoding='utf-8')
    result = dict(bindingsBefore=len(values), localized=len(local), shared=len(remaining), retainedWrites=retained,
                  mergedSteps=merged, structuredSteps=structured)
    return result


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=DEST)
    args = parser.parse_args()
    print(json.dumps(localize(args.directory), indent=2))
