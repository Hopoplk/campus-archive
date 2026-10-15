package com.campus.archive.pattern.export;

/**
 * 私密空间闸门。模板类只通过这个窄接口询问"现在能不能读私密内容"，
 * 从而完全不依赖 service 包，避免 pattern 与 service 形成双向依赖。
 */
@FunctionalInterface
public interface VaultGate {

    boolean isUnlocked();
}
