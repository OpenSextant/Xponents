package org.opensextant.util;

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
