package com.campus.archive.dao;

import com.campus.archive.entity.Tag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 标签数据访问。 */
public interface TagDao {

    @Select("SELECT id, name FROM tag ORDER BY name")
    List<Tag> findAll();

    @Select("SELECT id, name FROM tag WHERE name = #{name}")
    Tag findByName(@Param("name") String name);

    @Select("""
            SELECT t.id, t.name FROM tag t
            JOIN material_tag mt ON mt.tag_id = t.id
            WHERE mt.material_id = #{materialId} ORDER BY t.name
            """)
    List<Tag> listByMaterial(@Param("materialId") String materialId);

    @Insert("INSERT OR IGNORE INTO tag(id, name) VALUES(#{id}, #{name})")
    int insert(Tag tag);

    @Insert("INSERT OR IGNORE INTO material_tag(material_id, tag_id) VALUES(#{materialId}, #{tagId})")
    int attach(@Param("materialId") String materialId, @Param("tagId") String tagId);

    @Delete("DELETE FROM material_tag WHERE material_id = #{materialId}")
    int detachAll(@Param("materialId") String materialId);

    @Delete("DELETE FROM material_tag WHERE material_id = #{materialId} AND tag_id = #{tagId}")
    int detach(@Param("materialId") String materialId, @Param("tagId") String tagId);
}
