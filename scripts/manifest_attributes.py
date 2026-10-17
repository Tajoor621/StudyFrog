"""Read aapt2 xmltree attributes across symbolic and typed output formats."""
import json
import re

def has_attribute(text: str, key: str, expected: str | bool) -> bool:
    pattern = re.compile(r'^\s*A:\s+(?:[^\s=]+:)?' + re.escape(key) + r'(?:\([^)]*\))?=(.*)$')
    for line in text.splitlines():
        match = pattern.match(line)
        if not match:
            continue
        value = re.sub(r'^\(type\s+0x[0-9a-fA-F]+\)\s*', '', match.group(1).strip())
        if isinstance(expected, bool):
            token = value.split()[0] if value else ''
            values = {'true': True, 'false': False, '0xffffffff': True, '0x1': True, '0x0': False,
                      '"true"': True, '"false"': False}
            return token.lower() in values and values[token.lower()] == expected
        try:
            parsed, _ = json.JSONDecoder().raw_decode(value)
            return isinstance(parsed, str) and parsed == expected
        except ValueError:
            return False
    return False
