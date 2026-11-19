package com.kitepost.repository;

import com.kitepost.entity.MailSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSettingsRepository extends JpaRepository<MailSettingsEntity, String> {}
