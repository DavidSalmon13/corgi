package app.plainly.timeline;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** P2 "Cover timeline". Policyholder only (AuthFilter); always the caller's own policy. */
@RestController
class MyTimelineController {

    private final TimelineService timelines;

    MyTimelineController(TimelineService timelines) {
        this.timelines = timelines;
    }

    @GetMapping("/api/me/timeline")
    Timeline timeline(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me) {
        return timelines.forOwner(me);
    }
}
