# Decision log

Short, dated entries: what was decided, why, and any assumption made where the brief was silent.

## 2026-11-22: Chose Track 1 (Policy administration)

Of the three tracks, policy administration has the fewest external moving parts (Stripe test mode covers payments and refunds), its amounts are deterministic (dates × rates), and it is Corgi's own domain. Neobank was a close second; its main risk was card-issuing sandbox access. The investment app was ruled out because of its valuation and return-restatement complexity.

## 2026-11-22: Frontend is a first-class deliverable

The recruiter said the reviewers care strongly about a clean, easy-to-understand interface that shows creativity, especially in insurance. UI work gets real time in the plan, not just whatever is left at the end.

## 2026-11-22: Initial assumptions (confirmed 2026-11-22, see "Pricing rules confirmed" below)

- **Product:** one product, General Liability. Fictional pricing: base $1,200/year; one add-on at $730/year ($2.00/day).
- **Pro-rata:** annual premium × days covered ÷ days in term. Dates are end-exclusive, and each ledger line is rounded half-up to the cent once.
- **Cancellation:** pro-rata refund of the unearned premium with no penalty. Short-rate is a documented omission. Unpaid balances are netted off the refund.
- **Corrections:** reverse the original charge and rebill with the corrected effective date. The original stays visible.
- **Integrations:** Stripe (test mode) for payments, refunds and webhooks; Resend (sandbox) for policy and statement emails.
- **Stack:** React + TypeScript web, Spring Boot 3 / Java 21, PostgreSQL, deployed on Railway.

## 2026-11-22: Backend on Spring Boot, frontend on Corgi's stack

The brief doesn't require a stack. Corgi mainly uses Express and Next.js. I chose Spring Boot 3 / Java 21 for the backend anyway because it's my strongest stack. The heaviest-scored risk sits in the money logic: append-only ledger, reverse-and-rebill corrections and idempotent webhooks. Transactions, Flyway migrations and unique constraints are dependable tools for that, and a time-boxed trial is a poor place to learn a new stack. The frontend, which is graded heavily, uses React + TypeScript + Tailwind to match Corgi. With more time I'd port the API to TypeScript to share money and DTO types end to end.

## 2026-11-22: Policy dates use one timezone, America/New_York

The brief doesn't say which timezone a policy date belongs to, but it decides the day coverage starts and ends, so it changes every pro-rata amount. I assume every policy runs on America/New_York calendar days and store dates as plain dates (no time), end-exclusive. A real carrier would use the insured's location; per-policy timezones are a documented omission.

## 2026-11-22: Demo sign-in is simulated

Real accounts aren't part of the workflow being graded, so sign-in is a role switcher with seeded, fictional Broker and Policyholder users. This is not real security, and the README lists it under "simulated". Real authentication is in Phase 2.

_Superseded the same day by "Real sign-in: brokers are given accounts, owners sign up" below._

## 2026-11-22: Only Stripe webhooks change payment status

The app never marks a payment as succeeded or failed because the browser said so. The status starts as pending and changes only when a signed Stripe webhook arrives, and each event id is processed once. As a result, the pending, successful and failed states the brief asks for always match Stripe, and each one traces to a Stripe event.

## 2026-11-22: Policyholder persona is a small-business owner

The brief says "customer" and "policyholder" but doesn't say who they are. Because this is commercial insurance, the insured party is a business, not a person. The customer record is the business (legal name, address), and its owner is the contact person who signs in, pays and gets emails. All policyholder-facing copy is written for an owner with no insurance background. Multiple locations, users or policies per business are left out.

## 2026-11-22: Pricing rules confirmed, and which are simplified

I kept the initial assumptions. The rules that follow real practice: charging by the day for mid-term changes; counting days the way a 12:01 a.m. start-to-12:01 a.m. end policy does (Sep 1 → Sep 1 = 365 days); taking any unpaid amount off a refund; and correcting by undoing the original line and recharging. The simplifications: prices are made up and flat (a real price depends on business type, revenue and location; $730 for the extra cover was picked because it is exactly $2.00 a day, so a reviewer can check it by hand); and every cancellation refunds unused days in full with no fee. In real commercial insurance, a cancellation the customer asks for often keeps a penalty (short-rate) or a minimum premium. Rounding each line once is a billing convention I chose so that lines always add up to the total shown.

## 2026-11-22: A correction that lowers the price is refunded automatically

