package app.plainly.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * In the single-image deploy (root Dockerfile) the built web app sits in static/. A browser that
 * opens or refreshes a client-side route such as /me/pay gets index.html, and React Router takes over.
 * Only the SPA's own route prefixes are listed, so /api/** and the static assets are never shadowed.
 */
@Controller
class SpaController {

    @GetMapping({"/signin", "/signup", "/broker", "/broker/**", "/me", "/me/**"})
    String index() {
        return "forward:/index.html";
    }
}
