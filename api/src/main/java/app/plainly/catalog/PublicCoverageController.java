package app.plainly.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** L1 "What we offer". Public: AuthFilter lets /api/public/** through with no session. */
@RestController
class PublicCoverageController {

    private final CoverageCatalogService catalog;

    PublicCoverageController(CoverageCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/public/coverages")
    List<Coverage> coverages() {
        return catalog.list();
    }
}
