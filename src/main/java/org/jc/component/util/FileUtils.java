package org.jc.component.util;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static Path getOrCreateFilePath(Path path) throws IOException {
        if (!FileUtils.exists(path)) {
            // 1. 递归创建所有父目录（已存在不报错，幂等）
            Files.createDirectories(path.getParent());
            // 2. 只有文件不存在时才创建（已存在跳过，幂等）
            Files.createFile(path);
        }
        return path;
    }

    public static Path getOrCreateFilePath(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return FileUtils.getOrCreateFilePath(path);
    }

    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    public static boolean isJsonl(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase().endsWith(".jsonl");
    }

    public static Path resolve(Path path, String subPath, boolean init) throws IOException {
        Path resolvePath = path.resolve(subPath);
        if (init) {
            return FileUtils.getOrCreateFilePath(resolvePath);
        }
        return resolvePath;
    }

    public static Path resolve(String path, String subPath, boolean init) throws IOException {
        return FileUtils.resolve(Paths.get(path), subPath, init);
    }

    public static <T> List<T> readList(String filePath, Class<T> tClass) throws IOException {
        Path path = FileUtils.getOrCreateFilePath(filePath);
        return FileUtils.readList(path, tClass);
    }

    public static <T> T read(String filePath, Class<T> tClass) throws IOException {
        Path path = FileUtils.getOrCreateFilePath(filePath);
        return FileUtils.read(path, tClass);
    }

    public static String read(String filePath) throws IOException {
        Path path = FileUtils.getOrCreateFilePath(filePath);
        return Files.readString(path);
    }

    public static <T> List<T> readList(Path filePath, Class<T> tClass) throws IOException {
        if (FileUtils.isJsonl(filePath)) {
            List<String> lines = Files.readAllLines(filePath);
            List<T> tList = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                tList.add(JSON.parseObject(line.replaceAll("[\n\r]", ""), tClass));
            }
            return tList;
        }

        String json = FileUtils.read(filePath);
        if (json.isBlank()) {
            return new ArrayList<>();
        }
        return JSON.parseArray(json, tClass);
    }

    public static <T> T read(Path filePath, Class<T> tClass) throws IOException {
        String json = FileUtils.read(filePath);
        if (json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, tClass);
    }

    public static String read(Path filePath) throws IOException {
        return Files.readString(filePath);
    }


    public static void write(String filePath, Object content) throws IOException {
        FileUtils.write(FileUtils.getOrCreateFilePath(filePath), content);
    }

    public static void write(Path filePath, Object content) throws IOException {
        if (filePath == null) {
            return;
        }

        String jsonString = JSON.toJSONString(content);
        if (FileUtils.isJsonl(filePath)) {
            jsonString = jsonString + "\n";
        }
        //保证目录存在
        if (!FileUtils.exists(filePath)) {
            //第一次
            Files.write(filePath, jsonString.getBytes(), StandardOpenOption.CREATE);
        } else if (FileUtils.isJsonl(filePath)) {
            //追加
            Files.write(filePath, jsonString.getBytes(), java.nio.file.StandardOpenOption.APPEND);
        } else {
            //覆盖
            Files.write(filePath, jsonString.getBytes(), java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public static void clear(Path filePath) throws IOException {
        Files.write(filePath, new byte[0]);
    }
}
