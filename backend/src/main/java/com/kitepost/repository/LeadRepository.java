package com.kitepost.repository;

import com.kitepost.entity.LeadEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<LeadEntity, String>, JpaSpecificationExecutor<LeadEntity> {
    boolean existsByCampaignIdAndDomain(String campaignId, String domain);
    List<LeadEntity> findByCampaignIdAndStatus(String campaignId, String status);
    long countByStatus(String status);
    long countByOpenSeenFalseAndOpenedAtIsNotNull();
    List<LeadEntity> findTop200ByOpenedAtIsNotNullOrderByOpenedAtDesc();

    /** Records that the tracking image loaded. Order matters on MySQL: open_seen is set before open_count changes. */
    @Modifying
    @Transactional
    @Query("update LeadEntity l set l.openSeen = case when l.openCount = 0 then false else l.openSeen end, l.openedAt = coalesce(l.openedAt, :now), l.openCount = l.openCount + 1 where l.id = :id")
    int recordOpen(@Param("id") String id, @Param("now") java.time.Instant now);

    @Modifying
    @Transactional
    @Query("update LeadEntity l set l.openSeen = true where l.openSeen = false")
    int markOpensSeen();

    /** Approved emails, oldest approval first. */
    List<LeadEntity> findByStatusOrderByApprovedAtAsc(String status, Pageable page);

    /**
     * Claims an approved lead for sending. Only one worker can win, even with several app instances:
     * the update only matches while the status is still "approved".
     */
    @Modifying
    @Transactional
    @Query("update LeadEntity l set l.status = 'sending', l.version = l.version + 1 where l.id = :id and l.status = 'approved'")
    int claimForSending(@Param("id") String id);

    Optional<LeadEntity> findFirstBySentMessageIdIn(Collection<String> messageIds);
    Optional<LeadEntity> findFirstByEmailIgnoreCaseAndSentMessageIdIsNotNull(String email);
    Optional<LeadEntity> findFirstByDomainInAndSentMessageIdIsNotNullOrderByApprovedAtDesc(Collection<String> domains);

    /** [campaignId, status, category, count] rows for the dashboard numbers. */
    @Query("select l.campaignId, l.status, l.category, count(l) from LeadEntity l group by l.campaignId, l.status, l.category")
    List<Object[]> countAllGrouped();

    @Query("select l.campaignId, l.status, l.category, count(l) from LeadEntity l where l.campaignId = :campaignId group by l.campaignId, l.status, l.category")
    List<Object[]> countGrouped(@Param("campaignId") String campaignId);
}
