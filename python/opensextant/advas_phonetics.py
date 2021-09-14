# -*- coding: utf-8 -*-
# ----------------------------------------------------------
# AdvaS Advanced Search 
# module for phonetic algorithms
#
# (C) 2002 - 2005 Frank Hofmann, Chemnitz, Germany
# email fh@efho.de
# ----------------------------------------------------------
#
# changed 2005-01-24
# 2012-01-01 MU adapted to support various Unicode transliterations in Metaphone
# 2021-03-25 MU migrated to Xponents here

import re


def soundex(term):
    """Return the soundex value to a string argument."""

    # Create and compare soundex codes of English words.
    #
    # Soundex is an algorithm that hashes English strings into
    # alpha-numerical value that represents what the word sounds
    # like. For more information on soundex and some notes on the
    # differences in implemenations visit:
    # http://www.bluepoof.com/Soundex/info.html
    #
    # This version modified by Nathan Heagy at Front Logic Inc., to be
    # compatible with php's soundexing and much faster.
    #
    # eAndroid / Nathan Heagy / Jul 29 2000
    # changes by Frank Hofmann / Jan 02 2005

    # generate translation table only once. used to translate into soundex numbers
    # table = string.maketrans('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ', '0123012002245501262301020201230120022455012623010202')
    table = "".maketrans('ABCDEFGHIJKLMNOPQRSTUVWXYZ', '01230120022455012623010202')

    # check parameter
    if not term:
        return "0000"  # could be Z000 for compatibility with other implementations
    # end if

    # convert into uppercase letters
    term = term.upper()
    first_char = term[0]

    # translate the string into soundex code according to the table above
    term = term[1:].translate(table)

    # remove all 0s
    term = term.replace("0", "")

    # remove duplicate numbers in-a-row
    str2 = first_char
    for x in term:
        if x != str2[-1]:
            str2 = str2 + x
    # end if
    # end for

    # pad with zeros
    str2 = str2 + "0" * len(str2)

    # take the first four letters
    return_value = str2[:4]

    # return value
    return return_value


# MCU: optimization -- put constant tables in global space, not functional space.
#
# build translation table
metaphone_table = {
    "ae": "e",
    "gn": "n",
    # "kn":"n",  -- Generalization for 'known' or 'knowles' => 'nwn' or 'nwls'; But not "Ken" or "Kane"=> "kn"
    "pn": "n",
    "wr": "n",
    "wh": "w"}

# define standard translation table
metaphone_std_trans = {
    "b": "b",
    "c": "k",
    "d": "t",
    "g": "k",
    "h": "h",
    "k": "k",
    "p": "p",
    "n": "n",
    "q": "k",
    "s": "s",
    "t": "t",
    "v": "f",
    "w": "w",
    "x": "ks",
    "y": "y",
    "z": "s"}

EMPTY_STRING = ''

# cStringIO is about 5% slower than normal + operation for short strings.
# from cStringIO import StringIO

re_sub_nonalpha = re.compile(u'[^a-zḑḩñşţz̧]')
re_sub_vowels = re.compile(u'[aeiou]')
vowels = {'a', 'e', 'i', 'o', 'u'}
re_CI = re.compile('c[iey]')
re_SCI = re.compile('sc[iey]')
re_DG = re.compile('dg[eyi]')
re_GHvowel = re.compile('gh[aeiouy]')
# vowels of various Latin forms, preceeding H, where H is followed by consonant.
re_vowelHvowel = re.compile(u'[aeiouyāīū][hḩ][^aeiouy]')
re_softH = re.compile('[csptg]h')
re_SIvowel = re.compile('si[ao]')
re_TIvowel = re.compile('ti[ao]')
re_shadowedW = re.compile('w[^aeiouy]')


