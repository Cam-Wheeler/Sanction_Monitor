package com.camwheeler.transactionanalyser.model;

import java.util.ArrayList;
import java.util.List;

// Internal POJO wrapping a FilterResult with recent history for the same flagged party.
// Must be a POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class EnrichedFilterResult {

    private FilterResult current;
    private List<FilterResult> recentHistory;

    public EnrichedFilterResult() {
        this.recentHistory = new ArrayList<>();
    }

    public EnrichedFilterResult(FilterResult current, List<FilterResult> recentHistory) {
        this.current = current;
        this.recentHistory = recentHistory;
    }

    public FilterResult getCurrent() {
        return current;
    }

    public void setCurrent(FilterResult current) {
        this.current = current;
    }

    public List<FilterResult> getRecentHistory() {
        return recentHistory;
    }

    public void setRecentHistory(List<FilterResult> recentHistory) {
        this.recentHistory = recentHistory;
    }
}
