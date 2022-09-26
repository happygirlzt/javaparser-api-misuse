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

import javax.sound.midi.SysexMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommentRemover {
    private static final String ORIGINAL_PATH = "/Users/happygirlzt/Downloads/raw-files/";
    private static final String NO_COMMENT_PATH = "/Users/happygirlzt/Downloads/no-comment-files/";
    private static final String METHOD_PATH = "/Users/happygirlzt/Downloads/method-pairs/";
    private static final String HOME_PATH = "/Users/happygirlzt/Downloads/";

    static void RecursiveFindFiles(File[] arr, Set<File> files) {
        // for-each loop for main directory files
        for (File f : arr) {
            if (f.isFile()) {
               files.add(f);
            } else if (f.isDirectory()) {
                // recursion for sub-directories
                RecursiveFindFiles(Objects.requireNonNull(f.listFiles()), files);
            }
        }
    }

    public static Set<String> listFiles(String dir) {
        return Stream.of(Objects.requireNonNull(new File(dir).listFiles()))
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
        if (!parentDirectory.exists()) {
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

    public static void extractMethodByName(String filePath, String fileName, String targetMethodName,
                                           List<String> parsedErrors, String variant, int index)
            throws IOException {
        fileName = fileName.split("\\.")[0];
        try {
            CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
            List<MethodDeclaration> mds = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration md : mds) {
                String methodName = md.getNameAsString();
                if (!methodName.equals(targetMethodName)) {
                    continue;
                }
                String cur = md.toString();
                WriteToFile(cur, METHOD_PATH + index + "_" + fileName + "_"
                        + methodName + "_" + variant + ".java");
            }
        } catch(Exception e) {
            parsedErrors.add(filePath);
        }
    }

    public static void extractAllMethodsByName() throws Exception {
        JSONArray data = null;
        try {
            JSONParser parser = new JSONParser();
            //Use JSONObject for simple JSON and JSONArray for array of JSON.
            data = (JSONArray) parser.parse(
                    new FileReader("/Users/happygirlzt/Downloads/method-pairs-line.json"));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        assert data != null;
        List<String> parsedErrors = new ArrayList<>();
        int index = 0;
        for (Object dataObj : ProgressBar.wrap(data, "extract method with name")) {
            JSONObject item = (JSONObject) dataObj;
            String buggy_commit_path = (String) item.get("buggy_commit");
            String fixed_commit_path = (String) item.get("fixed_commit");

            String buggy_method_name = (String) item.get("buggy_method");
            String fixed_method_name = (String) item.get("fixed_method");

            String java_file_path = NO_COMMENT_PATH + buggy_commit_path;
            String[] paths = java_file_path.split("/");
            String fileName = paths[paths.length - 1];
            extractMethodByName(NO_COMMENT_PATH + buggy_commit_path, fileName,
                    buggy_method_name, parsedErrors, "buggy", index);

            java_file_path = NO_COMMENT_PATH + fixed_commit_path;
            paths = java_file_path.split("/");
            fileName = paths[paths.length - 1];
            extractMethodByName(NO_COMMENT_PATH + fixed_commit_path, fileName,
                    fixed_method_name, parsedErrors, "fixed", index);
            index += 1;
        }
        System.out.println("Parsed errors " + parsedErrors.size());
        saveErrors(parsedErrors, HOME_PATH + "parsed_errors.txt");
    }

    public static void saveErrors(List<String> obj, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
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

    public static void removeCommentAllFiles() throws Exception {
        // Remove comments
        List<File> directories = Arrays.asList(listDirectories(ORIGINAL_PATH));
        int errors = 0;
        List<String> errorFiles = new ArrayList<>();
        int totalFiles = 0;

        for (File directory : ProgressBar.wrap(directories, "remove comments")) {
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
        System.out.println("Parse errors " + errors);
        saveErrors(errorFiles, "/Users/happygirlzt/Downloads/error-files.txt");
    }

    public static void matchAllMethods() throws Exception {
        // For each method, we find the method name which covers the line
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
        // find the method which covers the line
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
//        removeCommentAllFiles();
//        handleErrors("/Users/happygirlzt/Downloads/error-files.txt");
        extractAllMethodsByName();
//          matchAllMethods();
    }
}