When a correction reduces what the customer owes (for example, the extra cover's start moves later), the difference first pays off any open balance, and the rest is refunded to the customer's card through Stripe straight away. Refunds start with the most recent payment, and no single refund exceeds what that payment collected. The broker's confirm screen previews the refund, so confirming the correction counts as approving it; there's no separate approval step. I chose this over keeping a credit balance because it matches how insurers usually return money they owe, the customer isn't left with money sitting idle, and the refund traces to a Stripe event like every other amount. Refunds that fail are shown to the broker to retry.

## 2026-11-22: The app is called Plainly

The app has its own name and look so it doesn't copy Corgi's real branding. "Plainly" says what the product promises: insurance explained in plain language. A warm amber accent is the only nod to a corgi. The internal product slug stays `policy-admin`.

## 2026-11-22: The database blocks edits to ledger rows

Append-only is enforced by a Postgres trigger that rejects any UPDATE or DELETE on ledger entries, not by code convention alone. I picked a trigger over restricted grants (a separate app role allowed only INSERT and SELECT) because it works even when the app connects as the schema owner, which is the simple setup on Railway. Corrections therefore have to post a reversal and a rebill. They can't edit the original line, even by mistake.

## 2026-11-22: Spring Boot 4 instead of Boot 3

The plan said Spring Boot 3, but Spring Initializr no longer offers it, and Boot 3's open-source support has ended. The API uses Spring Boot 4.2 on Java 21 with Gradle 9.9. Nothing in the design changes: Flyway, JdbcClient and Testcontainers work the same way. The visible differences are the renamed starters (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`) and Jackson 3.

## 2026-11-22: A policy must start within a year of today

The brief doesn't limit start dates. A start date more than a year in the past or the future is almost always a typo (2062 for 2026), and it would issue a policy that has already ended or that bills a year early. So the form asks for a start within a year of today, either way. Backdating within that window is allowed, because brokers often record cover that began a few weeks earlier.

## 2026-11-22: Web unit tests with Vitest; ledger and jargon work on phones

The web package had no test runner, and money formatting and timeline geometry are logic that must be tested first. I added Vitest, which reads the existing `vite.config.ts`, so no new config file was needed. Amounts are formatted from integer cents without floating point, and credits use a true minus sign (U+2212). The ledger is a table on wide screens and a stacked list below 768 px, so it never scrolls sideways at 390 px. `Term` keeps the design's dotted-underline `abbr` with a hover title, and also opens a small bubble on tap or keyboard focus, because a title attribute alone does nothing on a phone.

## 2026-11-22: P1 broker contact and "how we got this" for now

The design links "Your broker, Sam Reyes" to an email address, but the API stores no broker email and this spec doesn't add one. So P1 shows the broker's name as plain text, not a link, rather than a made-up address. The amount due on P1 doesn't open a breakdown yet: the "How we got this" drawer (P4) and the statement (P3) belong to later specs. Until they ship, the broker's B3 ledger shows the calculation behind every amount.

## 2026-11-22: Real sign-in: brokers are given accounts, owners sign up

Sign-in now uses an email and a password, the way a broker-led insurance portal works. Brokers never sign up; their accounts are created for them (Sam Reyes is seeded). An owner can't buy a policy here: their broker issues it and enters the owner's email, and the owner then creates their account with that email and a password of their own. Passwords are stored only as BCrypt hashes. The session cookie holds a random token, and the database keeps only its SHA-256 hash, so a cookie can't be edited to become someone else and a copy of the table can't be used to sign in. Sessions last 7 days and end on the server at sign-out. I used Spring Security's crypto module only, not the whole framework, because one filter already enforces the two roles and the full framework would add configuration without changing what's protected. Self-serve buying, SSO and password reset are Phase 2.

## 2026-11-22: Sign-up has no email confirmation

The brief needs no email, and the trial has no mail integration yet, so sign-up doesn't send a confirmation link. The risk is that someone who knows an owner's email, before the owner signs up, could create that account and see the owner's cover. I accept that for a demo with fictional data. In production the account would be created only after the owner clicks a link sent to that address. Sign-up also says whether a policy exists for an email; a confirmation link would remove that hint too.

## 2026-11-22: One email belongs to one customer

Sign-up finds the policy by the email the broker entered, so two customers can't share an owner email: the broker's form now refuses a second one ("Another customer already uses this email."). This matches the existing rule of one policy per customer. The V5 migration makes any earlier duplicates in demo data unique by adding "+<customer id>" before the @, so it runs on any existing database.

## 2026-11-22: CSRF and brute force, for the trial

The session cookie is SameSite=Lax, and every endpoint that changes something takes a JSON body, which a form on another site can't send with the cookie. That is the CSRF protection; there are no CSRF tokens. There's no lockout or rate limit on sign-in. BCrypt makes each guess slow, but that also makes sign-in an easy way to load the CPU, so production would need rate limiting by account and by IP before launch.

## 2026-11-22: The demo broker login exists on every deploy

The brief asks for demo credentials for both roles on the deployed app, so the V5 migration gives the broker Sam Reyes the public password `plainly-demo-2026` on every database, and the seed that signs Maya up is on by default (`PLAINLY_DEMO_SEED`). Anyone with the README can therefore sign in as a broker, read every policy and issue new ones, on fictional data. That is the point of this deploy, but it means this build must never hold real customers: a real launch would create brokers through an admin flow, drop Sam's known password, and turn the seed off. The security review (T17) flagged this; I kept it deliberately.


## 2026-11-22: Stripe webhook spike: signed events reach the API

Before building payments, I proved the riskiest piece end to end (collect-premium T1). The API takes the webhook body as a raw String and checks the `Stripe-Signature` header with `stripe-java` 34.2.0 (`Webhook.constructEvent`) before anything is parsed. A test payment for $14.00 with Stripe's always-succeeds test card, and one with the always-declines card, sent `charge.succeeded`, `payment_intent.succeeded`, `charge.failed` and `payment_intent.payment_failed` through `stripe listen` to `localhost:8080/api/stripe/webhook`. All four were verified and answered 200. A request with no signature, or a made-up one, gets 400. Three things the build must follow: the library and the account both use Stripe API version `2026-11-18.endive`, so event objects parse with no version mismatch; that version no longer accepts `payment_method_types`, so PaymentIntents use `automatic_payment_methods` (card only, no redirects); and Stripe CLI 1.55 needs the events named, so the README's `stripe listen` command lists them with `--events`. The webhook path is the only `/api` path that needs no session, because Stripe can't sign in; the signature is its proof instead.

## 2026-11-22: A declined card is a $0.00 line on the ledger

The brief asks to show whether a payment is pending, successful or failed, and the designs show a declined attempt on the ledger ("Payment attempt, card ending 0002 · No money moved"). So a `charge.failed` webhook posts a $0.00 `PAYMENT` line linked to the payment and the Stripe event. It changes no balance, but the attempt stays in the history next to the payment that worked, and it traces to Stripe like every other line. A payment that is still with the bank isn't on the ledger: no money has moved yet, so it shows as "Waiting for the bank" next to the balance instead.

## 2026-11-22: A payment's status is the one value that changes, and only a webhook changes it

The ledger never changes, but a payment has to move from waiting to paid or declined. That status lives on the `payments` row (one per Stripe PaymentIntent) and is the only mutable value in the money path. Only the webhook handler updates it, after the signature checks out and the event id is recorded for the first time, in the same transaction. A late or out-of-order event can't undo a payment: a failure after a success is ignored, and a success after a failure is recorded, because the money did move. What the money did is always a ledger line, never this status.

## 2026-11-22: Card details come from the charge events

Stripe sends both `payment_intent.*` and `charge.*` events for a card payment. The handler settles payments from `charge.succeeded` and `charge.failed`, because the charge carries the card's last four digits, the brand and the bank's reason for a decline, all of which the screens show. `payment_intent.processing` is used only to show "Waiting for your bank". The card that paid last is remembered from the payment row (its Stripe payment method id), and Stripe keeps the card on the business's Stripe customer, so "Used last time" needs no extra call to Stripe.

## 2026-11-22: The API hands out the publishable key

The pay page needs Stripe's publishable key to show the card form. Rather than build it into the web app, the API returns it with each new payment. One `.env` then configures everything, a deployment can change keys without rebuilding the web app, and with no keys the API simply says payments are off. The key is meant to be public, so nothing secret reaches the browser.

## 2026-11-22: Opening the pay page reuses the open payment

Each visit to the pay page asks the API to start a payment. If the owner already has an unsent payment for the same amount, they get that one back instead of a new PaymentIntent, so refreshing the page doesn't pile up intents in Stripe. If the amount due changed, a new intent is created and the old one is left as it is: it was never sent to a bank, so nothing can be charged on it, and it isn't shown anywhere. While a payment is with the bank, starting another is refused ("Your last payment is still on its way."), so an owner can't pay twice by accident. After a decline, "Try again" opens a fresh payment.

## 2026-11-22: The live payment check was run against the API, not by eye

To finish collect-premium (T18), both test suites passed: 126 API tests and 89 web tests. A live pass then ran through `stripe listen` in Stripe test mode, signed in as Maya, with the card confirmed by the Stripe CLI instead of typed into the card form. Card 0002 was declined: the `charge.failed` webhook got a 200, the payment showed as failed, the ledger got a $0.00 attempt line (LE-0006) and $1,200.00 was still due. Card 4242 was paid: the `charge.succeeded` webhook got a 200, the ledger got a −$1,200.00 line (LE-0007) carrying its Stripe event id, nothing was due, the cover page reported the payment as paid with card 4242, and the broker's policy view showed the same lines and a $0 balance. What the P5, P1 and B3 screens show for these states is covered by the web tests. Nobody looked at them in a browser, and the user chose to check them later.

## 2026-11-22: An old pay page can't charge twice, and payments need all three Stripe values

The code and security reviews of the collect-premium PRs found ways money could move without the app keeping up. This hotfix closes them; only webhooks still change a payment's status.

- Before opening a new payment, the API asks Stripe about every earlier intent nobody finished paying, whether open or declined. If the bank already has one (processing or succeeded) and its webhook simply hasn't arrived yet, the new payment is refused as "still on its way". Card payments usually skip `payment_intent.processing`, so the old PROCESSING-only check almost never caught this.
- The others are cancelled at Stripe, and `payments.stripe_canceled_at` records that we did it, so a pay page left open in another tab can't charge the customer a second time. That is a fact about our request, not a status.
- The saved card can't pay a replaced payment, or one whose amount no longer matches what's due.
- Payments switch on only when the secret key, the publishable key and the webhook signing secret are all set, and only with test-mode keys. Without the webhook secret every payment would be charged and never recorded, and this demo must never take real money.
- After a confirm fails, the app no longer says "Nothing was charged", because it can't know that.
- `payment_intent.payment_failed` and `payment_intent.canceled` release a payment stuck at PROCESSING.
- A signed event the API can't read gets 500 and isn't recorded, so Stripe's retry gets a fresh try instead of being ignored as a repeat.
## 2026-11-22: Add coverage: where the "can't add" errors go, and test-first skeletons

A 422 from adding cover puts each message under the field it belongs to: date problems under `startDate`, and both "Pick a cover to add." and "This policy already has product liability cover." under `kind`, because the Cover select is where the broker would fix them. A cancelled policy has no field to fix, so its message goes under `policy`, and B4 shows it with a link back instead of the form. Dates in messages read like the design ("Sep 1, 2027"), and the ledger line uses the short form ("Extra cover from Oct 15"). The failing-first service tests call `AddCoverageService` directly, so T1 adds the two plain records and a service whose methods throw "not built yet". That way the API suite still compiles and every new test fails for the right reason until T4 and T5 fill it in.

## 2026-11-22: Extra cover may start any day in the cover period, including the past

A broker can start the extra cover on any day from the policy start up to the day before it ends, and that includes days already gone. Brokers often record cover the customer asked for days or weeks ago, and a policy may be issued with a back-dated start, so refusing past dates would just push them to pick a wrong date. The charge covers the days from the chosen start to the policy end, so an earlier start costs more, and B4 shows that before anything is saved. The start date defaults to today when today is in the cover period, otherwise to the policy start. A real insurer usually asks for a statement that there have been no claims since a back-dated start; that check is out of scope here.

## 2026-11-22: One extra cover per policy, enforced under a row lock rather than a unique index

There's only one add-on product, and a policy can have it once. `AddCoverageService.add` locks the policy row (`select … for update`) and checks again for an existing product liability cover before inserting. Two brokers saving at the same moment therefore give one coverage and one charge, and the second gets "This policy already has product liability cover." I didn't add a unique index on (policy, kind) because correct-effective-date will close a coverage row and open a new one of the same kind when a start date is fixed. Collect-premium's payment code locks the same row, so adding cover during a checkout just leads to a new payment for the new balance.

## 2026-11-22: The daily price appears only when it's exact

B4's option label and the "Customer will see" sentence give a daily price ("at $2.00 a day") only when the yearly price divides into whole cents by the days in the policy year. $730.00 over 365 days is exactly $2.00, but over a 366-day leap-year term it's $1.9945…, and showing "$1.99 a day" next to a total of 290 × $1.9945 would look like an arithmetic slip. In that case the sentence keeps the days and the total and leaves the per-day clause out, while the calculation line still shows the full "yearly price × days ÷ days in the policy year".

## 2026-11-22: The "Extra cover added" banner is shown once

After saving, B4 passes the banner text to B3 in router state, rather than as a query parameter or in storage. B3 reads it once and then replaces the history entry without it, so reloading the page or coming back to it doesn't announce the change a second time. The broker can also hide it. The banner's amount comes from the save response and B3 refetches the policy, so the two match unless another change lands in between.

## 2026-11-22: Backend layered controller → service → repository, plain SQL in repositories, no JPA

The first specs put SQL straight into services and one controller, which mixes business rules with data access and doesn't follow the standard Spring layering a reviewer expects. From now on every API module is layered: controllers map HTTP to one service call, services hold the rules and own `@Transactional`, and `@Repository` classes are the only code that runs SQL (`playbook/standards/backend/layers.md`). Repositories use `JdbcClient` with plain SQL rather than JPA/Spring Data on purpose: an ORM saves changes to loaded objects automatically, which could quietly rewrite an append-only ledger row, while plain SQL keeps every write a visible `insert`. The existing code was refactored to this shape with no change in behaviour, before correct-effective-date and cancel-refund are built.

## 2026-11-22: Correcting a start date: what the preview needs, and test-first skeletons

B4's live preview prices a correction from the new start date alone. The reason is checked only when the broker saves, so the money shows while they are still typing it. On save, every field problem is reported at once (start date and reason), but a policy that can't be corrected (cancelled, no extra cover, or a payment still with the bank) gets one message under `policy`, as with adding cover. Like add-coverage, T1 adds the two plain records (`CorrectCoverageCommand`, `CorrectionPlan`) and a `CorrectCoverageService` whose methods throw "not built yet". That way the API suite compiles and each new test fails for the right reason. The tests pay through the real webhook path (signed `charge.succeeded`), so refund allocation is tested against real `payments` rows.

## 2026-11-22: Refund and correction contracts fixed by the T2 tests

A refund event is matched to our row by the `refund_id` we put in the refund's Stripe metadata, falling back to `stripe_refund_id`, because the webhook can arrive before the after-commit send has stored Stripe's id. Retry is `POST /api/broker/refunds/{id}/retry` with our numeric refund id; it answers 200 with the refund (`displayId`, `status`, `attempt`), and retrying a refund that isn't FAILED is a 422 under `refund` rather than a silent second send. P1 gets its own due reason, `CORRECTION`, plus `latestChangeReason` (the broker's words, as AC13 and the spec's "the customer sees the reason word for word" ask), and `refundWaiting {amountCents, cardLast4}` while a refund is pending. The refund event builders that T6 lists were added to `StripeTestSupport` now, with a test-only refund log on `FakeStripeGateway`, so these tests compile; T6 still adds `createRefund` to the gateway itself.

## 2026-11-22: Correction copy names the customer instead of guessing a pronoun

The B4 design and AC7 say "we've told her why" and "She's asked to pay it". A first name doesn't tell us how a customer refers to themselves, so the copy uses the name or a neutral phrase: "Saved. Maya now owes $14.00 and can see why." and "Maya is asked to pay it; no card is charged automatically." The T3 tests also pin the cases the designs don't draw: a credit that only lowers an unpaid balance ("You owe $14.00 less because we fixed when your extra cover started."), a credit that pays part of the balance and refunds the rest, and a refund split across two cards. As in T1, T3 adds the contract types to `types.ts` and a `correctCopy.ts` whose functions throw "not built yet", so the web build type-checks and each test fails for the right reason; T11 writes the words.

## 2026-11-22: A refund already promised isn't planned again, and the send ships with the save

A refund only becomes a REFUND line when Stripe's webhook says it went, so until then the ledger still shows the customer in credit. A second correction made in that window would plan the same money again. The plan therefore subtracts refunds that are recorded but not yet in the ledger (PENDING, and FAILED ones waiting for a retry) before splitting what's owed across payments. `RefundService.send` was built in T8 rather than T9 because `correct` calls it after commit. It never throws: a refusal from Stripe, or Stripe being unreachable, marks the refund FAILED with the reason, and the saved correction stands. `PaymentService.cancelOpenIntents` reuses the stale-intent cancel from `start`; if Stripe says the bank already has one of those payments, the correction is refused with the "payment is waiting for the bank" message.

## 2026-11-22: Each refund send has its own transaction; late events for a replaced attempt are ignored

`RefundService.send` runs in a new transaction (a `TransactionTemplate` with REQUIRES_NEW). It is called from the correction's after-commit hook, where the finished transaction's connection is still bound, so without its own transaction the "sent" or "failed" update would only be saved by accident. The refund row stays locked while Stripe is asked, so a webhook for that refund waits until Stripe's id is stored. A retry clears the old Stripe refund id and stores the new one; an event naming a different Stripe refund than the row now holds is about an attempt the retry replaced, and changes nothing.

## 2026-11-22: P1 shows every unsettled refund, summed, named by the card that gets the most

`refundWaiting` on P1 sums every refund on the policy that hasn't settled: PENDING ones, and FAILED ones the broker will retry. The B3 design says the customer "still sees 'We owe you $14.00' until it goes through", and hiding a failed refund would tell them "You're all paid up" while we still owe them money. When a refund is split across two cards, the sentence names the card that gets the larger share rather than listing both, and B3 still lists each piece. A refund row is exposed as a public `RefundListing` record through `RefundService`, so `policy` and `me` never import a billing repository type.

## 2026-11-22: B4's Correct tab opens from a link, says "undone in full", and asks once in a real dialog

B4 picks its tab from `?tab=correct`, so B3's Correct action can link straight to it and Back returns to the same tab. The tabs are Add cover · Correct; the design's Cancel tab waits for its own spec rather than showing a dead button. Each tab has its own blocked card: Add cover is blocked when extra cover already exists, and Correct is blocked when there is none, when the policy is cancelled, or while a payment is with the bank. The design's "reversal" and "rebill" labels became "undone in full" and "charged by the day", each with a tap-to-explain note, so even broker copy needs no insurance vocabulary. "Customer will see" also quotes the broker's reason, since the customer reads it word for word. The confirm step is a native `<dialog>` (focus stays inside, Esc closes) and opens only when there is a price and a usable reason. Otherwise the save goes straight to the API, so the 422 messages appear under each field exactly as the server words them.

## 2026-11-22: Correcting a start date: four rules that keep the money traceable

- **The cover row is replaced, not edited.** A correction inserts a new `coverages` row from the corrected date and points the old row at it (`replaced_by_coverage_id`, unique). Reads skip replaced rows, so timelines show one extra cover, and the live row carries the start it replaced ("from Oct 8 · was Oct 15"). Editing the date in place would lose what the original charge was priced from, and the struck-through ledger line would no longer match any cover.
- **A refund is sent after the correction commits, once per attempt.** The refund is recorded PENDING in the same transaction as the correction, then an after-commit hook asks Stripe with the idempotency key `refund-<id>-<attempt>`. If the correction rolls back, no money leaves. A repeated send of the same attempt can't pay twice, and a retry is a new attempt with a new key, so it isn't swallowed by Stripe's memory of the failed one.
- **No correction while a payment is with the bank.** A PROCESSING payment isn't on the ledger yet, so the balance a correction would plan against may be about to change, and a refund could be split over money that never arrives. B4 and the API both say "A payment is waiting for the bank. Correct this once it settles." The wait is usually short, and holding a credit balance instead is out of scope.
- **A REFUND line is posted only when Stripe's signed webhook says it succeeded.** Until then the customer is shown as owed (P1: "Refund on its way"), and the ledger still shows the credit. The line is posted on the PENDING → SUCCEEDED step under a row lock, and it names the refund and the Stripe event. A replayed or second "succeeded" event posts nothing, and a failed one marks the refund FAILED for the broker to retry. This is the same rule payments follow: the browser, or our own send, never decides that money moved.

## 2026-11-22: A refund is failed only when Stripe says no; an unclear answer is asked again, never re-sent

This replaces part of "A refund already promised isn't planned again": Stripe being unreachable no longer marks a refund FAILED. A timeout or 5xx can arrive after Stripe has made the refund. Marking it FAILED offered the broker a Retry, and a retry's new key would have sent the money a second time while the ledger showed it once. Now only a definite 4xx refusal fails a refund. An unclear answer leaves it PENDING with no Stripe id, and a scheduled sweep (`RefundSweep`, every minute, for refunds older than two minutes) sends it again under the same attempt's key. Every send, sweep or retry first asks Stripe for a refund already carrying our `refund_id` and adopts it, which still holds after Stripe forgets an idempotency key (about 24 hours). A refund webhook locks the policy before settling, so it queues behind a correction on the same policy. The insert of the REFUND line already waited on the policy row through its foreign key, so the explicit lock writes down an ordering we were relying on by accident. A webhook whose amount or payment intent doesn't match our refund is ignored.

## 2026-11-22: Cancelling: open payments are cancelled before anything is written, and "kept back" names the latest change

The cancel save calls `cancelOpenIntents` before inserting the CANCEL transaction, the same order as a correction, so a payment found with the bank stops the save before any write (the spec listed it after the writes; the outcome is the same because the transaction rolls back either way). The preview's "Unpaid balance kept back" line needs to say what that balance is for, so the plan carries `keptBackFor`: the type of the newest policy change (ISSUE, ADD_COVERAGE, CORRECT), the same rule P1 already uses for "why you owe". It is null when nothing is kept back.

## 2026-11-22: P1 and P6 read a cancellation back from what it posted

`MyCoverService` builds the `cancellation` block from the CANCEL transaction, its CREDIT lines and the refunds recorded against it, rather than storing a summary, so P6 can never disagree with the statement. A failed cancellation refund still reads as PENDING ("Refund on its way") to the customer, because the broker retries it and the money is still owed, the same rule as `refundWaiting`. `settledOn` is the day of the last REFUND line, posted only by a signed webhook.

## 2026-11-22: Cancelling posts one CREDIT line per cover instead of reversing and rebilling

Corrections reverse the original charge and rebill it, but a cancellation doesn't: it posts one negative CREDIT line per live cover for its unused days (annual price × unused days ÷ days in term, rounded half-up once per line), tied to the CANCEL transaction, the cover and its calculation. Reversing and rebilling would strike through charges the customer really did owe for the days they were covered, and would give four lines where the designs show two ("Base cover, unused days refunded"). The original charges stay live and unstruck above the credits, so the statement reads top to bottom as what was charged, then what came back. The database check `ledger_credit_traced` keeps every CREDIT negative and traced, and no row is ever edited.

## 2026-11-22: A cancelled policy's status flips at save, and the words take their tense from the date

The policy becomes CANCELLED when the broker saves, even when the cancel day is in the future, and `cancelled_on` records the day cover stops. There is no scheduled job to flip it on the day itself (out of scope). Instead, the copy compares the cancel day with today: "Your cover ends on Dec 1, 2026" before it, "ended" from that day on, because cover stops at the start of the cancel day. `end_date` keeps the original term end, since it sets the days in the term that every credit divides by.

## 2026-11-22: When the credit is smaller than what's owed, the rest stays owed and payable

If the unused days are worth less than the unpaid balance, nothing is refunded and the difference stays on the ledger as what the customer owes for the days they were covered ("$299.18 due · for the 91 days you were covered"). We don't write it off, which would need its own approval and is out of scope. P1 and P6 keep a Pay button for it, and `PaymentService.start` already accepts a payment on a cancelled policy. A payment still with the bank on a cancelled policy shows P1's "on its way" card, ahead of the cancelled card, so the customer doesn't pay twice.

## 2026-11-22: "Kept back" names a change only when that change alone left the balance owing

This replaces the "kept back names the latest change" part of the earlier cancelling entry. A code review found that the newest change can be the wrong answer: if the extra cover was never paid and then its start was corrected, B4 said "Correction still owed" and P6 said "from the start-date fix" for $656.00 that was mostly unpaid extra cover. Now `CancelPolicyPlan.soleCauseOfBalance` names the newest change only when the balance just before its first ledger line was paid off (zero or less). Otherwise it returns null, and the words stay neutral ("Still owed" on B4, "The $656.00, still owed" on P6). B4's preview and P6's read-back both use this one rule, so they can't disagree.

## 2026-11-22: The statement reuses the ledger's kinds, and money on its way keeps the balance it found

`Statement.Row.kind` is the ledger's own `EntryType` (CHARGE, CREDIT, REVERSAL, PAYMENT, REFUND) rather than a second list of kinds, so a pending payment or refund is just a PAYMENT or REFUND row with `pending = true`. A pending row carries the balance of the row before it, unchanged, and pending rows come last, so the last settled row's balance is always the ledger balance to the cent. A declined attempt is not pending: it is the $0.00 PAYMENT line the webhook already posts, marked DECLINED. The banner's due reason uses `MyCoverService.DueReason` for now; T3 may move the decision into a shared helper, and the test only checks the reason's name so that move doesn't break it.

## 2026-11-22: On P3, money on its way shows its amount, marked "Not counted yet"

The P3 design puts the word "Pending" where a waiting payment's amount would be. We show the amount instead (muted, "−$14.00") with "Not counted yet" under it, because every amount on the statement opens "How we got this" and a tappable "Pending" would hide the number. The banner keeps the design's "Waiting for your bank" pill, and while a payment is waiting it says what is still owed until the bank confirms. The latest correction card starts open and older ones start closed, so the change that explains today's balance is visible without a tap. The statement has no Pay button: P1 owns paying, and it knows whether payments are switched on.

## 2026-11-22: The statement carries each amount's "how we got this", instead of a per-line endpoint

The tech plan had a separate request per ledger line for its explanation. Instead, `GET /api/me/statement` returns every row with its trace inside it: the maths, the change that caused it and who made it, the payment or refund, and the references (PTX-…, LE-…, pi_…, re_…, evt_…). The drawer then opens instantly with nothing to load or fail, and the list and the drawer can't show different numbers because they come from the same response. A policy has a few dozen lines at most, so the extra payload is small. `StatementService` is role-neutral, so the broker's history (`broker-history`) adds a broker controller over the same service rather than a second set of traces.

## 2026-11-22: Money on its way is on the statement but outside the balance

A card payment still with the bank and a refund Stripe hasn't confirmed both appear as statement rows ("Waiting for your bank", "Refund on its way"), so the customer sees the money they've sent or are owed. But they don't change the running balance, because they aren't on the ledger: a payment is posted only when Stripe's webhook says it succeeded, and a refund only when Stripe says it was sent. The running balance therefore always ends at the ledger balance to the cent, and a payment that then fails never has to be taken back off the statement. Each pending row's drawer shows the balance before → after it would make once confirmed, and says it isn't counted yet.

## 2026-11-23: B5 payment and refund cards also carry their own ID in the title

The B5 design titles a payment "Payment, card ending 4242" with no ID. History's titles add the payment's or refund's own ID ("Payment, card ending 4242 · PAY-0001", "Refund failed, card ending 4242 · RF-0001"), the same way change cards end with their PTX-…, because the broker uses History to quote IDs to support and a payment's PAY-… is otherwise on no reference list. The detail line shows the Stripe refs in full (pi_…, re_…, evt_…) rather than the design's truncated "pi_3Q…", so they can be copied into Stripe.

## 2026-11-23: On B5, a correction's net isn't a button; its two line amounts are

"How we got this" explains one ledger line, and a correction card's "+$14.00 net" is the sum of two (the undo and the corrected line). So on a card with one line the headline amount opens that line's drawer, while on a correction the "−$642.00" and "+$656.00" inside the detail sentence are the buttons, as are the LE-… chips. The net is explained by the sentence it sits above, and every number in it opens its own line.

## 2026-11-23: The broker's history groups the statement's rows on the server

B5 History is not a second calculation. `HistoryService` takes the rows and traces `StatementService` already builds for the policyholder and groups them by the action behind each one: the change (PTX-…), the payment (PAY-…) or the refund (RF-…). A card's amount is the sum of its rows, so the broker and the policyholder can't see different numbers, and the settled cards add up to the ledger balance to the cent. Grouping happens on the server so the web page only lays the cards out; the per-card words live in `historyCopy.ts`, which is unit-tested.

## 2026-11-23: The broker sees the real state of money that went wrong; the policyholder's wording stays soft

On P3 a refund Stripe failed still reads "Refund on its way", and a declined card reads "Didn't go through", because the customer can't act on Stripe's internals and the broker is already retrying. The broker can act, so B5 says "Refund failed" with Stripe's failure reason and a link to retry on B3, and shows the decline reason Stripe gave (read from `payments.failure_reason` through `PaymentRepository`). The "How we got this" drawer stays the same for both roles apart from the voice: same maths, cause and references, about "the customer" instead of "you".

## 2026-11-23: On P2, "Charged by the day" opens one line, or lists the lines when there are several

"How we got this" explains one ledger line. A cover that was only charged has one line in force, so "328 days · $656.00" is itself the button. After a cancellation a cover has a charge and a cancellation credit, so the detail card shows the total ("91 days kept · $299.18") with each line underneath, and each line's amount opens its own drawer. This follows the same rule as the B5 correction cards: every number that is a button explains exactly one line.

## 2026-11-23: P1's way into the timeline is the "See your cover timeline" row in the cover card, not a row in More

T7 planned a "Cover timeline" row in the More card. P1's design (and AC1) puts "See your cover timeline" as the last row of the "What you're covered for" card instead, directly above More, so a second row in More would have repeated the same link one card lower. The bars themselves also open P2 on tap; they are left out of the tab order so keyboard and screen-reader users meet the link once, as the row.

## 2026-11-23: The cover timeline takes its money from the statement, and counts only lines still in force

P2 is not a second calculation. `TimelineService` has no repository and no SQL: it reads the policy from `PolicyService` and the statement from `StatementService`, so every amount on the timeline is a statement row to the cent, and its "how we got this" drawer is that row's. A cover's "Charged by the day" is the sum of its CHARGE and CREDIT rows that are settled (not pending) and not undone. An undone line and its undo cancel out, so the corrected line is what counts. A cancellation credit is subtracted, so a cancelled cover shows what was kept. Rows are matched to a cover by the ledger line's own `coverage_id` (from `PolicyService`'s ledger view), not by comparing dates, so `Statement.Row` needed no new field. Payments and refunds are left out: they move money but don't change what the cover cost.

