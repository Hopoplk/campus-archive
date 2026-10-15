package com.campus.archive.entity;

/** 安全设置：全局唯一一行。系统只保存派生校验值与盐，绝不保存明文安全密钥。 */
public class SecuritySetting {

    public static final String SINGLETON_ID = "security";

    private String id = SINGLETON_ID;
    private String keyHash;    // SHA-256(derivedKey)
    private String salt;       // 16 字节随机盐的十六进制
    private int kdfRounds;     // PBKDF2 迭代轮数
    private boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public int getKdfRounds() { return kdfRounds; }
    public void setKdfRounds(int kdfRounds) { this.kdfRounds = kdfRounds; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
