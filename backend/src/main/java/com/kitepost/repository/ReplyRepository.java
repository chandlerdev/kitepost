package com.kitepost.repository;

import com.kitepost.entity.ReplyEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReplyRepository extends JpaRepository<ReplyEntity, String> {
    List<ReplyEntity> findAllByOrderByReceivedAtDesc(Pageable page);
    boolean existsByMessageId(String messageId);
    long countByUnreadTrue();
}
