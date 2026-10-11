# Architecture notes

The website will be a static Progressive Web App hosted on GitHub Pages. Firebase Authentication and Firestore will hold separate user preferences and browser push subscriptions for two approved accounts. Firestore security rules will scope reads and writes to the signed-in user's UID.

The Java checker is a batch backend, not an always-on server. GitHub Actions starts it on a five-minute schedule, reads both users' active preferences and subscriptions, checks Better availability, sends Web Push for matching newly-bookable slots, persists deduplication state, then exits. GitHub Actions secrets will hold server credentials and the VAPID private key.

The Better endpoint is used by its public booking page but has no published third-party API contract. Initial API inspection found that omitting `location_id` returns slots across all 11 Highbury courts for a date, reducing the checker to one request per date. The observed unauthenticated date window was today through five days ahead. Both behaviors must be rechecked during implementation and monitored for changes.

At a five-minute cadence, a six-date scan is about 51,840 date requests per 30-day month. This is an estimate based on the observed response shape and can change if Better's booking window changes. The checker waits one second between date requests to avoid sending them as a burst.