def metaphone(text):
    """returns metaphone code for a given string"""

    # implementation of the original algorithm from Lawrence Philips
    # extended/rewritten by M. Kuhn
    # improvements with thanks to John Machin <sjmachin@lexicon.net>
    #
    # 2011-FEB
    # a) substantial perf improve by Marc Ubaldino <ubaldino@mitre.org> -- put regex in global space for lib. 2.5x faster
    # b) qualitative fixes:  vowel replacements occur as last step.
    # c) looking at oddball extended latin, e.g., ḑ,
    # d) improved repeated chars,  pizza, fayyad, etc.

    # i = 0
    if not text:
        # empty string ?
        return EMPTY_STRING
    # end if

    # extension #1 (added 2005-01-28)
    # convert to lowercase
    term = text.lower()

    # extension #2 (added 2005-01-28)
    # remove all non-english characters, first
    term = re_sub_nonalpha.sub('', term)
    if len(term) == 0:
        # nothing left
        return EMPTY_STRING
    # end if

    # extension #3 (added 2005-01-24)
    # conflate repeated letters
    firstChar = term[0]
    str2 = firstChar
    for x in term:
        if x != str2[-1]:
            str2 = str2 + x
    # end if
    # end for

    textnorm = str2
    # text = str2

    # term = str3
    term_length = len(textnorm)
    if term_length == 0:
        # nothing left
        return EMPTY_STRING
    # end if

    # define return value
    code = ''
    term = textnorm

    # check for exceptions
    if (term_length > 1):
        # get first two characters
        first_chars = term[0:2]

        kn_start = textnorm.startswith('kn')
        if first_chars in metaphone_table.keys() or kn_start:
            term = term[2:]
            if kn_start:
                code = 'n'
            else:
                code = metaphone_table[first_chars]
            term_length = len(term)
    # end if

    elif (term[0] == "x"):
        term = ""
        code = "s"
        term_length = 0
    # end if

    i = 0
    while (i < term_length):
        # init character to add, init basic patterns
        add_char = ""
        part_n_2 = ""
        part_n_3 = ""
        part_n_4 = ""
        part_c_2 = ""
        part_c_3 = ""

        # extract a number of patterns, if possible
        if (i < (term_length - 1)):
            part_n_2 = term[i:i + 2]

            if (i > 0):
                part_c_2 = term[i - 1:i + 1]
                part_c_3 = term[i - 1:i + 2]
        # end if
        # end if

        if (i < (term_length - 2)):
            part_n_3 = term[i:i + 3]
        # end if

        if (i < (term_length - 3)):
            part_n_4 = term[i:i + 4]
        # end if

        ch = term[i]

        # use table with conditions for translations
        if (ch == "b"):
            add_char = metaphone_std_trans["b"]
            if (i == (term_length - 1)):
                if (i > 0):
                    if (term[i - 1] == "m"):
                        add_char = ""
                # end if
            # end if
        # end if
        elif (ch == "c"):
            add_char = metaphone_std_trans["c"]
            if (part_n_2 == "ch"):
                add_char = "x"
            elif re_CI.search(part_n_2):
                add_char = "s"
            # end if

            if (part_n_3 == "cia"):
                add_char = "x"
            # end if

            if re_SCI.search(part_c_3):
                add_char = ""
        # end if

        elif (ch == "d" or ch == u'ḑ'):
            add_char = metaphone_std_trans["d"]
            if (re_DG.search(part_n_3)):
                add_char = "j"
        # end if

        elif (ch == "g"):
            add_char = metaphone_std_trans["g"]

            if (part_n_2 == "gh"):
                if (i == (term_length - 2)):
                    add_char = ""
            # end if
            elif (re_GHvowel.search(part_n_3)):
                add_char = ""
            elif (part_n_2 == "gn"):
                add_char = ""
            elif (part_n_4 == "gned"):
                add_char = ""
            elif re_DG.search(part_c_3):
                add_char = ""
            elif (part_n_2 == "gi"):
                if (part_c_3 != "ggi"):
                    add_char = "j"
            # end if
            elif (part_n_2 == "ge"):
                if (part_c_3 != "gge"):
                    add_char = "j"
            # end if
            elif (part_n_2 == "gy"):
                if (part_c_3 != "ggy"):
                    add_char = "j"
            # end if
            elif (part_n_2 == "gg"):
                add_char = ""
        # end if
        elif (ch == "h" or ch == u'ḩ'):
            add_char = metaphone_std_trans["h"]
            if (re_vowelHvowel.search(part_c_3)):
                add_char = ""
            elif (re_softH.search(part_c_2)):
                add_char = ""
        # end if
        elif (ch == "k"):
            add_char = metaphone_std_trans["k"]
            if (part_c_2 == "ck"):
                add_char = ""
        # end if
        elif (ch == "p"):
            add_char = metaphone_std_trans["p"]
            if (part_n_2 == "ph"):
                add_char = "f"
        # end if
        elif (ch == "q"):
            add_char = metaphone_std_trans["q"]
        elif (ch == "s" or ch == u'ş'):
            add_char = metaphone_std_trans["s"]
            if (part_n_2 == "sh"):
                add_char = "x"
            # end if

            if re_SIvowel.search(part_n_3):
                add_char = "x"
        # end if
        elif (ch == "t" or ch == u'ţ'):
            add_char = metaphone_std_trans["t"]
            if (part_n_2 == "th"):
                add_char = "0"
            # end if

            if (re_TIvowel.search(part_n_3)):
                add_char = "x"
        # end if
        elif (ch == "v"):
            add_char = metaphone_std_trans["v"]
        elif (ch == "w"):
            add_char = metaphone_std_trans["w"]
            if (re_shadowedW.search(part_n_2)):
                add_char = ""
        # end if
        elif (ch == "x"):
            add_char = metaphone_std_trans["x"]
        elif (ch == "y"):
            add_char = metaphone_std_trans["y"]
        elif (ch == "z" or ch == u'z̧'):
            add_char = metaphone_std_trans["z"]
        elif (ch == u'ñ'):
            add_char = metaphone_std_trans['n']
        else:
            # alternative
            add_char = ch
        # end if

        if add_char:
            code = code + add_char
        i += 1
    # end while

    # extension #4 (added 2005-01-24)
    # This was moved from before loop
    #  "mirance" was coming out as "mrnk"  not "mrns"
    #  So I refactored and retested all of this.  Vowels are to be stripped out after
    #  above patterns are run.
    # remove any vowels unless a vowel is the first letter
    # firstChar = str2[0]
    # str3 = firstChar
    # for x in str2[1:]:
    #	if x not in vowels:
    #		str3 = str3 + x
    # end if
    # end for
    # return metaphone code

    c0 = code[0]
    reduced_code = c0 + re_sub_vowels.sub('', code[1:])

    return reduced_code


