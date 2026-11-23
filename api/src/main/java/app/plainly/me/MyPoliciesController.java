package app.plainly.me;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** "Your policies": every policy the signed-in owner holds and the total due. Policyholder only (AuthFilter). */
@RestController
class MyPoliciesController {

    private final MyPoliciesService myPolicies;

    MyPoliciesController(MyPoliciesService myPolicies) {
        this.myPolicies = myPolicies;
    }

    @GetMapping("/api/me/policies")
    MyPoliciesService.MyPolicies list(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me) {
        return myPolicies.list(me);
    }
}
