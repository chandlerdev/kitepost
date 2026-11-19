package com.kitepost.repository;

import com.kitepost.entity.CampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignRepository extends JpaRepository<CampaignEntity, String> {
    List<CampaignEntity> findAllByOrderByCreatedAtDesc();
    List<CampaignEntity> findByStatus(String status);
}
