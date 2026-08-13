package org.dromara.aivideo.timeline.service;

import java.util.List;

/** Read-only integrity scanner for the timeline persistence graph. */
public interface ITimelineConsistencyService {

    ConsistencyReport scan();

    record ConsistencyReport(List<ConsistencyFinding> findings) {
        public ConsistencyReport {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    record ConsistencyFinding(String code, String safeSummary) {
    }
}
