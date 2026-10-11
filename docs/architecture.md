# Architecture notes

The website is a static Progressive Web App hosted on GitHub Pages. Firebase Authentication and Cloud Firestore on the no-cost Spark plan hold separate user preferences and browser push subscriptions. There is no public account creation UI; the administrator creates the two accounts. Firestore security rules scope browser reads and writes to the signed-in user's UID. The app uses direct Web Push subscriptions with VAPID rather than Firebase Cloud Messaging.

The Java 25 checker is a batch backend, not an always-on server. GitHub Actions starts it on a five-minute schedule. It reads preferences and subscriptions from Firestore, checks Better availability, and places newly available matching slots in a Firestore outbox before attempting Web Push delivery. A transient failure remains queued for the next run; expired subscriptions are deleted. Firestore availability state prevents duplicate notifications while a slot stays bookable. GitHub Actions secrets hold the Firebase service-account JSON and VAPID private key.

The Better endpoint is used by its public booking page but has no published third-party API contract. Initial API inspection found that omitting `location_id` returns slots across all 11 Highbury courts for a date, reducing the checker to one request per date. The observed unauthenticated date window was today through five days ahead. Both behaviors must be rechecked during implementation and monitored for changes.

At a five-minute cadence, a six-date scan is about 51,840 date requests per 30-day month. This is an estimate based on the observed response shape and can change if Better's booking window changes. The checker waits one second between date requests to avoid sending them as a burst.
