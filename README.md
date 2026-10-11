# Court Fix

Court Fix will watch Better.co.uk tennis availability at Highbury Fields and notify two users when a matching court slot becomes bookable.

## Current milestone

The Java availability checker runs on GitHub Actions every five minutes. The installable web app now supports sign-in, per-user court and weekly time preferences, Firestore persistence, and saving a Web Push subscription for each device. The Java workflow does not yet read those preferences or send notifications; that is the next backend milestone.

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

1. Deploy the site to GitHub Pages and configure its public Firebase and VAPID repository variables.
2. Extend the Java checker to read each user's preferences and subscriptions from Firestore.
3. Add Java Web Push delivery and notification deduplication, then configure the required private GitHub Actions secrets.

## Privacy and costs

GitHub Pages requires this repository to remain public on GitHub Free. Do not commit Firebase service-account files, VAPID private keys, or push subscription secrets. The first milestone makes availability checks only; it does not yet store user data or send notifications.
