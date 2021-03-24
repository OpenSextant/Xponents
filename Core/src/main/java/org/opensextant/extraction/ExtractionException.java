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
package org.opensextant.extraction;

/**
 * An exception to be thrown when place name matching goes awry.
 *
 * @author ubaldino
 */
public class ExtractionException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 20011981L;

    /**
     *
     */
    public ExtractionException() {
    }

    /**
     * @param msg   error message
     * @param cause root cause
     */
    public ExtractionException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * @param msg error message
     */
    public ExtractionException(String msg) {
        super(msg);
    }
}
