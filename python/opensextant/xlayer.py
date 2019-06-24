"""
Created on Mar 14, 2016

@author: ubaldino
"""

import requests
import simplejson as json


class XlayerClient:
    def __init__(self, server, options=""):
        """
        @param server: URL for the service
        @keyword  options:  a comma-separated list of options to send with each request.  There are no default options supported. 
        """
        self.server = server
        self.debug = False
        self.default_options = options

    def stop(self):
        response = requests.get("%s?cmd=stop" % (self.server))
        if response.status_code != 200:
            return response.raise_for_status()

    def ping(self):
        response = requests.get("%s?cmd=ping" % (self.server))
        if response.status_code != 200:
            return response.raise_for_status()

    def process(self, docid, text, features=["geo"]):
        """
          SERVICE parameters:
          docid and text -- obvious

          features = "f,f,f,f"  String of comma-separated features
          options  = "o,o,o,o"  String of comma-separated features

          features are places, coordinates, countries, orgs, persons, patterns. 
          
          feature aliases "geo" can be used to get All Geographic entities (places,coordinates,countries)
          feature "taxons" can get at any Taxon "taxons", "persons", "orgs"
          
          options are not observed by Xlayer "Xgeo", but you can adapt your own service
          to accomodate such options.   Possible options are clean_input, lowercase, for example:

          * clean_input scrubs the input text if it has HTML or other content in it.
          * lowercase allows the tagging to pass on through lower case matches.
          
          but interpretation of "clean text" and "lower case" support is subjective.
          so they are not supported out of the box here.
        """
        json_request = {'docid': docid, 'text': text, 'options': self.default_options}
        if features:
            json_request['features'] = ','.join(features)

        response = requests.post(self.server, json=json_request)
        if response.status_code != 200:
            return response.raise_for_status()

        json_content = response.json()

        if self.debug:
            print(json.dumps(json_content, indent=2))
        if 'response' in json_content:
            metadata = json_content['response']
        annots = []
        if 'annotations' in json_content:
            annots = json_content['annotations']
            if self.debug:
                for a in annots:
                    print("Match", a['matchtext'], "at char offset", a['offset'])
                    if 'lat' in a:
                        print("representing geo location (%2.4f, %3.4f)" % (a.get('lat'), a.get('lon')))

        return annots


if __name__ == '__main__':
    import os
    from traceback import format_exc
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("--service-url", help="XLayer server URL to /process endpoint")
    ap.add_argument("--docid", help="your doc id")
    ap.add_argument("--inputfile", help="your input")
    ap.add_argument("--lines", action="store_true", help="process your inputfile as one line per call")
    ap.add_argument("--text", help="UTF-8 string to process")
    ap.add_argument("--options", help="your service options to send with each request")
    ap.add_argument("--debug", default=False, action="store_true")
    args = ap.parse_args()
    xtractor = XlayerClient(args.service_url)
    xtractor.debug = args.debug

    # ======================================
    # Support for arbitrary amounts of text
    #
    if args.text:
        _id = "test doc#1"
        _text = args.text
        result = xtractor.process(_id, _text)
        print("==============")
        print("INPUT: from text argument")
        print("PYTHON str(result)\t")
        print(result)
    # ======================================
    # Support data as one text record per line in a file
    #                
    elif args.lines and args.inputfile:
        print("INPUT: from individual lines from inputfile")
        try:
            fh = open(args.inputfile, 'rb')
            lineNum = 0
            for line in fh:
                lineNum += 1
                _id = "line{}".format(lineNum)
                print("=============={}:".format(_id))
                _text = unicode(line.strip(), 'utf-8')
                result = xtractor.process(_id, _text)
                print("PYTHON str(result)\t ")
                print(result)
            fh.close()
        except Exception, err:
            print(format_exc(limit=5))

    # ======================================
    # Use a single file as the source text to process
    #                
    elif args.inputfile:
        _id = os.path.basename(args.inputfile)
        if args.docid:
            _id = args.docid
        try:
            with open(args.inputfile, 'rb') as fh:
                _text = fh.read()
                _text = unicode(_text, 'utf-8')
                result = xtractor.process(_id, _text)
                print("==============")
                print("INPUT: from text inputfile")
                print("PYTHON str(result)")
                print(result)
        except Exception, err:
            print(format_exc(limit=5))

    # Testing:
    # xtractor.ping()
    # xtractor.stop()
