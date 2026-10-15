package com.campus.archive.entity;

/** 日程事项状态。 */
public enum ScheduleStatus {
    PENDING, DONE, OVERDUE;

    public String label() {
        return switch (this) {
            case PENDING -> "进行中";
            case DONE -> "已完成";
            case OVERDUE -> "已逾期";
        };
    }
}
