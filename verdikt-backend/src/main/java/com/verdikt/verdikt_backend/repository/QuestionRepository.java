package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.config.CacheConstants;
import com.verdikt.verdikt_backend.model.Question;
import com.verdikt.verdikt_backend.model.enums.SpiceLevel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findByCategory(String category);
    List<Question> findBySpiceLevel(SpiceLevel spiceLevel);
    List<Question> findByCategoryAndSpiceLevel(String category, SpiceLevel spiceLevel);

    @Cacheable(value = CacheConstants.NON_CUSTOM_QUESTIONS, key = "'all'")
    List<Question> findByIsCustomFalse();

    @Query(value = """
        SELECT * FROM questions
        WHERE is_custom = false
        ORDER BY id
        LIMIT :count
        OFFSET floor(random() * (
            SELECT COUNT(*) FROM questions WHERE is_custom = false
        ))::int
        """, nativeQuery = true)
    List<Question> findRandomQuestions(@Param("count") int count);
}
