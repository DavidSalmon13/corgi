package app.plainly.catalog;

import app.plainly.policy.CoverKind;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * What we offer, read straight from {@link CoverKind}, the same prices the broker charges, so the
 * landing page can never show a different premium. No repository: the catalog is the enum.
 */
@Service
public class CoverageCatalogService {

    /** Every cover, base policy first, in {@link CoverKind} order. */
    public List<Coverage> list() {
        return Arrays.stream(CoverKind.values())
                .map(kind -> new Coverage(kind, kind.label(),
                        kind == CoverKind.BASE ? Coverage.Role.BASE : Coverage.Role.ENDORSEMENT,
                        kind.annualPremiumCents()))
                .toList();
    }
}
