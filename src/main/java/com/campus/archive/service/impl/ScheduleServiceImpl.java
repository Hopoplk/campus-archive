package com.campus.archive.service.impl;

import com.campus.archive.config.Repositories;
import com.campus.archive.dto.Result;
import com.campus.archive.entity.Schedule;
import com.campus.archive.entity.ScheduleStatus;
import com.campus.archive.service.ScheduleService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 日程与提醒实现。 */
public class ScheduleServiceImpl implements ScheduleService {

    private final Repositories repos;

    public ScheduleServiceImpl(Repositories repos) {
        this.repos = repos;
    }

    @Override
    public Result<Void> create(Schedule item) {
        if (item == null || item.getTitle() == null || item.getTitle().isBlank()) {
            return Result.fail("E300", "请填写事项名称");
        }
        if (item.getDueTime() == null) {
            return Result.fail("E300", "请选择截止时间");
        }
        if (item.getRemindAt() != null && item.getRemindAt().isAfter(item.getDueTime())) {
            return Result.fail("E300", "提醒时间不能晚于事项截止时间");
        }
        if (item.getId() == null) item.setId(UUID.randomUUID().toString());
        if (item.getStatus() == null) item.setStatus(ScheduleStatus.PENDING);
        item.setReminded(false);
        repos.query(d -> d.schedule().insert(item));
        return Result.ok();
    }

    @Override
    public Result<Void> complete(String id) {
        repos.query(d -> d.schedule().updateStatus(id, ScheduleStatus.DONE.name()));
        return Result.ok();
    }

    @Override
    public Result<Void> delete(String id) {
        repos.query(d -> d.schedule().delete(id));
        return Result.ok();
    }

    @Override
    public List<Schedule> listAll() {
        return repos.query(d -> d.schedule().findAll());
    }

    @Override
    public List<Schedule> listUpcoming(int days) {
        LocalDateTime now = LocalDateTime.now();
        return repos.query(d -> d.schedule().listUpcoming(now, now.plusDays(Math.max(1, days))));
    }

    @Override
    public int countPending() {
        return repos.query(d -> d.schedule().countPending());
    }

    @Override
    public List<Schedule> findDue(LocalDateTime now) {
        return repos.query(d -> d.schedule().findDueReminders(now));
    }

    @Override
    public void markReminded(String id) {
        repos.query(d -> d.schedule().markReminded(id));
    }

    @Override
    public void markOverdue(String id) {
        repos.query(d -> d.schedule().updateStatus(id, ScheduleStatus.OVERDUE.name()));
    }
}
