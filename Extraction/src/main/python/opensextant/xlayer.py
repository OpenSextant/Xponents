'''
Created on Mar 14, 2016

@author: ubaldino
'''

import requests
import simplejson as json

class XlayerClient:
    def __init__(self, server):
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
    # xtractor = XlayerClient('http://localhost:6789/xlayer/rest/process')
    xtractor = XlayerClient(sys.argv[1])
    xtractor.debug = True
    result = xtractor.process('test doc#1', 'Where is 56:08:45N, 117:33:12W?  Is it near Lisbon or closer to Saskatchewan?'
                     + 'Seriously, what part of Canada would you visit to see the new prime minister discus our border?'
                     + 'Do you think Hillary Clinton or former President Clinton have an opinion on our Northern Border?')


    print("PYTHON str(result)\n==============")
    print(result)
    # Testing:
    #xtractor.ping()
    #xtractor.stop()
