package app.plainly.timeline;

import org.springframework.stereotype.Service;

/** Builds P2 "Cover timeline" from the policy's covers and the statement's money. Not built yet (T2). */
@Service
public class TimelineService {

    /** The timeline of a policy: its term and one band per live cover, base first. */
    public Timeline forPolicy(long policyId) {
        throw new UnsupportedOperationException("The timeline is not built yet (T2)");
    }
}