## 2026-11-23: After several corrections, P2 explains each cover's latest one

AC4's "each gets its sentence" means each corrected cover, not each correction: the timeline draws a cover's latest correction as the dashed outline and gives that one a sentence and a link to its statement card. Earlier corrections stay on the statement, where every correction has its own card. This keeps P2 about where your cover stands now, and the statement about everything that happened.

## 2026-11-23: Emails hear about money through events, and are claimed before commit and sent after it

The services that issue a policy, settle a payment and settle a refund publish an event (`NotifyEvents`) in their own transaction rather than calling `NotifyService`. `NotifyService` needs `PolicyService` for the owner's name and address, and `PolicyService` already depends on `RefundService`, so a direct call from `RefundService` would have made a constructor cycle. The listener is a plain synchronous `@EventListener`, so it still runs inside the change's transaction. There it writes the email from what is being committed (for example, the balance left to pay after the payment) and claims a `sent_emails` row keyed by (kind, source). Resend is called only after commit, and how the send ended is recorded in its own transaction. As a result, a rolled-back change sends nothing, a replayed webhook finds the row already claimed, and a Resend outage never undoes a payment. The emails are built in Java (`EmailTemplates`), not as template files, so the HTML and plain-text versions are always written side by side and tested together.

## 2026-11-23: An email that was claimed but never sent stays PENDING, and nothing retries it

