package com.kitepost.service.job;

import com.kitepost.config.AppProperties;
import com.kitepost.service.mail.ImapPoller;
import com.kitepost.service.mail.OutboundMail;
import com.kitepost.service.CampaignRunner;
import com.kitepost.service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Background jobs. Run them in one instance only; set RUN_WORKERS=false on extra API instances.
 * (Sending is still safe with several: each email is claimed atomically before it is sent.)
 */
@Component
public class Workers {
    private static final Logger log = LoggerFactory.getLogger(Workers.class);
    private final AppProperties props;
    private final CampaignRunner runner;
    private final OutboundMail mail;
    private final ImapPoller imap;
    private final StatusService status;

    public Workers(AppProperties props, CampaignRunner runner, OutboundMail mail, ImapPoller imap, StatusService status) {
        this.props = props;
        this.runner = runner;
        this.mail = mail;
        this.imap = imap;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        var m = status.mode();
        log.info("Kitepost ready · database: {} · search: {} · AI: {} · email: {} · replies: {} · workers: {}",
                m.database(), m.search(), m.ai(), m.email(), m.replies(), props.runWorkers() ? "on" : "off");
        if (props.runWorkers()) runner.resumeAll();
    }

    @Scheduled(fixedDelayString = "${kitepost.sending.interval-seconds:30}", initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void send() {
        if (!props.runWorkers()) return;
        try { mail.sendTick(); } catch (Exception e) { log.warn("Send job failed: {}", e.getMessage()); }
    }

    @Scheduled(fixedDelayString = "${kitepost.imap.poll-seconds:120}", initialDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void replies() {
        if (props.runWorkers() && imap.enabled()) imap.poll();
    }
}
