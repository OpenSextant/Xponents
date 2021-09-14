# -*- coding: utf-8 -*-
"""
Geocoding Phonetics Library

:created Created on Mar 15, 2012
:author: ubaldino
:copyright:  MITRE Corporation, (c) 2010-2012

Requirements: advas.phonetics library is used here; but a modified version of it is included in this package.
"""

from string import ascii_lowercase, digits

from opensextant.utility import levenshtein_distance
# Metaphone via Advanced Search (advas);  Modified by Marc Ubaldino
#
from .advas_phonetics import metaphone

# Very basic consonnance matching on word intials.
# Others not yet implemented:  'd' = 't'
KA_CONSONNANCE = {"q", "k", "c"}
PH_CONSONNANCE = {"f", "p"}  # Yeah this is actually PH, not "P".
JA_CONSONNANCE = {"g", "j", "y"}
SA_CONSONNANCE = {"s", "c", "z"}
XA_CONSONNANCE = {"s", "z"}
WA_CONSONNANCE = {"w", "v"}

ARRAY_OF_PHONETICS = [KA_CONSONNANCE, PH_CONSONNANCE,
                      JA_CONSONNANCE, SA_CONSONNANCE,
                      XA_CONSONNANCE, WA_CONSONNANCE]

# Reduce the consanance 
# 
#   Sacco River, ME 
#   Saco River?
# 
#   Abbattobad or
#   Abbatobod  or
#   Abatobod  ?
#
REDUCE_CONSONNANCE = {"bb", "cc", "dd", "ff", "gg", "kk", "ll", "mm",
                      "nn", "pp", "qq", "rr", "ss", "tt", "vv", "xx", "zz"}


class PhoneticMap:
    """ Convenience class to organize a single Phoneme to a list of names (which have the same phoneme)
    """

    def __init__(self, p):
        self.phoneme = p
        self.names = set([])

    def add(self, name):
        self.names.add(name)


def phonetic_redux(tok):
    t = tok
    for DUP in REDUCE_CONSONNANCE:
        t = t.replace(DUP, DUP[0])
    return t


def phonetic_code(tok):
    """
    An application of Advas phonetics library
    Metaphone appears to generate a fourth of the matches Caverphone does.
    ... that is Caverphone is looser, and noisier similarity matching.
    CAVEAT:  If you change phonetics, you must RE-Pickle
    
    WINNER: metaphone.
    """
    if tok is None:
        return None
    # Fix input tokens:
    return metaphone(tok)


def match_phonetically(a, b):
    """
    match_phonetically( a, b ) attempts to match 
    words by the phonetic similarity of their initials.
    Limitation:  F and PH are one intended match, but for now F =? P suffices. 
    """

    if not a or not b:
        return False

    a0 = a[0]
    b0 = b[0]
    if a0 == b0:
        return True

    for phset in ARRAY_OF_PHONETICS:
        if (a0 in phset) and (b0 in phset):
            #  AH,.. Phonetic equivalence by their CONSONNANCE
            # Find the first match and return True 
            return True

    # No phonetic match here.
    return False


# =======================================
# //////////////////////////////////////
#   A. Kazura, M. Ubaldino 2012
#   phonetic alphabet conversions.
#
#  Reference:   http://www.osric.com/chris/phonetic.html
#
#  Western Union is difficult becuase it uses city names primarily.
# 
#  NATO implemented here:
# =======================================
phonetic_alphabet = ["alpha", "bravo", "charlie", "delta", "echo",
                     "foxtrot", "golf", "hotel", "india", "juliet",
                     "kilo", "lima", "mike", "november", "oscar",
                     "papa", "quebec", "romeo", "sierra", "tango",
                     "uniform", "victor", "whiskey", "xray", "yankee", "zulu",
                     ]
phonetic_numbers = ["zero", "one", "two", "three", "four",
                    "five", "six", "seven", "eight", "nine"
                    ]

# Alpha to Word
phonetic_a2w = dict(zip(ascii_lowercase, phonetic_alphabet))
phonetic_a2w.update(dict(zip(digits, phonetic_numbers)))

# Word to Alpha
phonetic_w2a = dict(zip(phonetic_alphabet, ascii_lowercase))
phonetic_w2a.update(dict(zip(phonetic_numbers, digits)))

# additions
phonetic_w2a["x-ray"] = "x"
phonetic_w2a["nought"] = "0"
phonetic_w2a["not"] = "0"


def get_phonetic_phrase(word):
    """ Convert a code word into its expanded phonetic spelling.
    e.g., given TB generate tango bravo
    input is assumed lowercase.
    
    :param word: lower case code word
    """
    # Get the value for x or return x
    #   '#' is not in a2w map, so return it as-is.
    #
    phr = map(lambda x: phonetic_a2w.get(x, x), word)
    return " ".join(phr)


def get_phonetic_initials(phrase):
    """ 
    Convert a word into its acronym.  You would only do this if you knew
    you had a phonetic spelling, e.g., Tango Bravo = TB
    """
    words = phrase.lower().split()
    phon = []
    for w in words:
        if w in phonetic_w2a:
            # add first initial keyed to this word.
            #  'a' for Alpha
            #  '0' for Zero, Not, or Nought
            phon.append(phonetic_w2a.get(w))
        else:
            phon.append(' ')
            phon.append(w)
            phon.append(' ')

    return "".join(phon)


# length diff, max edit dist, by 5's
# increasing term size in terms less than 10 chars guidelines:
#    length-diff should not be more than 1 char
#    but edit dist should be linear to allow increasing for vowel variations, mainly
# With longer words both consonants and vowels will have an increasing entropy
# Approximately edit dist threshold is 2x length difference threshold. 
_phonetic_params_block = 3
_phonetic_params = {
    0: (0, 0),  # 1-3 chars
    1: (0, 1),  # 4-6 chars
    2: (1, 2),  # 10-15 chars
    3: (1, 3),  # etc.
    4: (2, 4),
    5: (3, 5),
    6: (4, 6),
    7: (4, 7),
    8: (5, 8),
    9: (5, 9),
    10: (6, 10),
    11: (6, 11),
    12: (7, 12),
    13: (7, 13),
    14: (8, 14),
    15: (8, 15)  # 45-47 chars.
}


def phonetic_params(termlen):
    """
    get params for a given term length
    :param termlen: term len
    :return:
    """
    lx = termlen / _phonetic_params_block
    p = _phonetic_params.get(lx)
    if lx > 15 or not p:
        # 5 x 10 = 50, 50 character phrase? What sort of variability
        return _phonetic_params.get(15)

    return p


def match_filter_phonetically(target, targetlen, test, testlen, max_len_diff, max_edit_dst):
    """  For performance reasons we assume you have lower case versions of target and test
    and lengths for both.
    
    Does test match target phonetically?  Usage: Given target, find test in [a, b, c, d...] that match target
    
    :param target:      thing you want to match to.
    :param targetlen:
    :param test:        a test.
    :param testlen:
    :param max_len_diff:  basic length filter
    :param max_edit_dst:  Finally assess edit distance of text
    """

    # FAIL only if one test fails
    # Otherwise attempt all tests -- that is do not PASS based on one conditional here.

    # Filter just by length alone.
    if abs(testlen - targetlen) > max_len_diff:
        return False

    # Filter because phonetic nature of initial syllable or consonants is not valid
    #   'Ka' or 'Qa' are equivlent, but maybe not 'Cua'
    if not match_phonetically(test, target):
        return False

    # Finally if all other filters pass, then check if edit distance of full string makes sense.
    editdst = levenshtein_distance(test, target)
    if editdst >= max_edit_dst:
        return False

    return True
