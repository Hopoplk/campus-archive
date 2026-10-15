package com.campus.archive.dao;

import com.campus.archive.entity.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 分类数据访问（MyBatis 注解映射）。分类是一棵树，靠 parent_id 自关联。 */
public interface CategoryDao {

    @Select("SELECT id, name, parent_id, sort FROM category ORDER BY sort, name")
    List<Category> findAll();

    @Select("SELECT id, name, parent_id, sort FROM category WHERE id = #{id}")
    Category findById(@Param("id") String id);

    @Select("SELECT id, name, parent_id, sort FROM category WHERE name = #{name} LIMIT 1")
    Category findByName(@Param("name") String name);

    /** 同一父节点下是否已有同名分类，用于挡住重复命名。 */
    @Select("SELECT COUNT(*) FROM category WHERE name = #{name} AND IFNULL(parent_id,'') = IFNULL(#{parentId},'')")
    int countSiblingWithName(@Param("name") String name, @Param("parentId") String parentId);

    @Insert("INSERT INTO category(id, name, parent_id, sort) VALUES(#{id}, #{name}, #{parentId}, #{sort})")
    int insert(Category c);

    @Update("UPDATE category SET name = #{name} WHERE id = #{id}")
    int rename(@Param("id") String id, @Param("name") String name);

    @Delete("DELETE FROM category WHERE id = #{id}")
    int delete(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM category WHERE parent_id = #{id}")
    int countChildren(@Param("id") String id);

    /** 该分类下（不含子分类）未删除的材料数。 */
    @Select("SELECT COUNT(*) FROM material WHERE category_id = #{id} AND deleted_at IS NULL")
    int countMaterials(@Param("id") String id);

    /** 删除分类前把其中的材料挪到别处，避免出现"材料指向不存在的分类"。 */
    @Update("UPDATE material SET category_id = #{to} WHERE category_id = #{from}")
    int moveMaterials(@Param("from") String from, @Param("to") String to);

    /** 新建分类时排到同级末尾。 */
    @Select("SELECT IFNULL(MAX(sort), 0) + 1 FROM category "
            + "WHERE IFNULL(parent_id,'') = IFNULL(#{parentId},'')")
    int nextSort(@Param("parentId") String parentId);
}
