package org.opensextant.xtext.test;

import java.io.IOException;

import org.opensextant.ConfigException;
import org.opensextant.xtext.XText;

public class TestTikaPST {

    /** Compare Tika's PST conversion to XText non-Tika PST conversion.
     */
    public static void main(String args[]) {

        String input = args[0]; // Path to a PST.  NOTE, java-libpst provides some good test data.

        try {
            XText xt = new XText();
            xt.enableSaving(true);
            xt.enableTikaPST(true);
            xt.getPathManager().enableSaveWithInput(false); // creates a ./text/ Folder locally in directory.
            xt.clearSettings();
            xt.convertFileType("pst");

            xt.getPathManager().setConversionCache("./xtext-testing");
            xt.setup();
            xt.extractText(input);
        } catch (IOException ioerr) {
            ioerr.printStackTrace();
            System.err.println("IO issue" + ioerr.getMessage());

        } catch (ConfigException cfgerr) {
            cfgerr.printStackTrace();
            System.err.println("Config issue" + cfgerr.getMessage());
        }
    }

}
