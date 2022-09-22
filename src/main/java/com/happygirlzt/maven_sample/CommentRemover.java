/**
 * Created by happygirlzt on 23/2/22 10:35 AM
 */

package com.happygirlzt.maven_sample;
import com.github.javaparser.Position;
import me.tongfei.progressbar.*;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.Node;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommentRemover {
    private static final String ORIGINAL_PATH = "/Users/happygirlzt/Downloads/raw-files/";
    private static final String NO_COMMENT_PATH = "/Users/happygirlzt/Downloads/no-comment-javas/";
    private static final String METHOD_PATH = "/Users/happygirlzt/Downloads/Spring-DATAMONGO-methods-java-1/";

//    public static Set<String> tooLong = new HashSet<>();

    // private static final String ORIGINAL_PATH = "/Users/happygirlzt/Downloads/ErrorFileInvestigation/";
    // private static final String NO_COMMENT_PATH = "/Users/happygirlzt/Downloads/no-comment-error-files/";

    static void RecursiveFindFiles(File[] arr, Set<File> files) {
        // for-each loop for main directory files
        for (File f : arr) {
            if (f.isFile()) {
               files.add(f);
            } else if (f.isDirectory()) {
                // recursion for sub-directories
                RecursiveFindFiles(f.listFiles(), files);
            }
        }
    }

    public static Set<String> listFiles(String dir) {
        return Stream.of(new File(dir).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());
    }

    public static File[] listDirectories(String dir) {
        return new File(dir).listFiles(File::isDirectory);
    }

    public static void WriteToFile(String toWrite, String outName) throws IOException {
        File f = new File(outName);
        String parent = f.getParent();
        File parentDirectory = new File(parent);
        if (! parentDirectory.exists()) {
            parentDirectory.mkdirs();
        }
        // handle long file name
//        if (filename.length() > 255) {
//            tooLong.add(filename);
//            int slashIndex = outName.lastIndexOf('/');
//            outName = outName.substring(0, slashIndex) + filename.substring(255);
//        }

        FileOutputStream outputStream = new FileOutputStream(outName);
        byte[] strToBytes = toWrite.getBytes();
        outputStream.write(strToBytes);
        outputStream.close();
    }

    public static void extractMethods(String filePath, String fileName) throws IOException {
        int dotIndex = fileName.lastIndexOf('.');
        fileName = fileName.substring(0, dotIndex);

        try {
            CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
            List<MethodDeclaration> mds = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration md : mds) {
                String methodName = md.getNameAsString();
                String cur = md.toString();
                WriteToFile(cur, METHOD_PATH + fileName + "_" + methodName + ".java");
            }
        } catch(Exception e) {
            System.out.println(fileName);
        }
    }

    public static void saveErrors(Set<String> obj, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(path), "UTF-8"))) {
            for (String s : obj) {
                pw.println(s);
            }
            pw.flush();
        }
    }

    public static void handleErrors(String errorFilePath) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(errorFilePath));
            String line = reader.readLine();
            while (line != null) {
                // read next line
                line = reader.readLine();
                System.out.println(line);
                CompilationUnit cu = StaticJavaParser.parse(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void handleAllFiles() throws Exception {
        List<File> directories = Arrays.asList(listDirectories(ORIGINAL_PATH));
        int errors = 0;
        Set<String> errorFiles = new HashSet<>();
        int totalFiles = 0;

        for (File directory : ProgressBar.wrap(directories, "check directory")) {
            File[] tmpDirectory = new File[1];
            tmpDirectory[0] = directory;
            Set<File> files = new HashSet<>();
            RecursiveFindFiles(tmpDirectory, files);

            for (File inputFilePath : files) {
                totalFiles += 1;
                String[] paths = String.valueOf(inputFilePath).split("/");
                paths[4] = "no-comment-files";
                String outputFilePath = String.join("/", paths);

                CompilationUnit cu = null;
                try {
                    cu = StaticJavaParser.parse(inputFilePath);
                } catch (Exception e) {
                    errors += 1;
                    if (errors % 50 == 0) {
                        System.out.println("failed " + errors);
                    }
                    errorFiles.add(String.valueOf(inputFilePath));
                    continue;
                }

                List<Comment> comments = cu.getAllContainedComments();
                List<Comment> unwantedComments = comments
                        .stream()
                        .filter(p -> p.isLineComment() || p.isBlockComment())
                        .filter(p -> p.getCommentedNode().isEmpty() || p.isLineComment() || p.isBlockComment())
                        .collect(Collectors.toList());
                unwantedComments.forEach(Node::remove);
                comments.forEach(Node::remove);
                WriteToFile(cu.toString(), outputFilePath);
            }
        }

        System.out.println("Total files are " + totalFiles);
        saveErrors(errorFiles, "/Users/happygirlzt/Downloads/error-files.txt");
    }

    public static void matchAllMethods() throws Exception {
        JSONArray data = null;
        try {
            JSONParser parser = new JSONParser();
            //Use JSONObject for simple JSON and JSONArray for array of JSON.
            data = (JSONArray) parser.parse(
                    new FileReader("/Users/happygirlzt/Downloads/method_lines.json"));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        assert data != null;
        JSONArray methodList = new JSONArray();
        for (Object dataObj :  ProgressBar.wrap(data, "get method name")) {
            JSONObject item = (JSONObject) dataObj;
            String buggy_commit_path = (String) item.get("buggy_commit");
            String fixed_commit_path = (String) item.get("fixed_commit");
            int buggy_line = Integer.parseInt((String) item.get("buggy_line"));
            int fixed_line = Integer.parseInt((String) item.get("fixed_line"));
            String buggy_method_name = matchMethod(ORIGINAL_PATH + buggy_commit_path, buggy_line);
            String fixed_method_name = matchMethod(ORIGINAL_PATH + fixed_commit_path, fixed_line);

            JSONObject methodInfo = new JSONObject();
            methodInfo.put("buggy_commit", buggy_commit_path);
            methodInfo.put("buggy_line", buggy_line);
            methodInfo.put("buggy_method", buggy_method_name);
            methodInfo.put("fixed_commit", fixed_commit_path);
            methodInfo.put("fixed_line", fixed_line);
            methodInfo.put("fixed_method", fixed_method_name);
            methodList.add(methodInfo);
        }
        try (FileWriter file = new FileWriter("/Users/happygirlzt/Downloads/method-pairs-line.json")) {
            //We can write any JSONArray or JSONObject instance to the file
            file.write(methodList.toJSONString());
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String matchMethod(String filePath, int line) throws IOException {
        String methodName = "";
        try {
            CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
            List<MethodDeclaration> mds = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration md : mds) {
                int beginLineNum = -1, endLineNum = -1;
                Optional<Position> beginLine = md.getBegin();
                if (beginLine.isPresent()) {
                    beginLineNum = beginLine.get().line;
                };

                Optional<Position> endLine = md.getEnd();
                if (endLine.isPresent()) {
                    endLineNum = endLine.get().line;
                };

                if (line >= beginLineNum && line <= endLineNum) {
                    methodName = String.valueOf(md.getName());
                    break;
                }
//                WriteToFile(cur, METHOD_PATH + fileName + "_" + methodName + ".java");
            }
        } catch(Exception e) {
            return methodName;
        }
        return methodName;
    }

    public static void main(String[] args) throws Exception {
//        handleAllFiles();
//        handleErrors("/Users/happygirlzt/Downloads/error-files.txt");
          matchAllMethods();
//        matchMethods("/Users/happygirlzt/Downloads/raw-files/zzzlift/Algorithm/buggy/88e2a7569a31caeb89d0b53eda3b1ed5c8f28e86/Graph_AdjList_int2_dfs.java",
//                "Graph_AdjList_int2_dfs.java");
    }
//        for (File dire : directories) {
//            System.out.println(String.valueOf(dire));
//            Set<String> javaFiles = listFiles(String.valueOf(dire));
//            System.out.println(javaFiles.size());
//
//            for (String javaFile : javaFiles) {
//                String inputFileName = ORIGINAL_PATH + javaFile;
//                String outputFileName = NO_COMMENT_PATH + javaFile;
//
//    //            extractMethods(inputFileName, javaFile);
//
//                CompilationUnit cu = null;
//                try {
//                     cu = StaticJavaParser.parse(new File(inputFileName));
//                } catch (Exception e) {
//                    errors += 1;
//                    if (errors % 50 == 0) {
//                        System.out.println("failed " + errors);
//                    }
//                    errorFiles.add(javaFile);
//                    continue;
//                }
//
//                List<Comment> comments = cu.getAllContainedComments();
//
//                List<Comment> unwantedComments = comments
//                 .stream()
//                 .filter(p -> p.isLineComment() || p.isBlockComment())
//                 .filter(p -> !p.getCommentedNode().isPresent() || p.isLineComment() || p.isBlockComment())
//                 .collect(Collectors.toList());
//                unwantedComments.forEach(Node::remove);
//
//                 comments.forEach(Node::remove);
//
//                 WriteToFile(cu.toString(), outputFileName);
//            }
//        }
//
//        File directory = new File(NO_COMMENT_PATH);
//        if (! directory.exists()) {
//            directory.mkdir();
//            System.out.println("create no comment javas folder");
//        }
//
//        File methDirectory = new File(METHOD_PATH);
//        if (! methDirectory.exists()) {
//            methDirectory.mkdir();
//            System.out.println("create method javas folder");
//        }
//
//        saveErrors(tooLong, "/Users/happygirlzt/Downloads/tooLong-Spring-DATAMONGO.txt");
}