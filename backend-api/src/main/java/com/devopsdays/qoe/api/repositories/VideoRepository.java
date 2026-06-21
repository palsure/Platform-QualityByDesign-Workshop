package com.devopsdays.qoe.api.repositories;

import com.devopsdays.qoe.api.models.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByVideoId(String videoId);

    List<Video> findByActiveTrue();

    List<Video> findByCategoryIgnoreCaseAndActiveTrue(String category);

    @Query("""
            SELECT v FROM Video v
            WHERE v.active = true
              AND (LOWER(v.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(v.description) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(v.genre) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY v.title
            """)
    List<Video> search(@Param("q") String query);

    boolean existsByVideoId(String videoId);
}
