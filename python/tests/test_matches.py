from opensextant import TextMatch, reduce_matches

arr = [

    # To confirm... are offsets inclusive?
    # span  from char = 10, 11, 12  - is three characters, 12-10 = 2, though.
    #       so length is end - start + 1, right?
    #  10    15
    #   abcdef
    TextMatch("abc", 10, 12),
    TextMatch("bc", 11, 12),
    TextMatch("def", 13, 16),
    TextMatch("abc", 10, 12),
    TextMatch("bcde", 11, 14),

]

reduce_matches(arr)
print("X1, X2, DUP, sUB, OVERLAP")
for m in arr:
    print(m.start, m.end, m.is_duplicate, m.is_submatch, m.is_overlap)
