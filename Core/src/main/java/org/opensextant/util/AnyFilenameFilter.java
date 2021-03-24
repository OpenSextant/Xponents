/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author ubaldino
 */
public class AnyFilenameFilter implements FilenameFilter {

    private String extension = null;

    /**
     * @param ext file extension
     */
    public AnyFilenameFilter(String ext) {
        extension = ext;
    }

    /**
     * FilenameFilter implementation for XML files
     *
     * @param dir   dir to filter on
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
