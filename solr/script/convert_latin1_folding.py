import os
from unicodedata import name, lookup, bidirectional as bidi, normalize
from opensextant.utility import get_list
uni_array = []
ascii_array =[]
uni_mapping = dict()

# res = lookup("")
#x = bidi('\u0100')
#x = name('\u0100')

# for ch in []
# a = normalize("NFD", '\u0100').encode("ascii", "ignore")

with open(os.path.join("solr7/gazetteer/conf/OpenSextant-Gazetteer-ASCIIFolding.txt"), "r", encoding="UTF-8") as fh:
    for line in fh:
        text = line.strip()
        if not text or  text.startswith("#"):
            continue

        if "=>" not in text:
            continue
        mapping = get_list(text, delim='=>')
        # print(mapping[0], mapping[1])
        u = mapping[0].replace('"', '')
        a = mapping[1].replace('"', '')
        uni_mapping[u] = a
        uni_array.append(mapping[0])
        ascii_array.append(mapping[1])

# print(repr(uni_array))
# print(repr(ascii_array))
print(uni_mapping)
