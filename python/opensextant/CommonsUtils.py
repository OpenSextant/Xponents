"""
 
                Copyright 2012-2014 The MITRE Corporation.
 
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
@module  CommonsUtils
"""
from opensextant import PY3
if PY3:
    from io import StringIO
else:
    from cStringIO import StringIO

import os
import csv
import re
from chardet import detect as detect_charset

version = 'v3'

# ---------------------------------------
#  TEXT UTILITIES
# ---------------------------------------
#


def is_text(t):
    if PY3:
        return isinstance(t, str)
    else:
        return isinstance(t, str) or isinstance(t, unicode)


def is_ascii(s):
    try:
        return all(ord(c) < 128 for c in s)
    except:
        pass

    return False


def get_text(t):
    """ Default is to return Unicode string from raw data"""
    if PY3:
        return str(t, encoding='utf-8')
    else:
        return unicode(t, 'utf-8')


## ISO-8859-2 is a common answer, when they really mean ISO-1
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
    if not encoding:
        enc = guess_encoding(buf)
        encoding = enc['encoding']
        if not encoding:
            return None

    if PY3:
        return str(buf, encoding=encoding)
    else:
        if encoding.lower() == 'utf-8':
            return unicode(buf)
        else:
            text = buf.decode(encoding)
            return unicode(text)


reSqueezeWhiteSpace = re.compile(r'\s+', re.MULTILINE)


def squeeze_whitespace(s):
    return reSqueezeWhiteSpace.sub(' ', s).strip()


def scrub_eol(t):
    return t.replace('\n', ' ').replace('\r', '')


BOOL_F_STR = {"false", "0", "n", "f", "no", "", "null"}
BOOL_T_STR = {"true", "1", "y", "t", "yes"}


def get_bool(token):
    if not token:
        return False

    if isinstance(token, bool):
        return token

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


## ---------------------------------------
##  FILE UTILITIES
## ---------------------------------------
##
def _utf_8_encoder(unicode_csv_data):
    for line in unicode_csv_data:
        yield line.encode('utf-8')


def get_csv_writer(fh, columns, delim=','):
    return csv.DictWriter(fh, columns, restval="", extrasaction='raise',
                          dialect='excel', lineterminator='\n',
                          delimiter=delim, quotechar='"',
                          quoting=csv.QUOTE_ALL, escapechar='\\')


def get_csv_reader(fh, columns, delim=','):
    from opensextant import PY3
    if PY3:
        return csv.DictReader(fh, columns,
                              restval="", dialect='excel', lineterminator='\n', escapechar='\\',
                              delimiter=delim, quotechar='"', quoting=csv.QUOTE_ALL)
    else:
        return csv.DictReader(_utf_8_encoder(fh), columns,
                          restval="", dialect='excel', lineterminator='\n', escapechar='\\',
                          delimiter=delim, quotechar='"', quoting=csv.QUOTE_ALL)


# |||||||||||||||||||||||||||||||||||||||||||||
# |||||||||||||||||||||||||||||||||||||||||||||
class ConfigUtility:
    # |||||||||||||||||||||||||||||||||||||||||||||
    # |||||||||||||||||||||||||||||||||||||||||||||
    """ A utility to load parameter lists, CSV files, word lists, etc. from a folder *dir*

    functions here take an Oxygen cfg parameter keyword or a file path.
    If the keyword is valid and points to a valid file path, then the file path is used.
    In otherwords, keywords are aliases for a file on disk.

      Ex.  'mywords' = '.\cfg\mywords_v03_filtered.txt'

      oxygen.cfg file would have this mapping.  Your code just references 'mywords' to load it.
    """

    def __init__(self, cfg, rootdir='.'):

        # If config is None, then caller can still use loadDataFromFile(abspath, delim) for example.
        #
        self.config = cfg
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
          Load columnar data from a file.
          Returns array of non-comment rows.
        """
        if not os.path.exists(path):
            raise Exception('File does not exist, FILE=%s' % path)

        f = open(path, 'rb')
        filereader = csv.reader(f, delimiter=delim, lineterminator='\n')
        data = []
        for row in filereader:
            first_cell = row[0].strip()
            if first_cell.startswith('#'):
                continue

            # if not delim and not first_cell:
            #    continue

            data.append(row)
        f.close()
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

        with open(path, 'r') as fh:
            termlist = []
            for line in fh:
                line = line.strip()
                if line.startswith('#'):
                    continue
                if len(line) == 0:
                    continue

                termlist.append(line.lower())

            return termlist
