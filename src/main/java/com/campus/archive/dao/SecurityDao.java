package com.campus.archive.dao;

import com.campus.archive.entity.SecuritySetting;
import com.campus.archive.entity.VaultEntry;
import org.apache.ibatis.annotations.*;

/** 安全设置与私密条目数据访问。注意：表里只有盐与校验值，永远不会有明文密钥。 */
public interface SecurityDao {

    @Select("SELECT * FROM security_setting WHERE id = #{id}")
    SecuritySetting findSetting(@Param("id") String id);

    @Insert("""
            INSERT INTO security_setting(id, key_hash, salt, kdf_rounds, enabled)
            VALUES(#{id}, #{keyHash}, #{salt}, #{kdfRounds}, #{enabled})
            """)
    int insertSetting(SecuritySetting s);

    @Update("UPDATE security_setting SET key_hash = #{keyHash}, salt = #{salt}, kdf_rounds = #{kdfRounds} WHERE id = #{id}")
    int updateSetting(SecuritySetting s);

    @Insert("""
            INSERT INTO vault_entry(id, material_id, encrypted_path, iv)
            VALUES(#{id}, #{materialId}, #{encryptedPath}, #{iv})
            """)
    int insertVault(VaultEntry v);

    @Select("SELECT * FROM vault_entry WHERE material_id = #{materialId}")
    VaultEntry findVaultByMaterial(@Param("materialId") String materialId);

    @Delete("DELETE FROM vault_entry WHERE material_id = #{materialId}")
    int deleteVault(@Param("materialId") String materialId);

    @Select("SELECT COUNT(*) FROM vault_entry")
    int countVault();
}