An email is sent after the change behind it commits. If the API process dies, or is redeployed, in the moment between the commit and Resend's answer, the `sent_emails` row stays PENDING and the owner gets no email. I accept that for the demo: the money is right either way, the row shows exactly which email is missing, and an email is a courtesy, not the record (the statement is). Sending before commit would be worse, because a rolled-back payment would already have told the owner it went through. In production a sweep would resend rows left PENDING for more than a few minutes, under the same `Idempotency-Key: <kind>/<source_id>` so Resend can't deliver one twice, the way `RefundSweep` does for refunds. A send that fails or times out (5 s to connect, 10 s to read) is marked FAILED with the error and logged as a warning; it isn't retried either.

## 2026-11-23: Every demo email goes to one test inbox; the owner's address is shown, not used

Without a verified domain, Resend's sandbox sender (`onboarding@resend.dev`) only delivers to the address that owns the Resend account. Owners in the demo are fictional, so their addresses couldn't receive mail anyway. Every email is therefore sent to `RESEND_TEST_TO`, while `sent_emails.to_address` records the owner's address and each email says "Sent to <owner email>" with the footer "Demo with fictional data, delivered to a test inbox." This is simulated delivery: the content, timing and once-only rule are real, and only the recipient is swapped. Email is switched on only when both `RESEND_API_KEY` and `RESEND_TEST_TO` are set; otherwise the app starts, logs that email is off, and records each email as SKIPPED, so tests and a fresh clone never call Resend. Going live means verifying a domain, setting `RESEND_FROM` to an address on it, and dropping the test-inbox swap.

