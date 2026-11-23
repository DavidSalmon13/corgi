package app.plainly.policy;

import app.plainly.common.NotFoundException;
import org.springframework.stereotype.Service;

/** The customer details other modules need. Billing uses it to find or record a customer's Stripe customer. */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /** The customer as B2 shows it read-only; NotFoundException when there is no such customer. */
    public CustomerRepository.Row find(long customerId) {
        return customers.find(customerId).orElseThrow(() -> new NotFoundException("No customer " + customerId + "."));
    }

    /** The customer's Stripe details, the row held until the caller's transaction ends. */
    public CustomerRepository.StripeDetails lockStripeDetails(long customerId) {
        return customers.lockStripeDetails(customerId);
    }

    public void saveStripeCustomerId(long customerId, String stripeCustomerId) {
        customers.setStripeCustomerId(customerId, stripeCustomerId);
    }
}
