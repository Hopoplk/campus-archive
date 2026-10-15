package com.campus.archive.service;

import com.campus.archive.dto.Result;

import java.util.List;

/** 私密空间与安全密钥服务（详细设计 9.1 节）。 */
public interface PrivateSecurityService {

    boolean isEnabled();

    /** 私密空间是否处于已解锁状态（会话级，退出或空闲超时后自动回到锁定）。 */
    boolean isUnlocked();

    Result<Void> initKey(char[] rawKey);

    /** 校验并解锁。失败只返回通用提示，不区分失败原因（对应 AC-05）。 */
    Result<Void> unlock(char[] rawKey);

    void lock();

    /** 将材料转入加密存储区；需已解锁，且 rawKey 由会话密钥承担。 */
    Result<Void> markPrivate(String materialId);

    /** 取消私密保护并二次确认后执行；需已解锁。 */
    Result<Void> unmarkPrivate(String materialId);

    /** 读取私密材料明文；未解锁时返回 E202，密文被篡改时返回 E203。 */
    Result<byte[]> readVault(String materialId);

    int failCount();
}
