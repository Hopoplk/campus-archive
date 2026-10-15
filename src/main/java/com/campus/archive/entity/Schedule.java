package com.campus.archive.entity;

import java.time.LocalDateTime;

/** 日程事项，对应 schedule 表。 */
public class Schedule {

    private String id;
    private String title;
    private LocalDateTime dueTime;
    private LocalDateTime remindAt;
    private ScheduleStatus status = ScheduleStatus.PENDING;
    private boolean reminded;      // 提醒幂等标记
    private String remark;

    public boolean isPending() {
        return status != ScheduleStatus.DONE;
    }

    public String getStatusLabel() {
        return status == null ? "" : status.label();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }

    public LocalDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(LocalDateTime remindAt) { this.remindAt = remindAt; }

    public ScheduleStatus getStatus() { return status; }
    public void setStatus(ScheduleStatus status) { this.status = status; }

    public boolean getReminded() { return reminded; }
    public void setReminded(boolean reminded) { this.reminded = reminded; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
