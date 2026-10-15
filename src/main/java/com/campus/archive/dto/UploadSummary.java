package com.campus.archive.dto;

import java.util.ArrayList;
import java.util.List;

/** 批量上传结果：区分成功与失败，并逐条给出失败原因（对应 AC-02）。 */
public class UploadSummary {

    /** 单条失败记录。 */
    public record Failure(String fileName, String code, String message) {
    }

    private final List<String> succeeded = new ArrayList<>();
    private final List<Failure> failed = new ArrayList<>();
    private final List<String> duplicateWarnings = new ArrayList<>();

    public void success(String fileName) {
        succeeded.add(fileName);
    }

    public void fail(String fileName, String code, String message) {
        failed.add(new Failure(fileName, code, message));
    }

    public void warnDuplicate(String fileName) {
        duplicateWarnings.add(fileName);
    }

    public List<String> getSucceeded() { return succeeded; }
    public List<Failure> getFailed() { return failed; }
    public List<String> getDuplicateWarnings() { return duplicateWarnings; }

    public int getSuccessCount() { return succeeded.size(); }
    public int getFailCount() { return failed.size(); }

    public String summaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("成功 ").append(succeeded.size()).append(" 个");
        if (!failed.isEmpty()) sb.append("，失败 ").append(failed.size()).append(" 个");
        if (!duplicateWarnings.isEmpty()) sb.append("，疑似重复 ").append(duplicateWarnings.size()).append(" 个");
        return sb.toString();
    }

    public boolean hasFailure() {
        return !failed.isEmpty();
    }
}
