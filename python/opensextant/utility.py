# -*- coding: utf-8 -*-
"""
 
                Copyright 2015-2021 The MITRE Corporation.
 
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy of
  the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.
 
  =============================================================================

@author: ubaldino

OpenSextant utilities
"""
import csv
import os
import re
from io import StringIO
from math import isnan

from chardet import detect as detect_charset
from .unicode import LATIN1_FOLDING


# ---------------------------------------
#  TEXT UTILITIES
# ---------------------------------------
#
def is_text(t):
    return isinstance(t, str)


code_pattern = re.compile("^[A-Z0-9]{1,}$", re.ASCII)


def is_code(t: str, nlen=6):
    """
    Test if a string is an ASCII code typically 1-3 chars in len.
    :param t: text
    :param nlen: threshold for string len
    :return:
    """
    if not t:
        return False
    if len(t) > nlen or not t.isupper():
        return False
    return code_pattern.match(t) is not None


def is_abbreviation(nm: str):
    """
    Determine if something is an abbreviation.
    Otherwise if text ends with "." we'll conclude so.

    Examples:
        Ala.     YES
        Ala      NO
        S. Bob   NO   -- abbreviated, yes, but this is more like a contraction.
        S. B.    YES

    :param nm: textual name
    :return: True if obj is inferred to be an abbreviation
    """
    return nm.endswith(".")


def is_ascii(s):
    try:
        return all(ord(c) < 128 for c in s)
    except:
        pass
    return False


def get_text(t):
    """ Default is to return Unicode string from raw data"""
    if isinstance(t, str):
        return t
    return str(t, encoding='utf-8')


def fast_replace(t, sep, sub=None):
    """
    Replace separators (sep) with substitute char, sub. Many-to-one substitute.

    "a.b, c" SEP='.,'
    :param t:  input text
    :param sep: string of chars to replace
    :param sub: replacement char
    :return:  text with separators replaced
    """
    result = []
    for ch in t:
        if ch in sep:
            if sub:
                result.append(sub)
        else:
            result.append(ch)
    return ''.join(result)


# ISO-8859-2 is a common answer, when they really mean ISO-1
CHARDET_LATIN2_ENCODING = 'ISO-8859-1'


def guess_encoding(text):
    """ Given bytes, determine the character set encoding
    @return: dict with encoding and confidence
    """
    if not text: return {'confidence': 0, 'encoding': None}

    enc = detect_charset(text)

    cset = enc['encoding']
    if cset.lower() == 'iso-8859-2':
        # Anomoaly -- chardet things Hungarian (iso-8850-2) is
        # a close match for a latin-1 document.  At least the quotes match
        # Other Latin-xxx variants will likely match, but actually be Latin1
        # or win-1252.   see Chardet explanation for poor reliability of Latin-1 detection
        #
        enc['encoding'] = CHARDET_LATIN2_ENCODING

    return enc


def bytes2unicode(buf, encoding=None):
    """
    Convert bytes 2 unicode by guessing character set.
    :param buf:
    :param encoding:
    :return:
    """
    if not encoding:
        enc = guess_encoding(buf)
        encoding = enc['encoding']
        if not encoding:
            return None
    return str(buf, encoding=encoding)


reSqueezeWhiteSpace = re.compile(r'\s+', re.MULTILINE)


def squeeze_whitespace(s):
    return reSqueezeWhiteSpace.sub(' ', s).strip()


def scrub_eol(t):
    return t.replace('\n', ' ').replace('\r', '')


def levenshtein_distance(s, t):
    """
    Wikipedia page on Levenshtein Edit Distance
    https://en.wikipedia.org/wiki/Levenshtein_distance

    This is the fastest, simplest of 3 methods documented for Python.
    """
    s = ' ' + s
    t = ' ' + t
    d = {}
    S = len(s)
    T = len(t)
    if S == T and s == t:
        return 0
    for i in range(S):
        d[i, 0] = i
    for j in range(T):
        d[0, j] = j
    for j in range(1, T):
        for i in range(1, S):
            if s[i] == t[j]:
                d[i, j] = d[i - 1, j - 1]
            else:
                d[i, j] = min(d[i - 1, j] + 1, d[i, j - 1] + 1, d[i - 1, j - 1] + 1)
    return d[(S - 1, T - 1)]


BOOL_F_STR = {"false", 0, "0", "n", "f", "no", "", "null"}
BOOL_T_STR = {"true", 1, "1", "y", "t", "yes"}


def get_bool(token):
    if not token:
        return False

    if isinstance(token, bool):
        return token

    if isinstance(token, int):
        if token > 0:
            return True
        if token == 0:
            return False

    t = token.lower()
    if t in BOOL_F_STR:
        return False

    if t in BOOL_T_STR:
        return True

    return False


