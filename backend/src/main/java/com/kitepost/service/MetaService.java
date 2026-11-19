package com.kitepost.service;

import com.kitepost.entity.MetaEntity;
import com.kitepost.repository.MetaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/** Small persistent counters and positions. */
@Service
public class MetaService {
    private final MetaRepository repo;

    public MetaService(MetaRepository repo) { this.repo = repo; }

    public Optional<String> get(String k) { return repo.findById(k).map(m -> m.v); }

    public void set(String k, String v) { repo.save(new MetaEntity(k, v)); }

    private static String today() { return "sent:" + LocalDate.now(ZoneOffset.UTC); }

    public long sentToday() { return get(today()).map(Long::parseLong).orElse(0L); }

    public synchronized void addSentToday() { set(today(), String.valueOf(sentToday() + 1)); }
}
