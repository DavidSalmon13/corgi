package app.plainly.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.IntegrationTest;
import app.plainly.policy.CoverKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/public/coverages: what we offer, for anyone, priced from CoverKind; nothing else opens up. */
// covers AC13, T8
@IntegrationTest
@AutoConfigureMockMvc
class PublicCoveragesEndpointTest {

    @Autowired MockMvc mvc;

    @Test
    void aGuestSeesEveryCoverWithItsPremiumFromCoverKind() throws Exception {
        mvc.perform(get("/api/public/coverages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(CoverKind.values().length))
                .andExpect(jsonPath("$[0].kind").value("BASE"))
                .andExpect(jsonPath("$[0].label").value(CoverKind.BASE.label()))
                .andExpect(jsonPath("$[0].role").value("BASE"))
                .andExpect(jsonPath("$[0].annualPremiumCents").value(CoverKind.BASE.annualPremiumCents()))
                .andExpect(jsonPath("$[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$[1].label").value(CoverKind.PRODUCT_LIABILITY.label()))
                .andExpect(jsonPath("$[1].role").value("ENDORSEMENT"))
                .andExpect(jsonPath("$[1].annualPremiumCents").value(CoverKind.PRODUCT_LIABILITY.annualPremiumCents()));
    }

    @Test
    void eachCoverCarriesOnlyItsFourCatalogFields() throws Exception {
        mvc.perform(get("/api/public/coverages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].length()").value(4))
                .andExpect(jsonPath("$[1].length()").value(4));
    }

    @Test
    void openingPublicDoesNotOpenTheRestOfTheApi() throws Exception {
        mvc.perform(get("/api/me/policies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/broker/policies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/publicity")).andExpect(status().isUnauthorized());
    }
}
