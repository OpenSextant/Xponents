package org.opensextant.xtext.test;

import java.io.File;
import java.io.IOException;

import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.converters.EmbeddedContentConverter;

public class Decomposer {

    public static void usage() {
        System.out.println("Decomposer -i input  [-h] [-o output] [-e]");
        System.out.println("  input is file or folder");
        System.out.println("  output is a folder where you want to archive converted docs");
        System.out.println("  -e embeds the saved, conversions in the input folder in 'xtext' folders in input tree");
        System.out.println("  NOTE: -e has same effect as setting output to input");
        System.out.println("  -h enables HTML scrubbing");
    }

    public static void main(String[] args) {

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("Decomposer", args, "hei:o:");

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
                    System.out.println("Saving conversions to Input folder.  Output folder will be ignored.");
                    break;
                default:
                    Decomposer.usage();
                    System.exit(1);
                }
            }
        } catch (Exception err) {
            Decomposer.usage();
            System.exit(1);
        }

        EmbeddedContentConverter conv = new EmbeddedContentConverter(0x200000);
        ConvertedDocument d;
        try {
            d = conv.convert(new File(input));
            System.out.println("Found Doc:" + d.getFilepath());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
