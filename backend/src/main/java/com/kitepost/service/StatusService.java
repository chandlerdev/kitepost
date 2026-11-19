package com.kitepost.service;

import com.kitepost.dto.Dtos.Mode;
import com.kitepost.dto.Dtos.Status;
import com.kitepost.config.AppProperties;
import com.kitepost.service.mail.ImapPoller;
import com.kitepost.service.pipeline.ClaudeClient;
import com.kitepost.service.pipeline.SearchService;
import com.kitepost.repository.LeadRepository;
import com.kitepost.repository.ReplyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** What is live and what is simulated. Never includes keys or passwords. */
@Service
public class StatusService {
    private final AppProperties props;
    private final SearchService search;
    private final ClaudeClient claude;
    private final ImapPoller imap;
    private final MetaService meta;
    private final LeadRepository leads;
    private final ReplyRepository replies;
    private final String jdbcUrl;

    public StatusService(AppProperties props, SearchService search, ClaudeClient claude, ImapPoller imap, MetaService meta,
                         LeadRepository leads, ReplyRepository replies, @Value("${spring.datasource.url}") String jdbcUrl) {
        this.props = props;
        this.search = search;
        this.claude = claude;
        this.imap = imap;
        this.meta = meta;
        this.leads = leads;
        this.replies = replies;
        this.jdbcUrl = jdbcUrl;
    }

    public Mode mode() {
        String db = jdbcUrl.startsWith("jdbc:postgresql") ? "PostgreSQL" : jdbcUrl.startsWith("jdbc:mysql") || jdbcUrl.startsWith("jdbc:mariadb") ? "MySQL" : "other";
        String replyMode = imap.enabled() ? "imap" : props.simulateReplies() && !props.smtpEnabled() ? "simulated" : "off";
        return new Mode(db, search.provider(), claude.enabled() ? "claude" : "rules", claude.enabled() ? claude.model() : null,
                props.smtpEnabled() ? "smtp" : "dry-run", replyMode, props.sending().dailyLimit(), props.sending().intervalSeconds());
    }

    public Status status() {
        return new Status(mode(), meta.sentToday(), leads.countByStatus("review"), replies.countByUnreadTrue(), leads.countByOpenSeenFalseAndOpenedAtIsNotNull());
    }
}
