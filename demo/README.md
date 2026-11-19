# Kitepost demo (HTML, CSS, JS, JSON)

The full Kitepost interface with **no server and no database**. A small simulator in the browser (`js/mock-server.js`) answers the same requests as the real app and returns the same JSON. Use it for presentations, user testing and agreeing on screens.

It opens with a sample campaign: dental scheduling software, targeting **Texas and California (US)** and **Maharashtra and Karnataka (India)**. It has 42 companies across every category and status, plus replies.

## Run it

Serve the folder with any static server:

```bash
python3 -m http.server 8000      # or: npx serve .
```

Open http://localhost:8000. Double-clicking `index.html` won't work, because browsers block the JSON files when a page is opened from disk. You can also upload the folder to any static host (Netlify, GitHub Pages, S3).

Changes are kept in this browser. Use **Settings → Reset sample data** to start over before each presentation.

## Files

```
index.html                 page shell
css/styles.css             all styles; light and dark theme tokens at the top
js/app.js                  every screen: campaigns, dashboard, lead details, wizard, review, replies, settings
js/api.js                  API client. USE_MOCK = true → simulator; false → the app's /api routes
js/mock-server.js          in-browser stand-in for the app's API (same endpoints and JSON)
data/reference.json        countries (US and India first), US states, Indian states/UTs, languages, categories
data/templates.json        email templates per language and tone, footers, reply answers, simulated replies
data/mock-companies.json   names and cities for fictional companies (including Indian names), blocked domains
data/seed.json             the sample campaign: 42 leads, replies, activity
```

## What is simulated

| Part | In the demo | In the app |
|---|---|---|
| Finding companies | Fictional companies on `.example` domains | Brave, SerpAPI or Google search |
| Classifying and writing | Keyword rules and templates | Claude reads each site and writes each email |
| Sending | Recorded only, one every 4 s | Your SMTP mailbox |
| Replies | About 4 in 10 emails get a reply after 10–40 s | Your IMAP inbox |
| Storage | This browser (localStorage) | PostgreSQL or MySQL |

## 5-minute presentation script

**1. The problem (30 s).** "Finding customers means days of searching and writing the same email again and again. And half the companies you find are suppliers or competitors."

**2. The campaign (60 s).** Open the dental campaign.
- **Who we found**: customers, plus the competitors, partners and vendors that were filtered out. Point at the countries row: Texas and California, Maharashtra and Karnataka.
- Click **Competitors**, open one and show **Why this category**. Change a lead's category to show you're in control.

**3. Approval (60 s).** Open **Review & send**.
- Every email is drafted for that company, and nothing goes out without approval.
- Edit a line, click **Approve & queue**, then **Select all** and **Approve selected**.
- Show the footer, which has the unsubscribe line and postal address, and the **Queued** and **Sent** tabs.

**4. Replies (60 s).** Open **Replies**: the intent label, the summary and the suggested answer, then **Send answer**. A "Not interested" reply goes on the do-not-contact list automatically.

**5. A new campaign, live (90 s).** Click **New campaign** → **Fill with an example**.
- **Where:** United States and India are already selected. Pick a few US states and Indian states.
- **Customers:** the profiles and search phrases built from the description.
- **Sender:** the postal address is required because the US is selected.
- **Start:** the dashboard fills in live.

**Close:** "In the app, real search, Claude, your mailbox and your database replace the simulation. The screens stay the same."

## Use it against the real app

Copy `index.html`, `css/` and `js/` next to a running app, set `USE_MOCK = false` in `js/api.js`, and remove the `mock-server.js` script tag. The same pages then call `/api/*`.
