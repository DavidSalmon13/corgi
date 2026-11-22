package app.plainly.policy;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B1 Policies and B3 Policy workspace. Broker only (AuthFilter). */
@RestController
@RequestMapping("/api/broker/policies")
class BrokerPolicyController {

    private final PolicyService policies;

    BrokerPolicyController(PolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    List<PolicyViews.ListRow> list() {
        return policies.list();
    }

    @GetMapping("/{id}")
    PolicyViews.Detail detail(@PathVariable long id) {
        return policies.detail(id);
    }
}
