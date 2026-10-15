package com.campus.archive;

/**
 * 启动器。
 *
 * <p>JavaFX 在检测到主类直接继承 {@link javafx.application.Application} 且 JavaFX 位于类路径
 * （而非模块路径）时会拒绝启动，报"缺少 JavaFX 运行时组件"。用一个不继承 Application 的
 * 入口类转调 {@link ArchiveApp#main} 即可绕开该检查，这也是打包可执行 JAR 的标准做法。</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ArchiveApp.main(args);
    }
}
