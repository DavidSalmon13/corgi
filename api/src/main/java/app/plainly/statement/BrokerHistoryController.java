package app.plainly.statement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** B5 "History": every action on a policy with its IDs and traces. Broker only (AuthFilter's /api/broker prefix). */
@RestController
class BrokerHistoryController {

    private final HistoryService history;

    BrokerHistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/broker/policies/{id}/history")
    History history(@PathVariable long id) {
        return history.forPolicy(id);
    }
}
