package org.opensextant.data;

public class LanguageCharacterization {

    /** flag whether you have assessed the text or not.
     * if other flags are set, then mark characterized=True
     */
    public boolean characterized = false;

    /** if text contains any Chinese, Japanese or Korean
     */
    public boolean hasCJK = false;

    /** if text contains any Arabic scripts.
     */
    public boolean hasMiddleEastern = false;

}
