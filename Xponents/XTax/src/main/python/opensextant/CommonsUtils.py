'''
Created on Mar 13, 2012

Core utilities useful for OpenSextant support here.

@author: ubaldino

@module OpenSextantCommonsUtils; 
'''

version = 'v3'

from cStringIO import StringIO
import os
import csv
import re
from chardet import detect as detect_charset

## ---------------------------------------  
##  TEXT UTILITIES
## ---------------------------------------  
##


## ISO-8859-2 is a common answer, when they really mean ISO-1
CHARDET_LATIN2_ENCODING = 'ISO-8859-1'

def guess_encoding(text):
    ''' Given bytes, determine the character set encoding
    @return: dict with encoding and confidence
    '''
    if not text: return {'confidence':0, 'encoding':None}
    
    enc = detect_charset(text)
    
    cset = enc['encoding']
    if cset.lower() == 'iso-8859-2':
        ## Anamoly -- chardet things Hungarian (iso-8850-2) is
        ## a close match for a latin-1 document.  At least the quotes match
        ##  Other Latin-xxx variants will likely match, but actually be Latin1
        ##  or win-1252.   see Chardet explanation for poor reliability of Latin-1 detection
        ##
        enc['encoding'] = CHARDET_LATIN2_ENCODING

    return enc

def bytes2unicode(buf, encoding=None):
    if not encoding:
        enc = guess_encoding(buf)
        encoding = enc['encoding']
        if not encoding:
            return None

    if encoding.lower() == 'utf-8':
        return unicode(buf)
    else:     
        text = buf.decode(encoding)
        return unicode(text)
    
    return None
    
reSqueezeWhiteSpace = re.compile(r'\s+', re.MULTILINE)
def squeeze_whitespace(s):
    return reSqueezeWhiteSpace.sub(' ', s).strip()

def scrub_eol(t):
    return t.replace('\n', ' ').replace('\r', '')

BOOL_F_STR = set(["false", "0", "n", "f", "no", "", "null"])
BOOL_T_STR = set(["true", "1", "y", "t", "yes" ])
def get_bool(token):
    
    if not token:
        return False
    
    if isinstance(token, bool):
        return token
    
    t=token.lower()
    if t in BOOL_F_STR:
        return False
    
    if t in BOOL_T_STR:
        return True
    
    return False
    
    
def get_number(token):
    ''' Turn leading part of a string into a number, if possible.
    '''
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
    '''
    Used primarily to report places and appears to be critical for 
    name filtering when doing phonetics. 
    '''
    if text is None:
        return False
    
    for ch in text:
        # ascii
        if ch.isdigit():
            return True
    return False
 
def get_text_window(offset, matchlen, textsize, width):
    ''' prepreprepre MATCH postpostpost
       ^            ^   ^            ^
       l-width      l   l+len        l+len+width
       left_y  left_x   right_x      right_y
    '''
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
            
    return [ left_x, left_y, right_x, right_y]




## ---------------------------------------  
##  FILE UTILITIES
## ---------------------------------------  
##

def get_csv_writer(fh, columns, delim=','):
    return csv.DictWriter(fh, columns, restval="", extrasaction='raise',
                            dialect='excel', lineterminator='\n',
                            delimiter=delim, quotechar='"',
                            quoting=csv.QUOTE_ALL, escapechar='\\')                

def get_csv_reader(fh, columns, delim=','):
    return csv.DictReader(fh, columns,
                          restval="",  dialect='excel', lineterminator='\n', escapechar='\\',
                          delimiter=delim, quotechar='"', quoting=csv.QUOTE_ALL)
 
 
# |||||||||||||||||||||||||||||||||||||||||||||
# |||||||||||||||||||||||||||||||||||||||||||||
class ConfigUtility:
# |||||||||||||||||||||||||||||||||||||||||||||
# |||||||||||||||||||||||||||||||||||||||||||||
    ''' A utility to load parameter lists, CSV files, word lists, etc. from a folder *dir*
    
    functions here take an Oxygen cfg parameter keyword or a file path. 
    If the keyword is valid and points to a valid file path, then the file path is used.
    In otherwords, keywords are aliases for a file on disk.  
    
      Ex.  'mywords' = '.\cfg\mywords_v03_filtered.txt'
      
      oxygen.cfg file would have this mapping.  Your code just references 'mywords' to load it.
    '''
    def __init__(self, CFG, rootdir='.'):
        
        # If config is None, then caller can still use loadDataFromFile(abspath, delim) for example.
        #
        self.config = CFG
        self.rootdir = rootdir
        
    def loadCSVFile(self, keyword, delim):
        '''
          Load a named CSV file.  If the name is not a cfg parameter, the keyword name *is* the file.        
        '''
        f = self.config.get(keyword)
        if f is None:
            f = keyword
        
        path = os.path.join(self.rootdir, f)
        return self.loadDataFromFile(path, delim)
        
    def loadDataFromFile(self, path, delim):
        '''
          Load columnar data from a file. 
          Returns array of non-comment rows.        
        '''
        if not os.path.exists(path):
            raise Exception('File does not exist, FILE=%s' % path)
        
        f = open(path, 'rb')
        filereader = csv.reader(f, delimiter=delim, lineterminator='\n')
        data = []
        for row in filereader:
            first_cell = row[0].strip()
            if first_cell.startswith('#'):
                continue
            
            #if not delim and not first_cell:
            #    continue
            
            data.append(row)
        f.close()
        return data
    
    
    def loadFile(self, keyword):
        '''
        Load a named word list file.
        If the name is not a cfg parameter, the keyword name *is* the file. 
        '''
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
        '''
          Load text data from a file. 
          Returns array of non-comment rows. One non-whitespace row per line.        
        '''
        if not os.path.exists(path):
            raise Exception('File does not exist, FILE=%s' % path)
        
        file = open(path, 'r')
        termlist = []
        for line in file:
            line = line.strip()
            if line.startswith('#'):
                continue
            if len(line) == 0:
                continue
            
            termlist.append(line.lower())

        file.close()
        return termlist    


