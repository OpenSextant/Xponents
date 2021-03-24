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
package org.opensextant.processing;

/**
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class ProcessingException extends Exception {

    protected static final long serialVersionUID = 20001981L;

    public ProcessingException(Exception ex) {
        super(ex);
    }

    public ProcessingException(String msg) {
        super(msg);
    }

    public ProcessingException(String msg, Exception ex) {
        super(msg, ex);
    }
}
