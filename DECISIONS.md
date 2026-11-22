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

## 2026-11-22: P1 shows pending refunds only, summed, named by the card that gets the most

`refundWaiting` on P1 sums every PENDING refund on the policy. When a refund is split across two cards, the sentence names the card that gets the larger share rather than listing both, and B3 still lists each piece. A FAILED refund isn't shown to the customer as "on its way" until the broker retries it, because nothing is moving. A refund row is exposed as a public `RefundListing` record through `RefundService`, so `policy` and `me` never import a billing repository type.

## 2026-11-22: B4's Correct tab opens from a link, says "undone in full", and asks once in a real dialog

B4 picks its tab from `?tab=correct`, so B3's Correct action can link straight to it and Back returns to the same tab. The tabs are Add cover · Correct; the design's Cancel tab waits for its own spec rather than showing a dead button. Each tab has its own blocked card: Add cover is blocked when extra cover already exists, and Correct is blocked when there is none, when the policy is cancelled, or while a payment is with the bank. The design's "reversal" and "rebill" labels became "undone in full" and "charged by the day", each with a tap-to-explain note, so even broker copy needs no insurance vocabulary. "Customer will see" also quotes the broker's reason, since the customer reads it word for word. The confirm step is a native `<dialog>` (focus stays inside, Esc closes) and opens only when there is a price and a usable reason. Otherwise the save goes straight to the API, so the 422 messages appear under each field exactly as the server words them.
