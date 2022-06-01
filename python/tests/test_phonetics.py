# -*- coding: utf-8 -*-

from opensextant.phonetics import phonetic_code, phonetic_redux, \
    get_phonetic_initials, get_phonetic_phrase, \
    match_filter_phonetically, match_phonetically, phonetic_params
from opensextant.utility import levenshtein_distance
from opensextant.advas_phonetics import caverphone, soundex, metaphone


def test_case(msg, truth, test):
    is_good = truth == test
    print(u"\n{:<40}\t{}\t{}\t{}".format(msg, truth, test, is_good))
    return is_good

def test_case2(msg, is_good):
    print(u"\n{:<40}\t\t{}".format(msg, is_good))

print("Advas Phonetics tests")
print("================================")
terms = ["Salah ad Din", "Philadelphia"]
for t in terms:
    print(f"\nTerm {t}")
    print("\tCaverphone\t", caverphone(t))
    print("\tSOUNDEX  \t", soundex(t))
    print("\tMetaphone\t", metaphone(t))

print()
print("Running Tests and demonstrations")
print("================================")
print("\n{:<40}\t{}\t{}\t{}".format("DESCRIPTION", "TARGET", "TEST", "PASS"))

test_case(u'AH - "Salah ad Din" vs. "Salad Din', 'slhtn', phonetic_code('salah ad din'))
test_case(u'Consonance in Nino vs Niño', 'nn', phonetic_code(u'Niño'))
test_case(u'Punctuation in word "Pizza!"', 'ps', phonetic_code(u'pizza!'))

phrase = u"Ziraḩ' Dajla"
print(u'\nDiacritic test; what is phonetic('+phrase+')? =' + phonetic_code(phrase))

# # Lower case input  required.
test_case(u'Test Phonetic Initials', 'ab0', get_phonetic_initials('Alpha Bravo Zero'))

test_case(u'Test Phonetic Initials - lower case', 'ab0', get_phonetic_initials('Alpha Bravo Zero'.lower()))

# Not California Guitar Trio
# # Lower case input required.
test_case(u'Test NATO Phonetics - Expand CGT', 'charlie golf tango', get_phonetic_phrase('CGT'.lower()))

test_case(u'Match "filadelfia"', True, match_phonetically('philadelphia', 'filadelfia'))

# PH...
test_case(u'Metaphone("Philadelphia")', 'fltlf', phonetic_code('philadelphia'))

# P...
test_case(u'Metaphone("P*iladelphia")', 'pltlf', phonetic_code('piladelphia'))

# Get Phonetic
test_case('Reducing phonetics in "Terrazzo"', 'terazo', phonetic_redux('terrazzo'))

# Get Phonetic on term with repeating consonance
test_case('Generate alternate phonetic variation w/fewer consants', 'trs', phonetic_code(phonetic_redux('terrazzo')))

print("\nSimple Levenshtein edit distance")
for a, b in [
    ('abc', ''),
    ('', 'abc'),
    ('abc', 'xbc'),
    ('abc', 'abc')]:
    print("edit distance on ", a, b, " = ", levenshtein_distance(a, b))

print("\nDeeper look at transliterated terms")
print("===================================================")
text = 'Al Bayya'
target_text = u"Al-Baya'"
test_case("Repeated vowels in {} vs. {}".format(text, target_text), phonetic_code(text), phonetic_code(target_text))

print("\nPhonetic Matching on names with diacritics")
print("===================================================")

text = u'Karyat id al Fayyad'
ph1 = phonetic_code(text)
target_text = u'qaryat ‘īd al fayyāḑ'
ph2 = phonetic_code(target_text)

test_case(u'd vs. ḑ:  "%s" (%s) =? "%s" (%s)' % (text, ph1, target_text, ph2), ph1, ph2)

print("\nHueristic Phonetic Matching on transliterated names")
print("===================================================")
target_text = 'Abbattobad'
target = target_text.lower()
tgtlen = len(target)
ph1 = phonetic_code(target_text)
for t in ['Abatobbad', 'Abatttabahd']:
    ph2 = phonetic_code(t)
    test_case2(u'{} ({}) =? {} ({})'.format(target_text, ph1, t, ph2),
              match_filter_phonetically(target, tgtlen, t.lower(), len(t), 3, 5))

target_text = 'Dara Adam Khel'
target = target_text.lower()
tgtlen = len(target)
(mxdiff, mxedit) = phonetic_params(tgtlen)

ph1 = phonetic_code(target_text)
for t in ['Dara Adam Khel', 'Dar AdamKhel', 'Dir AdamKhel', 'Dir Adam Khel', 'Dera Adamkhel']:
    ph2 = phonetic_code(t)
    test_case2('{} ({}) =? {} ({})'.format(target_text, ph1, t, ph2),
              match_filter_phonetically(target, tgtlen, t.lower(), len(t), mxdiff, mxedit))
