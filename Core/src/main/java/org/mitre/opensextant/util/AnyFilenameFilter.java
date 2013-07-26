/*
                                NOTICE
 
     This software (or technical data) was produced for the U. S.
  Government under contract W15P7T-08-C-F600, and is subject to the
  Rights in Data-General Clause 52.227-14 - Alternate IV (June 1987)
 
         (c) 2007-2008 The MITRE Corporation. All Rights Reserved.
 */

/*
 * AnyFilenameFilter.java
 *
 * Created on October 11, 2007, 4:18 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.mitre.opensextant.util;

import java.io.*;
/**
 *
 * @author ubaldino
 */
public class AnyFilenameFilter implements FilenameFilter {
    
    private String extension = null;
    
    /**
     *
     * @param ext
     */
    public AnyFilenameFilter(String ext){ extension = ext; }
    /**
     * FilenameFilter implementation for XML files
     *
     * @param dir
     * @param fname
     * @return
     */
    public boolean accept(File dir, String fname) {
        if (dir == null || fname == null) {
            return false;
        }
        return fname.endsWith(extension);
    }
}