## 2026-11-23: Railway needs four more variables for email

The API service on Railway gets `RESEND_API_KEY` (a sandbox key with send access only), `RESEND_FROM` (`Plainly <onboarding@resend.dev>`), `RESEND_TEST_TO` (the Resend account's own address) and `PLAINLY_WEB_URL` (the deployed web app's URL, used for the "Set up your account" and "See your statement" links). They are listed in `.env.example` with the same names. The key is set in Railway's variables, never committed. Without `PLAINLY_WEB_URL` the links point at `http://localhost:5173`, which is right for local runs only.

## 2026-11-23: Resend sandbox spike: it delivers only to the account owner, and its idempotency key holds

I ran the spike with curl against `POST https://api.resend.com/emails` with a sandbox key and the sender `onboarding@resend.dev`. Sent to the Resend account owner's address, it returned 200 with a message id. Sent again with the same `Idempotency-Key` and the same body, it returned the same message id, so one email was delivered, not two. The same key with a different body got a 409 `invalid_idempotent_request`. Resend remembers keys for 24 hours. Sent to any other address, such as a fictional owner's, it got a 403 ("You can only send testing emails to your own email address"). That confirms the one-test-inbox rule above: every demo email goes to `RESEND_TEST_TO`. Within one database the body is rendered once and claimed in `sent_emails`, so our own unique (kind, source_id) row is what stops a second send after Resend forgets a key. Across databases the key needed more; see "Resend's key is unique per claimed email" below. The sender line in `.env` is quoted (`RESEND_FROM="Plainly <onboarding@resend.dev>"`), because the README loads `.env` with `. ../.env`, and an unquoted `<` breaks that shell.