def get_number(token):
    """ Turn leading part of a string into a number, if possible.
    """
    num = StringIO()
    for ch in token:
        if ch.isdigit() or ch == '.' or ch == '-':
            num.write(ch)
        else:
            break
    val = num.getvalue()
    num.close()
    return val


def has_digit(text):
    """
    Used primarily to report places and appears to be critical for
    name filtering when doing phonetics.
    """
    if text is None:
        return False

    for ch in text:
        # ascii
        if ch.isdigit():
            return True
    return False


def measure_case(t):
    """

    :param t: text
    :return:  tuple:  counts of UPPER, lower, Alpha, Non-Alpha, WS
    """
    if not t:
        return 0, 0, 0, 0, 0

    u, l, ch, nonch, ws = 0, 0, 0, 0, 0
    for c in t:
        if c.isalpha():
            ch += 1
            if c.isupper():
                u += 1
            elif c.islower():
                l += 1
        elif c.isspace():
            ws += 1
        else:
            nonch += 1

    # you should verify
    #   TOTAL chars = ch + nonch + ws
    #   Alpha chars, ch = u + l
    return u, l, ch, nonch, ws


def is_upper_text(t, threadshold=0.90):
    u, l, ch, nonch, ws = measure_case(t)
    if ch == 0:
        return False
    return u / ch > threadshold


def is_value(v):
    """
    Working more with pandas or sci libraries -- you run into various types of default "Null" values.
    This checks to see if value is non-trivial, non-empty.
    :param v:
    :return:
    """
    if v is None:
        return False
    if isinstance(v, (float, int)):
        return not isnan(v)
    return True


def parse_float(v):
    if not v:
        return None
    try:
        return float(v)
    except Exception as float_err:
        print("Unable to parse float", v, str(float_err))
        return None


def get_list(text, delim=',', lower=False):
    """
    Take a string and return trim segments given the delimiter:

         "A,  B,\tC" => ["A", "B", "C"]
    :param text:
    :param delim: delimiter str
    :param lower: True if you want items lowercased
    :return: array
    """
    if not text:
        return []

    data = text.split(delim)
    arr = []
    for v in data:
        _v = v.strip()
        if _v:
            if lower:
                _v = _v.lower()
            arr.append(_v)
    return arr


def get_text_window(offset, matchlen, textsize, width):
    """ prepreprepre MATCH postpostpost
       ^            ^   ^            ^
       l-width      l   l+len        l+len+width
       left_y  left_x   right_x      right_y
    """
    left_x = offset - width
    left_y = offset - 1
    right_x = offset + matchlen
    right_y = right_x + width
    if left_x < 0:
        left_x = 0

    if left_y < left_x:
        left_y = left_x

    # bounds checking  END....y?  then y=END, results in shorter postmatch
    if right_y >= textsize:
        right_y = textsize - 1
    # bounds checking   y.... x?  then x=y,  results in empty postmatch
    if right_x > right_y:
        right_x = right_y

    return [left_x, left_y, right_x, right_y]


def has_cjk(text):
    """
    infer if chinese (unihan), korean (hangul) or japanese (hirgana) characters are present
    :param text: 
    :return: 
    """
    #             CJK, Hirgana, Katana.  Unified Ideagoraphs. Hangjul.
    search = re.search("[\u3000-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]", text, flags=re.IGNORECASE | re.UNICODE)
    return search is not None


def has_arabic(text):
    """
    infer if text has Arabic / Middle-eastern scripts ~ Urdu, Farsi, Arabic.
    :param text:
    :return:
    """
    search = re.search("[\u0600-\u08ff]", text, flags=re.IGNORECASE | re.UNICODE)
    return search is not None


def trivial_bias(name):
    """ Experimental: Deteremine unique a name is using length and character set and # of words

    Abcd           4/2 + 1 + 0   x 0.02  = 0.06
    Abcde fghi    10/2 + 2 + 0   x 0.02  = 0.14
    Abcdé fghi    10/2 + 2 + 1   x 0.02  = 0.16
    """
    l_points = len(name) / 2
    word_points = len(name.split())
    charset_points = 1 if not is_ascii(name) else 0
    score = (l_points + word_points + charset_points) * 0.02
    return float("{:0.3}".format(score))


COMMON_DIACRITC_HASHMARKS = re.compile("[\"'`\u00B4\u2018\u2019]")


def replace_diacritics(txt: str):
    """
    Leverage the OpenSextant traditional ASCII Folding map for now.
    Yes encoded("ascii", "ignore") may do this....
    :param txt:
    :return: a non-diacritic version of the text
    """
    str_prepped = COMMON_DIACRITC_HASHMARKS.sub("'", txt)

    buf = []
    for ch in str_prepped:
        buf.append(LATIN1_FOLDING.get(ch, ch))
    return "".join(buf)


def strip_quotes(t):
    """
    Run replace_diacritics first -- this routine only attempts to remove normal quotes ~ ', "
    """
    return t.strip('"').strip("'")


