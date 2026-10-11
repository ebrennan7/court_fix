# Court Fix

Court Fix will watch Better.co.uk tennis availability at Highbury Fields and notify two users when a matching court slot becomes bookable.

## Current milestone

The first milestone is a Java availability checker running on GitHub Actions. It queries Better's public booking endpoint once per date (the response contains all 11 courts), for today through five days ahead, and reports currently bookable slots. The workflow runs every five minutes and can also be started manually.

The endpoint is used by Better's public booking site, but Better does not publish it as a supported third-party API. Its response or access rules may change. The current unauthenticated date window was observed to include today through five days ahead; the checker should fail clearly if Better changes that window.

## Repository structure

- `checker/` — Java 21 command-line checker, built with Maven.
- `config/courts.json` — current IDs for Highbury Fields Courts 1–11.
- `.github/workflows/check-courts.yml` — scheduled/manual checker workflow.
- `site/` — planned Progressive Web App for per-user settings and push subscriptions.
- `docs/` — setup and design notes.

## Run the checker locally

Requirements: Java 21 and Maven.

From the repository root:

```sh
mvn --file checker/pom.xml package
java -jar checker/target/court-fix-checker-0.1.0-SNAPSHOT.jar
```

To check a specific date:

```sh
java -jar checker/target/court-fix-checker-0.1.0-SNAPSHOT.jar --date=2026-10-12
```

Set `DAYS_AHEAD` to change the number of days beyond the start date to query. The default is five days.

## Planned next milestones

1. Build the installable web app and service worker.
2. Add Firebase Authentication and Firestore with per-user rules and access limited to two users.
3. Add Java Web Push delivery and notification deduplication.
4. Deploy the site to GitHub Pages and configure required Actions secrets.

## Privacy and costs

GitHub Pages requires this repository to remain public on GitHub Free. Do not commit Firebase service-account files, VAPID private keys, or push subscription secrets. The first milestone makes availability checks only; it does not yet store user data or send notifications.