## 2026-11-23: Resend's key is unique per claimed email, and email can't hold up or undo a money change

These come from the code review of the emails spec.
- **Each email has its own Resend key.** Resend remembers an `Idempotency-Key` for 24 hours across the whole account, and the local stack and Railway share one sandbox account. Both number their policies, payments and refunds from 1, and so does a database that was reset. With the key `<kind>/<source_id>`, a second database's "policy 12" would either be refused (409, a different body) or, worse, get the old message id back and be marked SENT with nothing delivered (the same body, for example the demo seed after a reset). So each `sent_emails` row now gets a random `send_key` (V11), and Resend receives `<kind>/<source_id>/<send_key>`. That is unique to the claimed email and fixed for that row, so it still means "this one email". This extends AC5's `<kind>/<source_id>` key; the once-only rule itself is unchanged and still rests on the unique (kind, source_id) row.
- **A slow Resend doesn't slow the broker or Stripe.** After commit, the send goes to a small background pool (2 threads, 200 queued) instead of running on the request thread. Before, a Resend outage made "Issue policy" wait up to 15 s after the policy was saved, while holding two database connections. If the pool is full, or the app stops before a queued send runs, the row stays PENDING. That is the same accepted loss as a crash between commit and send. On shutdown the pool finishes what it holds for up to 20 s. Tests send in line (`plainly.email.send-async=false`), so they stay deterministic.
- **An email that can't be written never undoes the change.** Writing and claiming an email run under a savepoint inside the money change's transaction. If they fail (a policy that can't be read, a database error on the `sent_emails` row), only the savepoint rolls back: the payment, refund or policy commits without its email, and a warning is logged. Before, such a failure rolled back the payment's ledger line, and the webhook kept failing on every Stripe retry.
- **Resend's error text is cut to 300 characters** before it is stored in `sent_emails.error` or logged. Its real errors are short JSON; a proxy's error page shouldn't fill the table or the logs.

## 2026-11-23: Standard insurance terms everywhere, each with a "?" to explain it

This replaces the plain-words rule (the B4 entry's "undone in full" and "charged by the day", and the old jargon test). Screens now use the industry's own words: endorsement, mid-term change, premium and annual premium, pro-rata, effective date, policy period, reversal, rebill, return premium, short-rate penalty, ledger, general liability and product liability. Every one of them shows a small "?" that opens its plain explanation on hover, tap or keyboard. Brokers already say these words, and a policyholder who learns them can read any insurer's paperwork. Hiding them made the product look as if it didn't know them. The explanations live in one place, `web/src/lib/glossary.ts`. Copy helpers return plain strings, and screens render them through `<Glossed>`, which marks the first time each term appears in a sentence. `jargon.test.ts` now fails on a home-made substitute ("extra cover", "base cover", "money back"…) and on a term in a screen's own text that isn't inside `<Term>` or `<Glossed>`. Text inside a button, link or option can't hold a second control, so those labels rely on the explained term in the heading or hint next to them. "Product liability" alone names the coverage, because it can also be bought when the policy is issued; "endorsement" is used only where the coverage was added mid-term. Kept as they are: "customer" (not "insured"), the emails (an email has no "?" to explain a term), and ledger lines already posted under the old labels ("Base cover, full year", "Undo: …"), because the ledger is append-only.

## 2026-11-23: Add endorsement shows the policy period and stops dates at its end

B4 used to show only the policy's end date under the date field, and the date picker allowed days after the policy ended. Now the header shows the policy period with the days left ("Sep 1, 2026 → Sep 1, 2027 · 284 days left", or the cancelled range), the hint gives the full range an effective date can fall in ("Any day from Sep 1, 2026 to Aug 31, 2027"), and both the Add endorsement and Correct date pickers stop at the day before the policy ends. The server still checks the same range, so this changes no rule; it only lets the broker see the range before picking a date.

## 2026-11-23: Removing extra cover: messages, field names and test-first skeletons

The Remove tab sends `{endDate, reason}`, and its date errors go under `endDate`: "Product liability can't end before its effective date (Oct 8, 2026)." and "Pick a day before the policy period ends (Sep 1, 2027).". A removed cover blocks everything that would touch it again, each under `policy`: removing again says "Product liability was removed on Dec 1, 2026.", adding cover says it "can't be added again", and correcting says "its effective date can't be corrected". The preview returns the one credit as `credit` (the same shape as a cancellation's credit) with `creditCents` and `refundTotalCents`. As with earlier specs, T1 adds `RemoveCoverCommand`, `RemoveCoverPlan` and a `RemoveCoverService` whose methods throw "not built yet", so the API suite compiles and the 39 new cases fail for the right reason.

## 2026-11-23: Remove cover speaks the glossary's words, so its copy differs from the spec's examples

The spec was shaped with "extra cover" in its examples, but it also says new copy must use the standard terms from `chore/standard-insurance-terms`, whose `jargon.test.ts` rejects "extra cover". That branch is merged into this one, and the spec's sentences are translated term for term: "Remove extra cover" becomes "Remove product liability", the credit line is the cancellation's own "Product liability return premium", the date field is the "Effective date" of the removal endorsement with the hint "Product liability stops at the start of this day. No short-rate penalty.", and the blocked and API messages say "Product liability was removed on Dec 1, 2026.". "Product liability" names the coverage rather than "endorsement", because removal works the same whether it was added mid-term or bought at issue. The amounts, rules and layout are unchanged. T1's API test strings were updated to match.

## 2026-11-23: A removal's credit is titled from its transaction, not a new ledger description

The API posts the removal's credit with the cancellation's own description ("Product liability return premium"), so the web tells the two apart by the transaction behind the line: `toLedgerRows` now takes the policy's transactions, and P3/B5 read `cause.transactionType`. A REMOVE_COVERAGE credit is titled "Product liability removed, return premium", and its drawer reads "because it was removed" instead of "because your policy ends early". `KeptBackFor` gains REMOVE_COVERAGE because the API returns the newest transaction's type there. In practice a removal never leaves money owing, but the maps stay exhaustive. T2 adds the types and a throwing `removeCoverCopy.ts` skeleton, so `tsc` is clean and the 31 new web cases fail for the right reason.

## 2026-11-23: Removing product liability is a credit, allowed once per policy, and recorded with `ended_on`

- **A removal is a CREDIT, not a reverse-and-rebill.** Nothing about the original charge was wrong: the customer had the cover and paid for it. A reversal would say the charge was a mistake and hide the days the cover really ran. So the REMOVE_COVERAGE transaction posts one CREDIT for the unused days only, priced exactly as a cancellation credits a cover (`CancelPolicyPlan.credits`: annual premium × unused days ÷ days in the policy period, rounded half-up once). The original charge stays as posted, and the ledger stays append-only. Reverse-and-rebill remains the tool for fixing a mistake (a wrong effective date).
- **Product liability can be removed once per policy, and never added back.** Adding it again would need a second coverage row of the same kind, and every reader (credits on cancellation, the timeline, P1, the statement) assumes one product liability per policy. Undoing a removal, or moving its date, would also need its own reversal rules. Neither is in the brief, so both are out of scope. Add, Correct and Remove each say why under `policy`, and B3 hides Correct and Remove once the cover is removed rather than offering buttons that can only lead to a blocked card.
- **`coverages.ended_on`, not a replaced row.** The cover keeps its own dates (`effective_from`, `effective_to`), and `ended_on` records when it stopped, checked in the database to fall inside them. A correction replaces the row because the old dates were wrong. A removal doesn't make them wrong; the cover just stopped early. One nullable column lets a later cancellation credit the cover only up to `coalesce(ended_on, effective_to)`, so no day is credited twice, and the timeline can stop the bar there and stripe the rest. `markEnded` is guarded by `ended_on is null`, so a second removal racing the first can't overwrite it.

## 2026-11-23: A cancellation after a removal credits exactly what is left of the cover's charge

From the code review. A removed cover's days are now credited in two pieces: the removal's (its end day → the cover's end) and, if the policy is later cancelled from an earlier day, the cancellation's (the cancel day → the removal day). Rounded separately, the two can give back one cent more or less than the cover was charged. In a 366-day policy period, product liability charged $598.36 for 300 days, removed after 10 days ($578.42) and then cancelled from its start, would get $19.95 + $578.42 = $598.37 back. So the cancellation's piece is priced as what the cover's days from the cancel day to its original end are worth, less what the removal already credited ($19.94 here). The two always add up to a single credit for the whole span, and a cover cancelled from its start gets back exactly its charge. The "how we got this" line still shows annual premium × days ÷ days in the policy period. In the rare case above, its amount is the cent-exact remainder rather than that sum rounded on its own. This only happens when the yearly price doesn't divide into whole cents a day. At $730.00 over 365 days it never does.

## 2026-11-23: Stripe and Resend idempotency keys carry a per-environment prefix

The Railway deploy and local runs share one Stripe sandbox and one Resend account, and each database numbers its rows from 1. So Railway's first payment sent `pay-2`, a key a local run had already used that day with another customer and amount, and Stripe refused it (`idempotency_error`), which the pay page shows as "couldn't reach our card processor". Every key the app sends (Stripe customers, intents, cancels, refunds; Resend emails) is now `<prefix>:<key>`. The prefix is `IDEMPOTENCY_PREFIX`, else Railway's own `RAILWAY_ENVIRONMENT_ID`, else `local`. It stays the same across redeploys, so a retry still reuses its key. Local runs still share `local` with each other, as before.

## 2026-11-23: Several policies — the API contract the failing tests pin (more-policies T1)

`GET /api/me/policies` answers `{policies: [...], totalDueCents}`, newest first. Each row has `policyId`, `policyNumber`, `start`, `end`, `status`, `balanceCents`, `paymentWaiting`, `refundWaiting {amountCents, cardLast4}` (the same shape P1 uses), and `replaces`/`replacedBy` as `{policyId, policyNumber}`. A refund on its way leaves the cancelled policy's balance negative until it settles, and `totalDueCents` adds up only positive balances, so the worked example owes $1,200.00 rather than $0. `POST /api/me/payments` takes `{policyId}`. A policy the owner doesn't hold, or that doesn't exist, is a 404 on every per-policy read and on pay, and the old `/api/me/cover|statement|timeline` routes answer 404. A rewrite that can't happen is a 422 under `replaces`, in plain words: "GL-… isn't cancelled. Cancel it before rewriting it.", "GL-… belongs to another customer.", "GL-… was already rewritten as GL-….", and "We couldn't find the policy to replace.". As in earlier specs, T1 adds `IssueForCustomerCommand` and two `IssuePolicyService` methods that throw "not built yet", so the API suite compiles and each new test fails for the right reason. `EmailTemplatesTest` still expects `/me/statement` links; T11 changes those links and that test with them.

## 2026-11-23: Several policies — the web contract the failing tests pin (more-policies T2)

Per-policy paths and query keys live in one place, `lib/mePaths.ts`, so no page spells `/me/policies/${id}/…` or a cache key by hand: `['my-cover', id]` keeps one policy's numbers from ever showing on another's page. `/me` opens the only policy's home, or "Your policies" from two up, and the old `/me/pay`-style routes go to that page of the newest policy (the API lists newest first). The overview's cards never net a refund against what is due: GL-2026-0013 reads "$1,200.00 · Refund on its way to your card ending 4242" while the total says $1,200.00 due. Two words of copy go further than the spec asked, because the single-policy wording would be wrong with several: a flat-cancelled policy's P1 says it "never covered your business" instead of "that cover ended on" its start date, and P5's "You owe nothing now" names the policy ("Nothing more is due on GL-2026-0014") when the owner holds more than one. "Rewrite" and "Flat cancellation" join the glossary with their "?" (AC11). As in T1, the new modules are skeletons that throw "not built yet", and `policyStatus`, `payPaidCopy` and `protectionSentence` take their new optional argument without using it yet, so `tsc` stays clean.

## 2026-11-23: A rewrite is checked on preview too, and every check runs under the replaced policy's lock

`previewForCustomer` runs the same rewrite checks as `issueForCustomer`, so B2 can say "GL-… isn't cancelled" before the broker presses Issue rather than after. Both lock the replaced policy's row first and then read it, so a second rewrite waits and then sees the first one's policy, which gives "GL-2026-0013 was already rewritten as GL-2026-0014." The unique index on `replaces_policy_id` is the backstop. If it ever fires, the 422 can't name the winning policy, because Postgres has aborted the transaction by then, so it says "That policy was already rewritten." A new customer's details and the start date are now checked separately (`checkCustomer`, `checkStart`). Both issues share one `write` step: the policy, its ISSUE transaction and covers, the ledger charges and the `PolicyIssued` event.

## 2026-11-23: The ownership check lives in PolicyService; MyPoliciesService.ownedPolicy wraps it

The spec has every per-policy read and payment go through `MyPoliciesService.ownedPolicy(me, id)`. `PaymentService` (billing) can't call into `me`, because `me` already depends on billing for P1's payment state, and that would be a cycle. So the rule itself is `PolicyService.requireOwned(customerId, policyId)`, one `exists` query in `PolicyRepository.isOwnedBy`. `MyPoliciesService.ownedPolicy` is a thin wrapper for the `me` module, and billing, statement and timeline call `PolicyService` directly. A policy that isn't the owner's and one that doesn't exist throw the same `NotFoundException`, so a 404 doesn't tell anyone which policy ids exist. P1's "refund on its way" helper (`MyCoverService.refundWaiting`) is now package-visible, so the list and P1 total pending refunds the same way.

## 2026-11-23: With one policy, `/me` redirects to `/me/policies/:id` rather than rendering the home in place

AC7 says that with one policy `/me` "is today's home for it". The owner sees exactly that page, but at `/me/policies/:id` after a replace-redirect, so each page has one address: the one emails link to and the one Pay, Statement and Timeline go back to. There's no "All policies" link with one policy, so the address change isn't visible. The cards on "Your policies" aren't whole-card links. Each one ends in an "Open GL-…" row, which keeps the "?" on Policy period and Rewrite clickable instead of nested inside a link. A note under the total ("a refund on one isn't taken off what's due on another") shows only when one policy has a refund on its way and another has money due.

## 2026-11-23: A policy issued wrong is flat-cancelled and rewritten, never edited in place

When a bound policy has the wrong start date (GL-2026-0013 from Sep 1 instead of Oct 1), we don't change its dates. The broker cancels it on its start day, a flat cancellation, which credits all of its premium. Then they rewrite it: a new policy for the right period, linked through `policies.replaces_policy_id`. This is how carriers and agency systems do it (Guidewire's policy-change reversal, the NFIP rule in 44 CFR 62.5, "Cancel Flat / Rewrite Full Term" in EZLynx and AmTrust). It also keeps our own rules. Nothing that was issued, charged or paid is overwritten, every amount still traces to its transaction, and each policy number keeps meaning one contract. Editing the start date would have meant reversing and rebilling the charges and re-dating the covers of a policy the owner already holds documents for. Rewriting an active policy in one step is out of scope, so the broker cancels it first.

## 2026-11-23: Money never moves between policies

The cancelled policy's return premium goes back to the card it was paid with, and the rewrite is billed from zero. We don't transfer a credit from one policy to the other, and a payment pays only the policy it was started for (`POST /api/me/payments {policyId}`). A transfer would put one payment on two policies' ledgers and break the trace from ledger entry to payment to Stripe event that every amount on screen relies on. It would also make the two policies' statements depend on each other. The cost is that in the worked example the owner briefly sees $1,200.00 on its way back and $1,200.00 due. "Your policies" says so in a sentence rather than hiding it.

## 2026-11-23: The old single-policy `/api/me/cover|statement|timeline` routes are removed, not kept as aliases

With several policies, "the" cover is ambiguous, and an alias would have to guess which policy (the newest one), which isn't always the policy the owner means. Only our own web app called these routes, and it ships in the same commit range, so nothing outside depends on them. They now answer 404, and every per-policy read checks ownership through `PolicyService.requireOwned`. The web pages at `/me/pay`, `/me/statement`, `/me/timeline` and `/me/cancellation` are different: a person may have them bookmarked or in an old email, so they still redirect to the newest policy's page.

## 2026-11-23: The overview's total due adds positive balances only

`totalDueCents` on `GET /api/me/policies` is the sum of the balances above zero. A cancelled policy with a refund on its way has a negative balance until the refund settles, and adding it in would show $0.00 due in the worked example while the new policy really is unpaid. It would also suggest the refund pays the new policy, which it doesn't (see "Money never moves between policies"). Refunds on their way show on their own policy's card instead.

## 2026-11-23: The owner email is checked at the edge with the service's pattern, not `@Email`

Hibernate's `@Email` disagrees with `IssuePolicyService` both ways: it accepts `maya@juniperandrye` (no dot in the domain), which the service rejects, and it rejects `" lee@example.com "`, which the service trims and accepts (the web form sends the email as typed). Using `@Email` would either let a bad address reach the service or refuse one the broker typed with a stray space. `IssueRequest.ownerEmail` uses `@Pattern` built from the service's own `EMAIL_SHAPE`, with optional spaces around it, so the edge and the service accept exactly the same addresses and say the same "Enter an email like name@business.com.". The services' literal field messages became named constants so the annotations can reuse them.

## 2026-11-23: Save-only rules use a validation group, so the live preview doesn't ask for a reason

The correction, cancellation and cover-removal previews share their request record with the save, but the preview runs before the broker has written a reason. The reason's `@NotBlank` and `@Size` are in the `OnSave` group (`common/OnSave`). The preview binds with `@Valid` (the default group: only the date) and the save with `@Validated({Default.class, OnSave.class})`, which is Spring's form of `@Valid` that takes groups. This keeps one record per form instead of two near-identical ones, and matches what the services already do (`validate(..., saving)`).

## 2026-11-23: Validation has three layers: shape at the edge, rules in services, invariants in Postgres

**Edge.** Every body-taking endpoint (issue and its preview, issue for an existing customer, add cover, correct, cancel, remove cover, each with its preview, and sign-up) declares what it accepts on its request record (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`) and binds it with `@Valid`. A failure answers before any service or database call, with the same 422 body the services give (`ApiErrorHandler`), the services' own message constants, and fields in the record's order. An unreadable date (`common/LenientDate`) or an unknown cover kind reads as null, so it is a field message, not a 400. Malformed JSON is still a 400. Sign-in stays unannotated so it never says which field was wrong, and the Stripe webhook keeps its signature and event-id checks.

**Services.** Rules that need data stay in the services, unchanged: a start within a year of today, an email no one else uses, a policy that isn't cancelled, no payment waiting at the bank, a different start date, a refund retry only after a failure.

**The overlap is intentional.** The services still check blank fields, missing dates and lengths that the edge has already checked. `DemoSeeder` and any future internal caller (a job, an import) call the services without HTTP, so the services must not trust their input. Because the edge reuses the services' constants, the two can't drift apart in wording. The cost is that format errors now come before business errors: a blank name plus a taken email shows the name first and the email on the next submit. We accept that: both are still shown under their own field, one submit apart. One small difference: `@Size` counts the raw text while the service counts it trimmed, so a value just under the limit plus surrounding spaces is refused at the edge.

**Database audit, no migration.** The invariants that protect money and identity are already in Postgres: the append-only ledger (V2 triggers), integer cents with `amount_cents > 0` on payments and refunds, one ledger line per Stripe event (`ledger_entries_stripe_event_idx`), a reversal reverses one entry at most (`reverses_entry_id unique`), one owner email per customer and account (`lower(owner_email)` and `app_users.email` unique), `start_date < end_date`, a removal's end inside the cover (V12), and a policy rewritten at most once (V13). What isn't in the database is form rules: name and reason lengths, non-blank text. Those are about what a broker may type, not about data that would be corrupt without them, so we didn't add a migration (with its backfill risk on existing rows) for them.
