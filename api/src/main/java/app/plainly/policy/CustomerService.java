package app.plainly.policy;

import org.springframework.stereotype.Service;

/** The customer details other modules need. Billing uses it to find or record a customer's Stripe customer. */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /** The customer's Stripe details, the row held until the caller's transaction ends. */
    public CustomerRepository.StripeDetails lockStripeDetails(long customerId) {
        return customers.lockStripeDetails(customerId);
    }

    public void saveStripeCustomerId(long customerId, String stripeCustomerId) {
        customers.setStripeCustomerId(customerId, stripeCustomerId);
    }
}
