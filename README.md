# Court Fix

Court Fix will watch Better.co.uk tennis availability at Highbury Fields and notify two users when a matching court slot becomes bookable.

## Current milestone

The Java checker runs on GitHub Actions every five minutes. The installable web app supports sign-in, per-user court and weekly time preferences, Firestore persistence, and per-device Web Push subscriptions. The Java checker now reads those settings, detects newly bookable matching slots, queues push deliveries, retries transient failures, and removes expired subscriptions. Add the Firebase service account and VAPID private keys as repository secrets to turn on delivery.

The endpoint is used by Better's public booking site, but Better does not publish it as a supported third-party API. Its response or access rules may change. The current unauthenticated date window was observed to include today through five days ahead; the checker should fail clearly if Better changes that window.

## Repository structure

- `checker/` — Java 25 command-line checker, built with Maven.
- `config/courts.json` — current IDs for Highbury Fields Courts 1–11.
- `.github/workflows/check-courts.yml` — scheduled/manual checker workflow.
- `site/` — installable Progressive Web App, service worker, and Firebase client.
- `firestore.rules` — per-user Firestore access rules.
- `docs/` — setup and design notes.

## Configure Firebase

Follow [the Firebase setup guide](docs/firebase-setup.md) to create the Spark project, enable email/password sign-in, create the two accounts, publish Firestore rules, and add the local web app configuration. The app expects `site/firebase-config.js`, which is ignored by Git.

For a local preview, run `python3 -m http.server 8000 --directory site` and open `http://localhost:8000`. Firebase Auth and Firestore need a configured Firebase project. Push setup also needs the public VAPID key; the private VAPID key belongs in a GitHub Actions secret.

## Run the checker locally

Requirements: Java 25 and Maven. On Apple Silicon with Homebrew:

```sh
brew install openjdk@25 maven
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"' >> ~/.zshrc
echo 'export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version
mvn -version
```

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

1. Configure the notification secrets and test a real push on each device.
2. Monitor Better's response and GitHub Actions runs for reliability.

## Privacy and costs

GitHub Pages requires this repository to remain public on GitHub Free. Do not commit Firebase service-account files, VAPID private keys, or push subscription secrets. Web Push delivery remains off until the `FIREBASE_SERVICE_ACCOUNT_JSON` and `VAPID_PRIVATE_KEY` repository secrets are configured.
