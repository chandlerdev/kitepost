package com.kitepost.repository;

import com.kitepost.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateRepository extends JpaRepository<TemplateEntity, String> {
    List<TemplateEntity> findByCampaignIdOrderByCreatedAtAsc(String campaignId);
    List<TemplateEntity> findAllByOrderByCreatedAtAsc();
}
