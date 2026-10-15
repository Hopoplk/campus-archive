package com.campus.archive;

import com.campus.archive.config.AppContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** 测试夹具：每个测试类在独立的临时目录里跑，互不干扰，也不污染真实档案。 */
public final class TestEnv {

    private TestEnv() {
    }

    public static AppContext newContext(String tag) {
        try {
            Path home = Files.createTempDirectory("archive-test-" + tag + "-");
            return AppContext.forTesting(home);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 在夹具目录里写一个内容可控的假文件。 */
    public static java.io.File sample(Path dir, String name, String content) {
        try {
            Files.createDirectories(dir);
            Path p = dir.resolve(name);
            Files.writeString(p, content);
            return p.toFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 测试清理失败不影响断言结果
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }
}
