package app.plainly.policy;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B1 Policies and B3 Policy workspace. Broker only (DemoAuthFilter). */
@RestController
@RequestMapping("/api/broker/policies")
class BrokerPolicyController {

    private final PolicyQueries queries;

    BrokerPolicyController(PolicyQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    List<PolicyViews.ListRow> list() {
        return queries.list();
    }

    @GetMapping("/{id}")
    PolicyViews.Detail detail(@PathVariable long id) {
        return queries.detail(id);
    }
}
