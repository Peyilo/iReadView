"""Remove unused pure local declarations from maintained/generated Kotlin stages.

Only function/block-local bindings are considered. Unknown calls are kept;
observable packed-lane writes become expression statements rather than deleted.
The pass does not infer memory stores to be dead from missing symbolic names.
"""
from pathlib import Path
import re


def remove_unreachable_steps(directory):
    from CurlKernelLocals import FUNCTION, closing
    from CurlKernelAlgorithms import remove_steps
    directory = Path(directory)
    blocks = {}
    for path in directory.glob('*.kt'):
        text = path.read_text(encoding='utf-8')
        for match in FUNCTION.finditer(text):
            blocks[match[1]] = text[match.end():closing(text, match.end()-1)]
    reached, pending = set(), ['prepareMaterialsAndBounds']
    while pending:
        name = pending.pop()
        if name in reached:
            continue
        reached.add(name)
        for target in re.findall(r'KernelStep\.(\w+)', blocks[name]):
            if target not in ('Complete', 'Invalid'):
                pending.append(target[0].lower()+target[1:])
    unused = set(blocks)-reached
    remove_steps(directory, unused)
    if not any('KernelStep.Invalid' in blocks[name] for name in reached):
        flow = directory/'KernelFlow.kt'
        text = flow.read_text(encoding='utf-8')
        text = re.sub(r'(?m)^\s*Invalid,\s*\n', '', text)
        text = re.sub(r'(?m)^\s*KernelStep\.Invalid -> error\("Invalid curl geometry"\)\s*\n', '', text)
        flow.write_text(text, encoding='utf-8')
    return sorted(unused)


def remove_unused_helpers(directory, source_root=None):
    directory = Path(directory)
    root = Path(source_root) if source_root is not None else next(parent for parent in directory.parents if parent.name == 'kotlin')
    corpus = '\n'.join(p.read_text(encoding='utf-8') for p in root.rglob('*.kt'))
    path = directory/'KernelPrimitives.kt'
    text = path.read_text(encoding='utf-8')
    unused = []
    for name in ('writeHalfWord', 'sqrtFloat'):
        if len(re.findall(r'\b'+name+r'\b', corpus)) == 1:
            text = re.sub(r'(?m)^internal fun (?:CurlMeshKernel\.)?'+name+r'\([^\n]+\n', '', text)
            unused.append(name)
    path.write_text(text, encoding='utf-8')
    return unused


def prune_unused_views(directory):
    from CurlKernelLocals import bindings
    directory = Path(directory)
    path = directory/'KernelValues.kt'
    header, values = bindings(path.read_text(encoding='utf-8'))
    corpus = '\n'.join(p.read_text(encoding='utf-8') for p in directory.glob('*.kt') if p != path)
    used = set(re.findall(r'\b\w+\b', corpus))
    removed = sorted(set(values)-used)
    path.write_text(header+''.join(v.source for n,v in values.items() if n in used), encoding='utf-8')
    return removed

PURE = {'readDouble', 'readFloat', 'readLongBits', 'readUnsignedWord', 'readUnsignedByte',
        'vectorValue', 'integerValue', 'fma', 'abs', 'sqrt', 'min', 'max', 'ceil', 'floor',
        'halfFloat', 'floatHalf', 'toInt', 'toLong', 'toFloat', 'toDouble', 'toRawBits',
        'copy', 'isFinite', 'isNaN'}
LANES = {'double', 'float', 'longBits', 'halfBits', 'wordBits'}
DECLARATION = re.compile(r'(?m)^([ \t]+)(?:val|var) (\w+)(?:\s*:\s*[\w?.<>]+)?\s*=\s*')


def expression_end(text, start):
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        depth += (char in '([{') - (char in ')]}')
        if char == '\n' and depth == 0:
            return index + 1
    return len(text)


def is_pure(expression):
    from CurlKernelLocals import closing, arguments
    if any(token in expression for token in ('{', '}', '++', '--', '=')):
        return False
    for m in re.finditer(r'\b(\w+)\(', expression):
        if m[1] in LANES:
            end = closing(expression, m.end()-1)
            if len(arguments(expression[m.end():end])) != 1:
                return False
        elif m[1] not in PURE:
            return False
    return True


def clean_scope(body):
    from CurlKernelLocals import closing
    # Clean nested scopes first, without treating a class property as a local.
    index = 0
    count = 0
    while index < len(body):
        if body[index] == '{':
            end = closing(body, index)
            child, removed = clean_scope(body[index+1:end])
            body = body[:index+1] + child + body[end:]
            index += len(child) + 2
            count += removed
        else:
            index += 1
    changed = True
    while changed:
        changed = False
        for m in DECLARATION.finditer(body):
            if len(re.findall(r'\b' + m[2] + r'\b', body)) != 1:
                continue
            end = expression_end(body, m.end())
            expression = body[m.end():end].strip()
            if is_pure(expression):
                replacement = ''
            elif re.fullmatch(r'retain(?:Double|Float|Integer|LongBits|WordBits|HalfBits)\([\s\S]+\)', expression):
                replacement = m[1] + expression + '\n'
            else:
                continue
            body = body[:m.start()] + replacement + body[end:]
            count += 1
            changed = True
            break
    return body, count


def remove_unused_locals(directory):
    from CurlKernelLocals import closing
    count = 0
    for path in Path(directory).glob('*.kt'):
        text = path.read_text(encoding='utf-8')
        spans = []
        for m in re.finditer(r'\bfun\s+[\w.<>]+\(', text):
            params = closing(text, m.end()-1)
            tail = re.match(r'\s*(?::[^={]+)?\s*\{', text[params+1:])
            if tail:
                opening = params + 1 + tail.end()-1
                end = closing(text, opening)
                spans.append((opening+1, end))
        for start, end in reversed(spans):
            body, removed = clean_scope(text[start:end])
            text = text[:start] + body + text[end:]
            count += removed
        path.write_text(text, encoding='utf-8')
    return count


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    print({'unusedLocalsRemoved': remove_unused_locals(parser.parse_args().directory)})
