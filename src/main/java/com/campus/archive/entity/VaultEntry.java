package com.campus.archive.entity;

/** 私密条目：记录密文位置与 AES-GCM 的 IV。 */
public class VaultEntry {

    private String id;
    private String materialId;
    private String encryptedPath;
    private String iv;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; }

    public String getEncryptedPath() { return encryptedPath; }
    public void setEncryptedPath(String encryptedPath) { this.encryptedPath = encryptedPath; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
}
