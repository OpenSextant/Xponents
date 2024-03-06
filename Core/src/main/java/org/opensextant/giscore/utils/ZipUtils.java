/****************************************************************************************
 *  ZipUtils.java
 *
 *  Created: Jul 27, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

/**
 * Common manipulations
 * 
 * @author DRAND
 */
public final class ZipUtils {
	
    /**
     * Outputting data to zip
     *
     * @param prefix String file path prefix
     * @param outputPath File or Directory to be recursively processed
     * @param outputStream ZipOutputStream to write to
     * @throws IllegalStateException if there is an I/O error
     */
    public static void outputZipComponents(String prefix, File outputPath,
                                     ZipOutputStream outputStream) {
        InputStream is = null;
        if (outputPath == null) return;
        File[] files = outputPath.listFiles();
        if (files == null) return;
        for (File component : files) {
            if (component.isDirectory()) {
                outputZipComponents(prefix + "/" + component.getName(),
                        component, outputStream);
            } else {
                try {
                    ZipEntry entry = new ZipEntry(prefix + "/"
                            + component.getName());
                    outputStream.putNextEntry(entry);
                    is = new FileInputStream(component);
                    IOUtils.copy(is, outputStream);
                } catch (FileNotFoundException e) {
                    // Ignore since lock files may linger and cause this
                } catch (IOException e) {
                    throw new IllegalStateException("Problem writing zip output", e);
                } finally {
                    IOUtils.closeQuietly(is);
                    is = null;
                }
            }
        }
    }
}
