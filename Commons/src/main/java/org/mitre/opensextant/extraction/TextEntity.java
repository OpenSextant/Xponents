/**
 *
 *  Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
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
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//   OpenSextant Commons
// *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */

package org.mitre.opensextant.extraction;

/**
 * Modeled after FlexPat TextMatch or GATE's Annotation -- this is intended to be
 * a very simple struct to hold data useful for post-processing entities once found.
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class TextEntity {

    /**
     *
     */
    protected String text = null;
    // the location this was found in the document
    /**
     *
     */
    public long start = -1L;
    /**
     *
     */
    public long end = -1L;
    // Use this 
    private String context = null;
    // OR this
    private String prematch = null;
    private String postmatch = null;
    /** */
    public String match_id = null;
    /** If this entity is contained completely within some other     */
    public boolean is_submatch = false;
    /** If this entity is a overlaps with some other     */
    public boolean is_overlap = false;
    /** If this entity is a duplicate of some other     */
    public boolean is_duplicate = false;

    /**
     *
     */
    public TextEntity() {
    }

    /**
     *
     * @param t
     */
    public void setText(String t) {
        text = t;
    }

    /**
     *
     * @return
     */
    public String getText() {
        return text;
    }

    /** get the length of the matched text 
     * @return 
     */
    public int match_length() {
        if (start < 0) {
            // Match not initialized
            return 0;
        }
        return (int) (end - start);
    }

    /** Convenience methods for carrying the context through the output processing */
    /** Set the context with before and after windows
     * @param before
     * @param after  
     */
    public void setContext(String before, String after) {
        this.prematch = before;
        this.postmatch = after;
        StringBuilder buf = new StringBuilder();
        buf.append(this.prematch);
        buf.append(" ");
        buf.append(this.text);
        buf.append(" ");
        buf.append(this.postmatch);
        this.context = buf.toString();
    }

    /** Set the context buffer from a single window
     * @param window 
     */
    public void setContext(String window) {
        this.context = window;
    }

    /**
     *
     * @return
     */
    public String getContext() {
        return this.context;
    }

    /**
     *
     * @return
     */
    public String getContextBefore() {
        return this.prematch;
    }

    /**
     *
     * @return
     */
    public String getContextAfter() {
        return this.postmatch;
    }
    
    /**
     *
     * @return
     */
    @Override
    public String toString() {
        return text + " @(" + start + ":" + end + ")";
    }

    /**
     *
     * @param m
     */
    public void copy(TextEntity m) {

        // TextMatch generic stuff:
        this.text = m.text;
        this.start = m.start;
        this.end = m.end;
        this.is_duplicate = m.is_duplicate;
        this.is_overlap = m.is_overlap;
        this.is_submatch = m.is_submatch;
        
        // These are private.  maybe should use this.setA(m.getA())
        this.postmatch = m.postmatch;
        this.prematch = m.prematch;
        this.context = m.context;
        this.match_id = m.match_id;
    }    
}
