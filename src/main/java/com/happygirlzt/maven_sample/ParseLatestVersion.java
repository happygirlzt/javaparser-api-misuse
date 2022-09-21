/**
 * Created by happygirlzt on 23 May 2022
 */

package com.happygirlzt.maven_sample;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

public class ParseLatestVersion {
    public static void parseAllCode(String file) {
        try {
            // Create an object of file reader
            // class with CSV file as a parameter.
            FileReader filereader = new FileReader(file);

            // create csvReader object and skip first Line
            CSVReader csvReader = new CSVReader(filereader);
            List<String[]> allData = csvReader.readAll();
            List<String> latestVersions = new ArrayList<>();
            int errorCount = 0;

            // print Data
            for (String[] row : allData) {
                int totalCells = row.length;
                // the last cell is the latests version
                String curLastestVersion = row[totalCells - 1];
                try {
                    CompilationUnit cu = StaticJavaParser.parse(curLastestVersion);
                } catch(Exception e) {
                    System.out.println(curLastestVersion);
                    errorCount += 1;
                    if (errorCount == 5) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void alterParseCode(String file) {
        try {
            Scanner scanner = new Scanner(new File(file));
            int errorCount = 0;
            scanner.nextLine();

            while (scanner.hasNextLine()) {
                String columns[] = scanner.nextLine().split(",");
                System.out.println(columns.length);

                String curLatestVersion = columns[3];

                try {
                    CompilationUnit cu = StaticJavaParser.parse(curLatestVersion);
                } catch(Exception e) {
                    System.out.println(curLatestVersion);
                    errorCount += 1;
                    if (errorCount == 5) {
                        return;
                    }
                }
            }
            scanner.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static final String ORIGINAL_CSV = "src/main/resources/code_edits.csv";
    private static final String LATEST_CSV = "src/main/resources/latest_versions.csv";
    private static final String REFINED_CSV = "src/main/resources/refined_code_edits.csv";
    private static final String LATEST_JAVA_PATH = "src/main/resources/latest/";
    private static final String INVESTIGATE_PATH = "src/main/resources/investigate/";
    public static Set<String> listFiles(String dir) {
        return Stream.of(new File(dir).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());
    }


    public static void main(String[] args) throws Exception {
        String targetFolder = INVESTIGATE_PATH;
        Set<String> javaFiles = listFiles(targetFolder);
        int errors = 0;
        List<String> errorList = new ArrayList<>();
        List<String> errorMsgList = new ArrayList<>();

        int count = 0;
        for (String javaFile : javaFiles) {
            String inputFileName = targetFolder + javaFile;
            if (count % 50 == 0) {
                System.out.println(count);
            }
            count += 1;
            CompilationUnit cu = null;

            try {
//                cu = StaticJavaParser.parse(new File(inputFileName));
                cu = StaticJavaParser.parse(new File(inputFileName));
            } catch (ParseProblemException e) {
                System.out.println(javaFile);
                System.out.println(e.getMessage());
                errors += 1;
                errorMsgList.add(e.toString());
                int underScoreIndex = inputFileName.lastIndexOf('_');
                int dotIndex = inputFileName.length() - 5;
                String index = inputFileName.substring(underScoreIndex + 1, dotIndex);
                errorList.add(index);
            }
        }

//        FileWriter writer = new FileWriter("src/main/resources/error_index_parser.txt");
//        for (String str: errorList) {
//            writer.write(str + "\n");
//        }
//        writer.close();
//
//        writer = new FileWriter("src/main/resources/error_message.txt");
//        for (String str: errorMsgList) {
//            writer.write(str + "\n");
//        }
//        writer.close();
//        parseAllCode(LATEST_CSV);
//        alterParseCode(ORIGINAL_CSV);
    }
}