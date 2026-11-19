package com.kitepost.entity;

import com.kitepost.entity.Models.*;
import com.kitepost.entity.Models.Query;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "campaigns")
public class CampaignEntity {
    @Id public String id;
    public String name;
    public String description;
    public String goal;
    @Convert(converter = JsonConverters.StringList.class) public List<String> countries = new ArrayList<>();
    @Convert(converter = JsonConverters.Regions.class) public Map<String, List<String>> regions = new LinkedHashMap<>();
    @Convert(converter = JsonConverters.StrategyJson.class) public Strategy strategy;
    public String pitch;
    @Convert(converter = JsonConverters.SenderJson.class) public Sender sender;
    public String tone;
    public String emailLanguage;     // local | english
    public int targetCustomers;
    public int maxSites;
    public String status;            // draft | running | paused | done
    @Convert(converter = JsonConverters.Queries.class) public List<Query> queries = new ArrayList<>();
    public int queryIndex;
    @Column(name = "current_search") @Convert(converter = JsonConverters.CurrentJson.class) public CurrentSearch current;
    public String error;
    public boolean sending = true;
    public String createdBy;
    public Instant createdAt;
    public Instant startedAt;
    @Version public Long version;
}