# /---------------------------------------
#  FILE UTILITIES
# /---------------------------------------
#
def _utf_8_encoder(unicode_csv_data):
    for line in unicode_csv_data:
        yield line.encode('utf-8')


def get_csv_writer(fh, columns, delim=','):
    return csv.DictWriter(fh, columns, restval="", extrasaction='raise',
                          dialect='excel', lineterminator='\n',
                          delimiter=delim, quotechar='"',
                          quoting=csv.QUOTE_ALL, escapechar='\\')


def get_csv_reader(fh, columns, delim=','):
    return csv.DictReader(fh, columns, restval="", dialect='excel', lineterminator='\n', escapechar='\\',
                          delimiter=delim, quotechar='"', quoting=csv.QUOTE_ALL)


# |||||||||||||||||||||||||||||||||||||||||||||
# |||||||||||||||||||||||||||||||||||||||||||||
class ConfigUtility:
    """ A utility to load parameter lists, CSV files, word lists, etc. from a folder *dir*

    functions here take an Oxygen cfg parameter keyword or a file path.
    If the keyword is valid and points to a valid file path, then the file path is used.
    In otherwords, keywords are aliases for a file on disk.

      Ex.  'mywords' = './cfg/mywords_v03_filtered.txt'

      oxygen.cfg file would have this mapping.  Your code just references 'mywords' to load it.
    """

    def __init__(self, config=None, rootdir='.'):

        # If config is None, then caller can still use loadDataFromFile(abspath, delim) for example.
        #
        self.config = config
        self.rootdir = rootdir

    def loadCSVFile(self, keyword, delim):
        """
          Load a named CSV file.  If the name is not a cfg parameter, the keyword name *is* the file.
        """
        f = self.config.get(keyword)
        if f is None:
            f = keyword

        path = os.path.join(self.rootdir, f)
        return self.loadDataFromFile(path, delim)

    def loadDataFromFile(self, path, delim):
        """

        :param path: file path
        :param delim: delimiter
        :return: Array of tuples.
        """
        if not os.path.exists(path):
            raise Exception('File does not exist, FILE=%s' % path)

        with open(path, 'r', encoding="UTF-8") as f:
            filereader = csv.reader(f, delimiter=delim, lineterminator='\n', dialect="excel")
            data = []
            for row in filereader:
                if not row:
                    print("Blank line")
                    continue
                first_cell = row[0].strip()
                if first_cell.startswith('#'):
                    continue
                data.append(row)
        return data

    def loadFile(self, keyword):
        """
        Load a named word list file.
        If the name is not a cfg parameter, the keyword name *is* the file.
        """
        filename = ''

        if os.path.exists(keyword):
            path = keyword
        else:
            filename = self.config.get(keyword)
            if filename is None:
                filename = keyword

            path = os.path.join(self.rootdir, filename)
            if not os.path.exists(path):
                raise Exception('File does not exist, FILE=%s' % path)

        return self.loadListFromFile(path)

    def loadListFromFile(self, path):
        """
          Load text data from a file.
          Returns array of non-comment rows. One non-whitespace row per line.
        """
        if not os.path.exists(path):
            raise Exception('File does not exist, FILE=%s' % path)

        with open(path, 'r', encoding="UTF-8") as fh:
            termlist = []
            for line in fh:
                line = line.strip()
                if line.startswith('#'):
                    continue
                if len(line) == 0:
                    continue

                termlist.append(line.lower())

            return termlist


def load_list(path, lower=False):
    """
      Load text data from a file.
      Returns array of non-comment rows. One non-whitespace row per line.
      :param path: file to load.
      :param lower: Lowercased is optional.
      :return: array of terms
    """
    if not os.path.exists(path):
        raise Exception('File does not exist, FILE=%s' % path)

    with open(path, 'r', encoding="UTF-8") as fh:
        termlist = []
        for line in fh:
            line = line.strip()
            if line.startswith('#') or not line:
                continue

            termlist.append(line.lower() if lower else line)

        return termlist


def load_datafile(path, delim):
    """
    :param path: file path
    :param delim: delimiter
    :return: Array of tuples.
    """
    if not os.path.exists(path):
        raise Exception(f'File does not exist, FILE={path}')

    with open(path, 'r', encoding="utf-8") as f:
        data = []
        text = f.read().replace('\uFEFF', '')

        for line in text.split("\n"):
            row = line.strip().split(delim)
            if not row:
                # print("Blank line")
                continue
            first_cell = row[0]
            if first_cell.startswith('#'):
                continue
            data.append(row)
        return data


def ensure_dirs(fpath):
    """
    Given a file path, ensure parent folders exist.
    If path is intended to be a directory -- use os.makedirs(path) instead.
    May throw exception -- caller should handle.

    :path: path a file
    """
    d = os.path.dirname(fpath)
    if d and not os.path.isdir(d):
        os.makedirs(d)
        return True
    return False
