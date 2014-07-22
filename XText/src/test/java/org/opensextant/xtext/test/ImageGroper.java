package org.opensextant.xtext.test;

import java.io.IOException;

import org.opensextant.ConfigException;
import org.opensextant.xtext.XText;

public class ImageGroper {

    public static void usage() {
        System.out.println("ImageGroper -i input  [-h] [-o output] [-e]");
        System.out.println("  input is file or folder");
        System.out.println("  output is a folder where you want to archive converted docs");
        System.out
                .println("  -e embeds the saved, conversions in the input folder in 'xtext' folders in input tree");
        System.out.println("  NOTE: -e has same effect as setting output to input");
        System.out.println("  -h enables HTML scrubbing");
    }

    public static void main(String[] args) {

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("ImageGroper", args, "hei:o:");

        String input = null;
        String output = null;
        boolean embed = false;

        try {

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'i':
                    input = opts.getOptarg();
                    break;
                case 'o':
                    output = opts.getOptarg();
                    break;

                case 'e':
                    embed = true;
                    System.out
                            .println("Saving conversions to Input folder.  Output folder will be ignored.");
                    break;
                default:
                    ImageGroper.usage();
                    System.exit(1);
                }
            }
        } catch (Exception err) {
            ImageGroper.usage();
            System.exit(1);
        }
        // Setting LANG=en_US in your shell.
        //
        // System.setProperty("LANG", "en_US");
        XText xt = new XText();
        xt.enableSaving(true);
        xt.getPathManager().enableSaveWithInput(embed); // creates a ./text/ Folder locally in directory.
        xt.clearSettings();
        xt.convertFileType("jpg");
        xt.convertFileType("jpeg");

        try {
            xt.getPathManager().setConversionCache(output);
            xt.setup();
            xt.extractText(input);
        } catch (IOException ioerr) {
            ioerr.printStackTrace();

        } catch (ConfigException cfgerr) {
            cfgerr.printStackTrace();
        }
    }
}
