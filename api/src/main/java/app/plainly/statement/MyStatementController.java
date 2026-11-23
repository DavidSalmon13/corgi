package app.plainly.statement;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** P3 "Your statement" and its P4 traces. Policyholder only (AuthFilter); always the caller's own policy. */
@RestController
class MyStatementController {

    private final StatementService statements;

    MyStatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping("/api/me/policies/{policyId}/statement")
    Statement statement(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me, @PathVariable long policyId) {
        return statements.forPolicy(me, policyId);
    }
}
