package app.plainly.auth;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The demo role switcher's API (sign-in screen). Simulated; see {@link DemoAuthFilter}. */
@RestController
@RequestMapping("/api/demo")
class DemoSessionController {

    private final DemoUsers users;

    DemoSessionController(DemoUsers users) {
        this.users = users;
    }

    record SessionRequest(Long userId) {}

    @GetMapping("/users")
    List<DemoUser> users() {
        return users.all();
    }

    @GetMapping("/session")
    ResponseEntity<DemoUser> session(@RequestAttribute(name = DemoAuthFilter.CURRENT_USER, required = false) DemoUser user) {
        return user == null ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build() : ResponseEntity.ok(user);
    }

    @PostMapping("/session")
    ResponseEntity<DemoUser> signIn(@RequestBody SessionRequest body) {
        if (body == null || body.userId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return users.find(body.userId())
                .map(u -> ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(Long.toString(u.id()), -1)).body(u))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> signOut() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }

    private static String cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(DemoAuthFilter.COOKIE, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
