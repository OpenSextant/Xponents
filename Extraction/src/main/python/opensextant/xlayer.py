'''
Created on Mar 14, 2016

@author: ubaldino
'''

import requests
import simplejson as json

class XlayerClient:
    def __init__(self, server):
        '''
        @param server: URL for the service 
        '''
        self.server = server
        self.debug = False
        
    def stop(self):
        response = requests.get("%s?cmd=stop" %(self.server))
        if response.status_code != 200:        
            return response.raise_for_status()
        
    def ping(self):
        response = requests.get("%s?cmd=ping" %(self.server))
        if response.status_code != 200:        
            return response.raise_for_status()        
        
    def process(self, docid, text):
        response = requests.post(self.server, json={'docid':docid, 'text':text})
        if response.status_code != 200:        
            return response.raise_for_status()
        
        json_content = response.json()
        
        if self.debug:
            print json.dumps(json_content, indent=2)
        if 'response' in json_content:
            metadata = json_content['response']
        if 'annotations' in json_content:
            annots = json_content['annotations']
            if self.debug:
                for a in annots:
                    print "Match", a['matchtext'], "at char offset", a['offset'];
                    if 'lat' in a: print "representing geo location (%2.4f, %3.4f)" % (a.get('lat'), a.get('lon'))
            
        return annots
    
if __name__ == '__main__':
    import sys
    from traceback import format_exc
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-url")
    ap.add_argument("--docid")
    ap.add_argument("--inputfile")
    ap.add_argument("--text")
    args = ap.parse_args()
    xtractor = XlayerClient(args.service_url)
    xtractor.debug = True
    _id = "test doc#1"
    if args.docid:
        _id = args.docid
    _text = None
    if args.inputfile:
        try:
            fh = open(args.inputfile, 'rb')
            _text = fh.read()
            _text = unicode(_text, 'utf-8')
        except Exception, err:
            print(format_exc(limit=5))
    elif args.text:
        _text  = args.text
        
    if not _text:
        print ("No inputfile or text args providea")
        sys.exit(-1)
        
    result = xtractor.process(_id, _text)
    print("PYTHON str(result)\n==============")
    print(result)
    # Testing:
    #xtractor.ping()
    #xtractor.stop()
