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
package org.opensextant.xtext.converters;

import java.io.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.FilenameUtils;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.utils.IOUtils;

import org.opensextant.util.FileUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opensextant.xtext.iFilter;
import org.opensextant.xtext.iConvert;
import org.opensextant.xtext.ArchiveUnpacker;

/**
 * Archive is traversed, but no data is written to disk unless XText is in save
 * mode. Conversion listener should be listening for Converted Docs.
 * 
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class ArchiveNavigator implements ArchiveUnpacker {

    private final Logger log = LoggerFactory.getLogger(ArchiveNavigator.class);
    // private File tempDir = null;
    private File saveDir = null;
    private iFilter filter = null;
    private iConvert converter = null;
    public boolean overwrite = false;

    /**
     * Given a working temp folder and a file filter unpack archives.
     */
    public ArchiveNavigator(String saveTo, iFilter fileFilter, iConvert fileConv)
            throws IOException {
        this.saveDir = new File(saveTo);
        FileUtility.makeDirectory(saveDir);
        filter = fileFilter;
        converter = fileConv; // Uh... this is really a proxy for XText for now.

        if (filter == null || converter == null) {
            throw new IOException(
                    "Filter and converter cannot be null -- XText is the default for both.");
        }
    }

    public String getWorkingDir() {
        return saveDir.getAbsolutePath();
    }

    /**
     * Unpack any archive. You must provide a converter -- which converts each
     * file.
     */
    public void unpack(File archive) throws IOException {

        // Get file extension
        String ext = FilenameUtils.getExtension(archive.getPath());

        File archivetmp = null;

        if (ext.equalsIgnoreCase("zip")) {
            archivetmp = unzip(archive);
        } else if (ext.equalsIgnoreCase("tar")) {
            archivetmp = untar(archive);
        } else if (ext.equalsIgnoreCase("gz") || ext.equalsIgnoreCase("tgz")
                || ext.equalsIgnoreCase("tar.gz")) {
            String basename = FilenameUtils.getBaseName(archive.getName());
            // We assume the file is a tarball. First unzip it
            File tarFile = gunzip(archive, basename);

            // Then untar it
            archivetmp = untar(tarFile);
        } else {
            throw new IOException("Unsupported archive type: EXT=" + ext);
        }
        log.info("Archive FILE={} has been processed to DIR={}", archive, archivetmp);
    }

    /*
     * Un-TAR. Oops. Its just a copy of Un-TAR and I replace tar with zip.
     * 
     * so there may be Zip-specific stuff here, ... but the approach is the
     * same.
     */
    public File unzip(File zipFile) throws IOException {

        // String _working = FilenameUtils.concat(getWorkingDir(),
        // FilenameUtils.getBaseName(zipFile.getPath()));
        // if (_working == null){
        // throw new IOException("Invalid archive path for "+zipFile.getPath());
        // }

        // File workingDir = new File(_working);
        // workingDir.mkdir();
        File workingDir = saveDir;

        InputStream input = new BufferedInputStream(new FileInputStream(zipFile));
        ZipArchiveInputStream in = null;
        try {
            in = (ZipArchiveInputStream) (new ArchiveStreamFactory().createArchiveInputStream(
                    "zip", input));

            ZipArchiveEntry zipEntry;
            while ((zipEntry = (ZipArchiveEntry) in.getNextEntry()) != null) {
                if (filterEntry(zipEntry)) {
                    continue;
                }

                try {
                    File tmpFile = saveArchiveEntry(zipEntry, in, workingDir);
                    converter.convert(tmpFile);

                } catch (IOException err) {
                    log.error(
                            "Unable to save item, FILE=" + zipEntry.getName() + "!"
                                    + zipEntry.getName(), err);
                }
            }
            return workingDir;

        } catch (ArchiveException ae) {
            throw new IOException(ae);
        } finally {
            in.close();
        }
    }

    /**
     * 
     * @param theFile
     * @param fname
     * @return
     * @throws IOException
     */
    private File gunzip(File theFile, String fname) throws IOException {

        GZIPInputStream gzipInputStream = null;
        OutputStream out = null;

        try {
            gzipInputStream = new GZIPInputStream(new FileInputStream(theFile));
            // TODO:  more testing on this particular case:  gunzip *.gz *.tgz *.tar.gz -- a mix of tar and gunzip
            String outFilename = getWorkingDir() + '/' + fname + ".tar";
            File outFile = new File(outFilename);
            out = new BufferedOutputStream(new FileOutputStream(outFilename));

            byte[] buf = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

            return outFile;
        } finally {
            gzipInputStream.close();
            if (out != null) {
                out.close();
            }
        }
    }

    /*
     * Un-TAR Once items are saved off to temp folder, they'll be converted by
     * the file converter. The converter can choose to do something else with
     * them.
     */
    public File untar(File tarFile) throws IOException {

        String _working = FilenameUtils.concat(getWorkingDir(),
                FilenameUtils.getBaseName(tarFile.getPath()));
        if (_working == null) {
            throw new IOException("Invalid archive path for " + tarFile.getPath());
        }
        File workingDir = new File(_working);
        workingDir.mkdir();

        InputStream input = new BufferedInputStream(new FileInputStream(tarFile));
        TarArchiveInputStream in = null;
        try {
            in = (TarArchiveInputStream) (new ArchiveStreamFactory().createArchiveInputStream(
                    "tar", input));

            TarArchiveEntry tarEntry;
            while ((tarEntry = (TarArchiveEntry) in.getNextEntry()) != null) {
                if (filterEntry(tarEntry)) {
                    continue;
                }

                try {
                    File tmpFile = saveArchiveEntry(tarEntry, in, _working);
                    converter.convert(tmpFile);
                } catch (IOException err) {
                    log.error(
                            "Unable to save item, FILE=" + tarFile.getName() + "!"
                                    + tarEntry.getName(), err);
                }
            }
        } catch (ArchiveException ae) {
            throw new IOException(ae);
        } finally {
            in.close();
        }
        return workingDir;
    }

    /**
     * save to root dir
     * 
     * @param E
     * @param archiveio
     * @param root
     * @return
     * @throws IOException
     */
    private File saveArchiveEntry(ArchiveEntry E, InputStream archiveio, File root)
            throws IOException {
        return saveArchiveEntry(E, archiveio, root.getAbsolutePath());
    }

    /** */
    private File saveArchiveEntry(ArchiveEntry E, InputStream archiveio, String root)
            throws IOException {

        // Note: using native OS file path is fine here.  As long as you do not 
        // try any string mechanics on paths.
        //
        String targetPath = FilenameUtils.concat(root, E.getName());
        if (targetPath == null) {
            throw new IOException("Invalid archive entry target for " + E.getName());
        }
        File target = new File(targetPath);
        if (target.exists() && !overwrite) {
            return target;
        }

        target.getParentFile().mkdirs();
        log.debug("ARCHIVE_ENTRY={}", E.getName());
        OutputStream output = null;
        try {
            output = new FileOutputStream(target);
            IOUtils.copy(archiveio, output);
        } finally {
            output.close();
        }
        return target;
    }

    private boolean filterEntry(ArchiveEntry E) {
        if (E.isDirectory()) {
            return true;
        }
        if (filter.filterOutFile(E.getName())) {
            return true;
        }
        return false;
    }
}
