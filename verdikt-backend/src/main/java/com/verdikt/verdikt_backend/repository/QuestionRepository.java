package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.model.Question;
import com.verdikt.verdikt_backend.model.enums.SpiceLevel;
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
    List<Question> findByIsCustomFalse();
    @Query(value = "SELECT * FROM questions WHERE is_custom = false ORDER BY RANDOM() LIMIT :count", nativeQuery = true)
List<Question> findRandomQuestions(@Param("count") int count);
}