def nysiis(term):
    """returns New York State Identification and Intelligence Algorithm (NYSIIS) code for the given term"""

    code = ""

    # i = 0
    term_length = len(term)

    if (term_length == 0):
        # empty string ?
        return code
    # end if

    # build translation table for the first characters
    table = {
        "mac": "mcc",
        "ph": "ff",
        "kn": "nn",
        "pf": "ff",
        "k": "c",
        "sch": "sss"
    }

    table_value_len = 0
    for table_entry in table.keys():
        table_value = table[table_entry]  # get table value
        table_value_len = len(table_value)  # calculate its length
        first_chars = term[0:table_value_len]
        if (first_chars == table_entry):
            term = table_value + term[table_value_len:]
            break
    # end if
    # end for

    # build translation table for the last characters
    table = {
        "ee": "y",
        "ie": "y",
        "dt": "d",
        "rt": "d",
        "rd": "d",
        "nt": "d",
        "nd": "d",
    }

    for table_entry in table.keys():
        table_value = table[table_entry]  # get table value
        table_entry_len = len(table_entry)  # calculate its length
        last_chars = term[(0 - table_entry_len):]
        # print last_chars, ", ", table_entry, ", ", table_value
        if (last_chars == table_entry):
            term = term[:(0 - table_value_len + 1)] + table_value
            break
    # end if
    # end for

    # initialize code
    code = term

    # transform ev->af
    code = re.sub(r'ev', r'af', code)

    # transform a,e,i,o,u->a
    code = re.sub(r'[aeiouy]', r'a', code)

    # transform q->g
    code = re.sub(r'q', r'g', code)

    # transform z->s
    code = re.sub(r'z', r's', code)

    # transform m->n
    code = re.sub(r'm', r'n', code)

    # transform kn->n
    code = re.sub(r'kn', r'n', code)

    # transform k->c
    code = re.sub(r'k', r'c', code)

    # transform sch->sss
    code = re.sub(r'sch', r'sss', code)

    # transform ph->ff
    code = re.sub(r'ph', r'ff', code)

    # transform h-> if previous or next is nonvowel -> previous
    occur = re.findall(r'([a-z]{0,1}?)h([a-z]{0,1}?)', code)
    # print occur
    for occur_group in occur:
        occur_item_previous = occur_group[0]
        occur_item_next = occur_group[1]

        if ((re.match(r'[^aeiouy]', occur_item_previous)) or (re.match(r'[^aeiouy]', occur_item_next))):
            if (occur_item_previous != ""):
                # make substitution
                code = re.sub(occur_item_previous + "h", occur_item_previous * 2, code, 1)
        # end if
    # end if
    # end for

    # transform w-> if previous is vowel -> previous
    occur = re.findall(r'([aeiouy]{1}?)w', code)
    # print occur
    for occur_group in occur:
        occur_item_previous = occur_group[0]
        # make substitution
        code = re.sub(occur_item_previous + "w", occur_item_previous * 2, code, 1)
    # end for

    # check last character
    # -s, remove
    code = re.sub(r's$', r'', code)
    # -ay, replace by -y
    code = re.sub(r'ay$', r'y', code)
    # -a, remove
    code = re.sub(r'a$', r'', code)

    # return nysiis code
    return code


