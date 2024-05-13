import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.util.FileUtility;

public class XTaxTester {

    public static void xtax(String... args) {
        /*
            A trivial demonstration of using TaxonMatcher -- aka XTax or TaxCat
         */
        try {

            String text = null;
            String input = null;

            Options options = new Options();

            options.addOption("i", "input", true, "an input file");
            options.addOption("t", "text", false, "an input text string");
            options.addOption("l", "lang", true, "ISO language for input file or text");
            options.addOption("h", "help", false, "this help");
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd;
            HelpFormatter help = new HelpFormatter();
            cmd = parser.parse(options, args);
            String opt = "input";
            if (cmd.hasOption(opt)) {
                input = cmd.getOptionValue(opt);
            }
            opt = "text";
            if (cmd.hasOption(opt)) {
                text = input;
            } else {
                text = FileUtility.readFile(input);
            }

            TextInput content = new TextInput("test", text);
            if (cmd.hasOption("lang")) {
                // Has to be a valid lang ID.
                content.langid = cmd.getOptionValue("lang");
            }
            Extractor ex = new TaxonMatcher();
            List<TextMatch> tags = ex.extract(content);
            for (TextMatch t : tags) {
                System.out.println(t);
            }
            ex.cleanup();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public static void main(String[] args) {
        xtax(args);
    }
}
