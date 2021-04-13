import re

def test_splits():
    splitter = re.compile("[-.`'\u2019\\s]+", re.UNICODE | re.IGNORECASE)

    res = splitter.split("Saint Bob's Pond")
    print(res)
    res = splitter.split("Saint Bob' Pond")
    print(res)
    res = splitter.split("Sant' Bob Pond")
    print(res)

    replacements = {}

    term = 'saint'
    replacements[term] = 'st. '
    pat = f"({term}[-`'\u2019\s]+)"
    regex = re.compile(pat, re.UNICODE | re.IGNORECASE)

    test = 'Saint-Pryvé-Saint-Mesmin'
    repl = replacements[term].capitalize()
    nVar = regex.sub(repl, test)
    nVar = nVar.replace('-', ' ').strip()
    nVar = nVar.replace('  ', ' ')
    print(nVar)

test_splits()