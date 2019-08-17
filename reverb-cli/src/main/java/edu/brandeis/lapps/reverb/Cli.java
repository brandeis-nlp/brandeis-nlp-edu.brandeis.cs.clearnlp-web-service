package edu.brandeis.lapps.reverb;

import edu.brandeis.lapps.CliUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Cli {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            help();
        } else {

            ReverbRelationExtractor tool = new ReverbRelationExtractor();
            if (args[0].equals("-")) {
                System.out.println(CliUtil.processInputStream(tool, System.in));
            } else {
                Path inputPath = Paths.get(args[0]);
                if (Files.exists(inputPath) && Files.isDirectory(inputPath)) {
                    CliUtil.processDirectory(tool, inputPath, "rel");
                } else {
                    System.out.println(CliUtil.processInputStream(tool, new FileInputStream(inputPath.toFile())));
                }
            }
        }
    }

    private static void help() {
        System.out.println("Usage: java -jar jar (input)" +
                "\n" +
                "\n       Annotate input file using Reverb Relation Extraction." +
                "\n       Pass an optional argument to specify input." +
                "\n       If the input is a file, annotated output will be written to " +
                "\"         STDOUT and can be piped to any other processing. " +
                "\n       If the input is a directory, a new timestamped-subdirectory will be created " +
                " \n        named \"rel\", and *.lif (not starting with a dot) " +
                "\n         files in the original directory will be annotated and " +
                "\n         the results are written in the subdirectory." +
                "\n       And finally, the input is \"-\", STDIN will be read in. "
        );
    }

}

