package com.camwheeler.transactionanalyser.function;

import com.camwheeler.transactionanalyser.model.EnrichedFilterResult;
import com.camwheeler.transactionanalyser.model.FilterResult;
import com.camwheeler.transactionanalyser.util.TimestampUtils;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// Enriches each FilterResult with recent flagged transaction history for the same party.
// State is keyed by flagged party UID and cleaned up via event-time timers.
public class TransactionEnrichmentFunction
        extends KeyedProcessFunction<String, FilterResult, EnrichedFilterResult> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionEnrichmentFunction.class);

    static final long HISTORY_TTL_MILLIS = 30 * 60 * 1000L; // 30 minutes
    static final int MAX_HISTORY_SIZE = 20;

    private transient ListState<FilterResult> historyState;

    @Override
    public void open(Configuration parameters) {
        ListStateDescriptor<FilterResult> descriptor =
                new ListStateDescriptor<>("flagged-history", FilterResult.class);
        historyState = getRuntimeContext().getListState(descriptor);
    }

    @Override
    public void processElement(FilterResult current, Context ctx, Collector<EnrichedFilterResult> out)
            throws Exception {

        long currentTimestamp = TimestampUtils.toEpochMillis(
                current.getTransaction().getDate(),
                current.getTransaction().getTime()
        );

        // Read existing history
        List<FilterResult> history = new ArrayList<>();
        for (FilterResult fr : historyState.get()) {
            history.add(fr);
        }

        LOG.info("Enriching transaction {} with {} history entries for party {}",
                current.getTransaction().getTransactionId(), history.size(), ctx.getCurrentKey());

        // Emit enriched result — current transaction is NOT in its own history
        out.collect(new EnrichedFilterResult(current, new ArrayList<>(history)));

        // Add current to history, cap at MAX_HISTORY_SIZE (remove oldest first)
        history.add(current);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }

        // Write updated history back to state
        historyState.update(history);

        // Register cleanup timer
        ctx.timerService().registerEventTimeTimer(currentTimestamp + HISTORY_TTL_MILLIS);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<EnrichedFilterResult> out)
            throws Exception {

        List<FilterResult> history = new ArrayList<>();
        for (FilterResult fr : historyState.get()) {
            history.add(fr);
        }

        long cutoff = timestamp - HISTORY_TTL_MILLIS;
        List<FilterResult> filtered = new ArrayList<>();
        for (FilterResult fr : history) {
            long ts = TimestampUtils.toEpochMillis(fr.getTransaction().getDate(), fr.getTransaction().getTime());
            if (ts > cutoff) {
                filtered.add(fr);
            }
        }

        if (filtered.isEmpty()) {
            historyState.clear();
        } else {
            historyState.update(filtered);
        }
    }
}