def caverphone(term):
    """returns the language key using the caverphone algorithm 2.0"""

    # Developed at the University of Otago, New Zealand.
    # Project: Caversham Project (http://caversham.otago.ac.nz)
    # Developer: David Hood, University of Otago, New Zealand
    # Contact: caversham@otago.ac.nz
    # Project Technical Paper: http://caversham.otago.ac.nz/files/working/ctp150804.pdf
    # Version 2.0 (2004-08-15)

    code = ""

    # i = 0
    term_length = len(term)

    if (term_length == 0):
        # empty string ?
        return code
    # end if

    # convert to lowercase
    code = term.lower()

    # remove anything not in the standard alphabet (a-z)
    code = re.sub(r'[^a-z]', '', code)

    # remove final e
    if code.endswith("e"):
        code = code[:-1]

    # if the name starts with cough, rough, tough, enough or trough -> cou2f (rou2f, tou2f, enou2f, trough)
    code = re.sub(r'^([crt]|(en)|(tr))ough', r'\1ou2f', code)

    # if the name starts with gn -> 2n
    code = re.sub(r'^gn', r'2n', code)

    # if the name ends with mb -> m2
    code = re.sub(r'mb$', r'm2', code)

    # replace cq -> 2q
    code = re.sub(r'cq', r'2q', code)

    # replace c[i,e,y] -> s[i,e,y]
    code = re.sub(r'c([iey])', r's\1', code)

    # replace tch -> 2ch
    code = re.sub(r'tch', r'2ch', code)

    # replace c,q,x -> k
    code = re.sub(r'[cqx]', r'k', code)

    # replace v -> f
    code = re.sub(r'v', r'f', code)

    # replace dg -> 2g
    code = re.sub(r'dg', r'2g', code)

    # replace ti[o,a] -> si[o,a]
    code = re.sub(r'ti([oa])', r'si\1', code)

    # replace d -> t
    code = re.sub(r'd', r't', code)

    # replace ph -> fh
    code = re.sub(r'ph', r'fh', code)

    # replace b -> p
    code = re.sub(r'b', r'p', code)

    # replace sh -> s2
    code = re.sub(r'sh', r's2', code)

    # replace z -> s
    code = re.sub(r'z', r's', code)

    # replace initial vowel [aeiou] -> A
    code = re.sub(r'^[aeiou]', r'A', code)

    # replace all other vowels [aeiou] -> 3
    code = re.sub(r'[aeiou]', r'3', code)

    # replace j -> y
    code = re.sub(r'j', r'y', code)

    # replace an initial y3 -> Y3
    code = re.sub(r'^y3', r'Y3', code)

    # replace an initial y -> A
    code = re.sub(r'^y', r'A', code)

    # replace y -> 3
    code = re.sub(r'y', r'3', code)

    # replace 3gh3 -> 3kh3
    code = re.sub(r'3gh3', r'3kh3', code)

    # replace gh -> 22
    code = re.sub(r'gh', r'22', code)

    # replace g -> k
    code = re.sub(r'g', r'k', code)

    # replace groups of s,t,p,k,f,m,n by its single, upper-case equivalent
    for single_letter in ["s", "t", "p", "k", "f", "m", "n"]:
        otherParts = re.split(single_letter + "+", code)
        letter = single_letter.upper()
        code = letter.join(otherParts)

    # replace w[3,h3] by W[3,h3]
    code = re.sub(r'w(h?3)', r'W\1', code)

    # replace final w with 3
    code = re.sub(r'w$', r'3', code)

    # replace w -> 2
    code = re.sub(r'w', r'2', code)

    # replace h at the beginning with an A
    code = re.sub(r'^h', r'A', code)

    # replace all other occurrences of h with a 2
    code = re.sub(r'h', r'2', code)

    # replace r3 with R3
    code = re.sub(r'r3', r'R3', code)

    # replace final r -> 3
    code = re.sub(r'r$', r'3', code)

    # replace r with 2
    code = re.sub(r'r', r'2', code)

    # replace l3 with L3
    code = re.sub(r'l3', r'L3', code)

    # replace final l -> 3
    code = re.sub(r'l$', r'3', code)

    # replace l with 2
    code = re.sub(r'l', r'2', code)

    # remove all 2's
    code = re.sub(r'2', r'', code)

    # replace the final 3 -> A
    code = re.sub(r'3$', r'A', code)

    # remove all 3's
    code = re.sub(r'3', r'', code)

    # extend the code by 10 '1' (one)
    code += '1' * 10

    # take the first 10 characters
    caverphoneCode = code[:10]

    # return caverphone code
    return caverphoneCode
