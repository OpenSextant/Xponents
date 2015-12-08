package org.opensextant.util;

import java.io.File;
import java.io.FilenameFilter;
/**
 *
 * @author ubaldino
 */
public class AnyFilenameFilter implements FilenameFilter {

    private String extension = null;

    /**
     *
     * @param ext file extension
     */
    public AnyFilenameFilter(String ext){ extension = ext; }

    /**
     * FilenameFilter implementation for XML files
     *
     * @param dir dir to filter on
     * @param fname file name to test
     * @return if file is accepted by this filter
     */
    @Override
    public boolean accept(File dir, String fname) {
        if (dir == null || fname == null) {
            return false;
        }
        return fname.endsWith(extension);
    }
}
