#!/usr/bin/env python3
"""Kotlin brace/paren/bracket balance checker (template-string aware).

Ignores content inside string literals, char literals, and comments, while
counting structural (), {}, and []. Also tracks basic line sanity.
Exit 1 if any file is unbalanced.
"""
import sys
from pathlib import Path

def strip_and_count(text: str):
    """Return counts of (), {}, [] outside strings/comments, or None state info."""
    stack = []
    pairs = {')': '(', '}': '{', ']': '['}
    i, n = 0, len(text)
    mode = 'code'  # code | line_comment | block_comment | string | triple_string | char
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if mode == 'code':
            if c == '/' and nxt == '/':
                mode = 'line_comment'; i += 2; continue
            if c == '/' and nxt == '*':
                mode = 'block_comment'; i += 2; continue
            if c == '"' and text[i:i + 3] == '"""':
                mode = 'triple_string'; i += 3; continue
            if c == '"':
                mode = 'string'; i += 1; continue
            if c == "'":
                mode = 'char'; i += 1; continue
            if c in '({[':
                stack.append((c, i))
            elif c in ')}]':
                if not stack or stack[-1][0] != pairs[c]:
                    line = text.count('\n', 0, i) + 1
                    return stack, f"mismatched '{c}' at offset {i} (line {line})"
                stack.pop()
        elif mode == 'line_comment':
            if c == '\n':
                mode = 'code'
        elif mode == 'block_comment':
            if c == '*' and nxt == '/':
                mode = 'code'; i += 2; continue
        elif mode == 'string':
            if c == '\\':
                i += 2; continue
            if c == '"':
                mode = 'code'
        elif mode == 'triple_string':
            if c == '\\':
                i += 2; continue
            if text[i:i + 3] == '"""':
                mode = 'code'; i += 3; continue
        elif mode == 'char':
            if c == '\\':
                i += 2; continue
            if c == "'":
                mode = 'code'
        i += 1
    if mode not in ('code', 'line_comment'):
        return stack, f"unterminated {mode}"
    return stack, None

def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java')
    files = sorted(root.rglob('*.kt'))
    bad = 0
    for f in files:
        text = f.read_text(encoding='utf-8')
        stack, err = strip_and_count(text)
        if err or stack:
            bad += 1
            print(f"FAIL {f}: {err or 'unclosed ' + str([s[0] for s in stack[-5:]])}")
    print(f"{'OK' if bad == 0 else 'FAILED'} — {len(files) - bad}/{len(files)} balanced")
    sys.exit(1 if bad else 0)

if __name__ == '__main__':
    main()
