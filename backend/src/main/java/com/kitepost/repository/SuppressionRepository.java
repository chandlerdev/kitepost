package com.kitepost.repository;

import com.kitepost.entity.SuppressionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface SuppressionRepository extends JpaRepository<SuppressionEntity, String> {
    boolean existsByValueIn(Collection<String> values);
    List<SuppressionEntity> findAllByOrderByAtDesc();
}
