/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xtext.converters;

import java.io.IOException;
import java.io.InputStream;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.mitre.xtext.ConvertedDocument;

/**
 * @author T. Allison, MITRE 
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class MSDocConverter extends ConverterAdapter {
    
    /** TODO: Replace with a Tika converter?
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream input, java.io.File doc) throws IOException {
        org.apache.poi.hwpf.extractor.WordExtractor ex = new WordExtractor(input);
        
        String[] ps = ex.getParagraphText();
        input.close();

        StringBuilder sb = new StringBuilder();        
        for (int i = 0; i < ps.length; i++) {
            sb.append(WordExtractor.stripFields(ps[i]).trim());
            sb.append('\n');
        }
        ConvertedDocument textdoc = new ConvertedDocument(doc);
        textdoc.setPayload(sb.toString());
        
        return textdoc;
    }
}
