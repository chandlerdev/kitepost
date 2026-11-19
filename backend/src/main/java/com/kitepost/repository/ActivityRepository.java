package com.kitepost.repository;

import com.kitepost.entity.ActivityEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityRepository extends JpaRepository<ActivityEntity, String> {
    List<ActivityEntity> findByCampaignIdOrderByAtDescIdDesc(String campaignId, Pageable page);
}
