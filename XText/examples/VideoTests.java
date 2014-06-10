// package org.opensextant.xtext.test;

import java.io.IOException;

import org.opensextant.xtext.ConversionListener;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.XText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A trivial test program that allows you to see if Tika will get text by default from Video file formats you have.
 */
public class VideoTests implements ConversionListener {
    Logger log = LoggerFactory.getLogger(getClass());

    public void run(String input) throws IOException {

        XText xt = new XText();
        xt.enableSaveWithInput(false);
        xt.setArchiveDir("video-output");

        xt.enableSaving(true);
        xt.convertFileType("mp4");
        xt.convertFileType("mpeg");
        xt.convertFileType("mpg");
        xt.convertFileType("avi");
        xt.convertFileType("wmv");

        xt.setMaxFileSize(0x8000000);
        xt.setup();
        xt.setConversionListener(this);
        xt.extractText(input);

    }

    public void handleConversion(ConvertedDocument d, String fpath) {
        log.info("FILE=" + d.filename + " Converted?=" + d.is_converted + " ID={} PATH={}", d.id,
                fpath);
        d.setDefaultID();
        log.info("\t\tTry resetting Doc ID to default ID = " + d.id);
    }

    public static void main(String[] args) {

        try {
            new VideoTests().run(args[0]);
        } catch (IOException err) {
            err.printStackTrace();
        }
    }

}
