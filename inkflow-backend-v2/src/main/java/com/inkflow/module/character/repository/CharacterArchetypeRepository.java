package com.inkflow.module.character.repository;

import com.inkflow.module.character.entity.CharacterArchetype;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 角色原型仓储接口
 */
@Repository
public interface CharacterArchetypeRepository extends JpaRepository<CharacterArchetype, UUID> {

    /**
     * 根据英文名称查询
     */
    Optional<CharacterArchetype> findByName(String name);

    /**
     * 根据中文名称查询
     */
    Optional<CharacterArchetype> findByNameCn(String nameCn);

    /**
     * 检查原型是否存在
     */
    boolean existsByName(String name);
}
