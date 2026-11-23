package app.plainly.me;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** P1 "Your cover". Policyholder only (AuthFilter). */
@RestController
class MyCoverController {

    private final MyCoverService myCover;

    MyCoverController(MyCoverService myCover) {
        this.myCover = myCover;
    }

    @GetMapping("/api/me/policies/{policyId}/cover")
    MyCoverService.MyCover cover(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me,
            @PathVariable long policyId) {
        return myCover.cover(me, policyId);
    }
}
