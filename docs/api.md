# Backlog Battlers API

## Quick facts

| | |
|---|---|
| Format | JSON, UTF-8 |
| Base URL | `https://backlog-battlers-api-7314-gyancxcndrh0akgn.uaenorth-01.azurewebsites.net` (Azure Web App, UAE North) |
| Local development | set `apiBaseUrl` in `local.properties`, see "Connecting from the Android app during development" |
| Authentication | Nothing so far, though once login is built, every route except `/health` is expected to need an `Authorization: Bearer <token>` header. |
| Errors | Always `{"error": "message"}` |
| Request IDs | Optional `X-Request-Id` header (8–64 letters, digits or `-`). Sent back on every response; generated when missing or invalid. The app sends one with every call so its Logcat line and the API's log line can be matched. |

## Status codes

| Code | Meaning |
|---|---|
| 200 | Success |
| 400 | The request was invalid (missing or malformed parameter) |
| 404 | The thing asked for does not exist |
| 502 | The game catalogue (IGDB) is unavailable. Try again later. |
| 500 | Unexpected server error. The body may be empty. |

## LIve endpoints

### `GET /health`

Reports that the API and its database connection are running. Azure App Service health checks poll this endpoint; a `503 Service Unavailable` response marks the instance unhealthy.

`200 OK` when the database answers:
```json
{
  "status": "ok",
  "database": "ok",
  "version": "abc1234"
}
```

`503 Service Unavailable` when the database check fails:
```json
{
  "status": "degraded",
  "database": "down",
  "version": "abc1234"
}
```

- `version`: The git commit short hash stamped during CI deployment, or `dev` during local runs.

### `GET /games/search`

Searches through the IGDB catalogue of games based on search query.

| Parameter | Required | Rules |
|---|---|---|
| `q` | yes | 1 to 100 characters after trimming |
| `page` | no | Whole number of 1 or more. Defaults to 1. Ten results per page. |

E.g. `GET /games/search?q=hades`:

```json
[
  {
    "gameId": 113112,
    "title": "Hades",
    "coverImageUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/cob9kr.jpg",
    "platforms": ["XBOX", "PLAYSTATION", "PC", "OTHER", "SWITCH"],
    "avgCompletionHours": null,
    "avg100PercentHours": null
  },
  {
    "gameId": 172092,
    "title": "Hade: Forbidden Levels",
    "coverImageUrl": null,
    "platforms": [],
    "avgCompletionHours": null,
    "avg100PercentHours": null
  }
]
```

Notes:

- **Hours are always null in search results.** You have to call `GET /games/{id}` to get them.
- **IGDB's results are in order of relevance** and can include obscure and fan-made titles.
- **`coverImageUrl` can be `null` and `platforms` can be `[]`.** So to be handled appropriately in the UI.
- **Paging:** fewer than 10 results means the last page. An empty list means no matches, or a page past the end.

| Error | Body |
|---|---|
| 400 | `{"error":"q is required and must be 1 to 100 characters"}` |
| 400 | `{"error":"page must be a whole number of 1 or more"}` |
| 502 | `{"error":"The game catalogue is unavailable right now"}` |

### `GET /games/{id}`

Returns one game, with time-to-beat figures. `{id}` is IGDB's game id, which is the `gameId` from search.

E.g. `GET /games/113112`:

```json
{
  "gameId": 113112,
  "title": "Hades",
  "coverImageUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/cob9kr.jpg",
  "platforms": ["XBOX", "PLAYSTATION", "PC", "OTHER", "SWITCH"],
  "avgCompletionHours": 70.44444444444444,
  "avg100PercentHours": 150.75
}
```

Notes:

- **Hours can be null** when IGDB has no figure, or when the figure is implausible (over 1000 hours) due to user-inputted data.
- **Each call saves or refreshes the game on the server**, so leaderboards and scoring can use it later without calling IGDB again.

| Error | Body |
|---|---|
| 400 | `{"error":"Game id must be a number"}` |
| 404 | `{"error":"Game 1 was not found"}` |
| 502 | `{"error":"The game catalogue is unavailable right now"}` |

### The `Game` object

| Field | Type | Notes |
|---|---|---|
| `gameId` | integer | IGDB's game id |
| `title` | string | |
| `coverImageUrl` | string or null | |
| `platforms` | string list | Any of `PC`, `PLAYSTATION`, `XBOX`, `SWITCH`, `OTHER`. No duplicates. Can be empty. |
| `avgCompletionHours` | number or null | Average hours to finish the main story |
| `avg100PercentHours` | number or null | Average hours to reach 100% |

WHich matches the app's `Game` model, except `cachedAt`, which the app sets itself when it caches a game.

### `DELETE /users/me`

Permanently deletes the signed-in user's account. Needs `Authorization: Bearer <access token>`.

Everything the account owns goes with it: library entries and their unlocked achievements, completion records, monthly points, friend requests (sent and received) and friendships. Other users are not affected, apart from losing the friendship. The access token stops working straight away.

| Status | Meaning |
|---|---|
| 204 | Deleted. No body. |
| 401 | `{"error":"Sign in to manage your account"}` (missing, invalid or expired token) |
| 404 | `{"error":"Account was not found"}` (already deleted) |

### `GET /users/me/settings` and `PUT /users/me/settings`

The app's preferences, saved to the account so they follow the user to another phone. Both need `Authorization: Bearer <access token>`. The fingerprint lock is not included: it belongs to one phone's sensor.

`PUT` replaces every setting at once, and the newest save wins. Both routes answer with the stored settings:

```json
{
  "language": "af",
  "themeMode": "LIGHT",
  "homeBackdrop": "COAST",
  "defaultPlatform": "PLAYSTATION",
  "achievementNotifications": true,
  "rankChangeNotifications": false,
  "seasonResetNotifications": true,
  "friendActivityNotifications": false,
  "updatedAt": 1761030000000
}
```

| Field | Allowed values |
|---|---|
| `language` | `en`, `af`, `zu` |
| `themeMode` | `DARK`, `LIGHT` |
| `homeBackdrop` | `HILLS`, `MOUNTAIN`, `MEADOW`, `FOREST`, `COAST` |
| `defaultPlatform` | `PC`, `PLAYSTATION`, `XBOX`, `SWITCH` |
| `updatedAt` | Epoch milliseconds, set by the server. Ignored in a `PUT`. |

| Status | Meaning |
|---|---|
| 200 | The stored settings |
| 400 | A value the app doesn't offer, e.g. `{"error":"language must be one of en, af, zu"}` |
| 401 | `{"error":"Sign in to use your settings"}` |
| 404 | `GET` only: `{"error":"No settings have been saved yet"}`. The app then uploads its own. |

How the app uses them: every change in Settings is uploaded straight away. If the upload fails (for example offline), the change is kept and uploaded the next time the app opens. Otherwise, opening the app or signing in downloads the account's settings, so a change made on another phone shows up.

The API also reads the notification switches before it sends a push (see below).

### `POST /devices` and `DELETE /devices/{token}`

The phones the signed-in user gets push notifications on. Both need `Authorization: Bearer <access token>`. The token is the phone's Firebase Cloud Messaging registration token.

`POST /devices` with `{"token": "<FCM token>"}` stores the token for the caller. The app sends it on sign-in, on launch and whenever Firebase issues a new token. Sending the same token again is harmless. If another account had that token (someone else signed in on the same phone), it moves to the caller.

`DELETE /devices/{token}` (token URL-encoded) forgets it. The app calls it on sign-out. Deleting the account removes all of its devices.

| Status | Meaning |
|---|---|
| 204 | Stored or removed. No body. `DELETE` answers 204 even when the token wasn't stored. |
| 400 | `POST` only: `{"error":"token is required"}`, or a token longer than 512 characters |
| 401 | `{"error":"Sign in to get notifications"}` |

### Push notifications

The API sends these through Firebase Cloud Messaging to every phone the recipient is signed in on:

| Push (`type`) | Sent to | When | Switch that turns it off | Tapping it opens |
|---|---|---|---|---|
| `FRIEND_REQUEST_RECEIVED` | the person asked | a friend request is sent | Friend activity | Friends, Requests tab |
| `FRIEND_REQUEST_ACCEPTED` | whoever sent the request | it is accepted | Friend activity | Friends |
| `FRIEND_COMPLETED_GAME` | each of the completer's friends | a game is marked complete | Friend activity | Activity (the feed, `GET /activity`) |
| `OVERTAKEN` | everyone the completer passed this month | a completion lifts them past you | Rank changes | Standings |

"Passed" means you had at least as many points as them before the completion and fewer after it.

Pushes are data-only messages: `type`, `userId` (the recipient), `actorName` and, for `FRIEND_COMPLETED_GAME`, `gameTitle`. The app writes the text itself in the user's language and drops a push meant for an account that is no longer signed in. Nothing is sent when the recipient switched that kind off, or when the account-wide `push_notifications_enabled` flag is off. A user who has never saved settings gets everything, because the app ships with every switch on. Tokens that FCM reports as gone are deleted.

The Achievements and Season resets switches are synced and stored, but no push uses them yet.

Configure the sender with `FIREBASE_SERVICE_ACCOUNT_FILE` (path to the service-account JSON) or `FIREBASE_SERVICE_ACCOUNT_JSON` (the JSON itself). Without either, the API runs normally and only logs the pushes it would have sent.

### `GET /activity`

The activity feed: what the signed-in user and their friends have been doing, newest first. Needs `Authorization: Bearer <access token>`.

| Parameter | Rules |
|---|---|
| `limit` | Whole number of 1 or more, at most 100. Defaults to 30. |
| `before` | Epoch milliseconds. Only events older than this are returned. Pass the previous page's `nextBefore`. |

```json
{
  "events": [
    {
      "eventId": "6f1c…",
      "type": "GAME_COMPLETED",
      "actorId": "7f3c…",
      "actorName": "Dylan",
      "actorAvatarUrl": "pfp_03",
      "gameId": 113112,
      "gameTitle": "Hades",
      "subjectId": null,
      "subjectName": null,
      "count": 1,
      "points": 120,
      "detail": "COMPLETIONIST",
      "createdAt": 1762340000000
    }
  ],
  "nextBefore": 1762340000000
}
```

`nextBefore` is `null` on the last page. Events are written when they happen, into the `activity_events` table, and only those by the user and their friends are returned, never a stranger's.

| `type` | Written when | Fields it uses |
|---|---|---|
| `GAME_COMPLETED` | a game is marked complete | `gameId`/`gameTitle`, `points` earned, `detail` = the completion type (`COMPLETIONIST` is 100%) |
| `ACHIEVEMENT_UNLOCKED` | achievements are newly unlocked on an entry (one event per update, however many) | `gameId`/`gameTitle`, `count`, `detail` = the rarest one's name, when known |
| `FRIEND_ADDED` | a friend request is accepted | `actorId` is who accepted, `subjectId`/`subjectName` who sent it. Both people see it |
| `RANK_PASSED` | a completion lifts the user past others on this month's leaderboard | `subjectId`/`subjectName` = the closest person passed, `count` = how many were passed |

A Steam import writes `ACHIEVEMENT_UNLOCKED` only for unlocks Steam dates within the last 7 days, and stores each unlock with the time Steam gives, so importing a whole history does not flood the feed.

| Status | Meaning |
|---|---|
| 200 | One page, as above |
| 400 | `limit` or `before` is not a number |
| 401 | `{"error":"Sign in to see your activity"}` |

The app ignores a `type` it does not know, so new kinds can be added without breaking older versions.

### `GET /users/me/profile`

The profile screen's whole answer, worked out from the signed-in user's own data. Needs `Authorization: Bearer <access token>`.

```json
{
  "userId": "7f3c…",
  "displayName": "Dylan",
  "avatarUrl": null,
  "xp": 850,
  "level": 4,
  "xpIntoLevel": 250,
  "xpForNextLevel": 400,
  "monthlyPoints": 80,
  "monthRank": 2,
  "gamesInLibrary": 103,
  "gamesCompleted": 4,
  "achievementsUnlocked": 212,
  "badges": [{ "id": "FIRST_BLOOD", "progress": 1, "target": 1, "earned": true }],
  "rarestAchievements": [
    { "achievementId": "ACH_1", "name": "Beat it", "iconUrl": "https://…", "rarityPercent": 0.4, "gameId": 113112, "gameTitle": "Hades" }
  ],
  "unlockedRecently": 9,
  "recentActivity": [
    { "gameId": 113112, "gameTitle": "Hades", "unlockedCount": 4, "lastUnlockedAt": 1762340000000, "achievementsUnlocked": 36, "achievementsTotal": 42 }
  ]
}
```

- **`xp`** is every point earned in any month. **`level`**, **`xpIntoLevel`** and **`xpForNextLevel`** come from it by the formula in the README (XP to reach level *n* is 50 × n × (n − 1)).
- **`monthRank`** is the rank on this month's leaderboard (ties share a rank), or `null` before the user has scored this month. **`monthlyPoints`** is this month's points.
- **`gamesInLibrary`** and **`gamesCompleted`** count games, so one game on two platforms is one.
- **`badges`** lists all 20, in a fixed order. A badge is earned when `progress` reaches `target`. The ids are `FIRST_BLOOD`, `CLEAN_SWEEP`, `COMPLETION_CLUB`, `FINISHER`, `THE_LONG_HAUL`, `NEEDLE_IN_A_HAYSTACK`, `GHOST_HUNTER`, `STATISTICALLY_IMPROBABLE`, `SHELF_CLEANER`, `DUST_OFF`, `COLD_TURKEY`, `BACKLOG_ZERO`, `TOP_OF_THE_TABLE`, `PHOTO_FINISH`, `COMEBACK`, `NEMESIS`, `DAILY_DRIVER`, `MARATHON_MONTH`, `GENRE_TOURIST` and `PLATFORM_AGNOSTIC`. The names and descriptions belong to the app; the rules are in the README.
- **`achievementsUnlocked`** counts each achievement once, even when the same game is in the library on two platforms.
- **`rarestAchievements`** are the user's fourteen rarest unlocked achievements, rarest first (two rows of seven in the app). Achievements Steam gave no rarity for are left out.
- **`unlockedRecently`** is how many achievements the user unlocked in the last 14 days, over every game.
- **`recentActivity`** lists the games those unlocks were in, the five most recently unlocked in first. `unlockedCount` is the unlocks within the 14 days and `lastUnlockedAt` the latest of them; `achievementsUnlocked` out of `achievementsTotal` is how far along the whole game is. The server stores no pictures, so the app takes each game's from the games it has cached.

| Status | Meaning |
|---|---|
| 200 | The profile, as above |
| 401 | `{"error":"Sign in to see your profile"}` |
| 404 | `{"error":"Account was not found"}` (the account was deleted) |

### `POST /users/me/steam/import`

Adds the games the user owns on Steam to their library. Needs `Authorization: Bearer <access token>`, a linked Steam account (`PUT /users/me/steam`), and `STEAM_API_KEY` on the server. No request body.

- A game not in the library yet is added on **PC**, in the **backlog**, with Steam's hours played (rounded to 0.1 h).
- A game already in the library on PC has its hours raised to Steam's figure. Hours are never lowered, so hours logged by hand are kept.
- A game already in the library on another platform is left alone; no second, PC copy is made.
- Steam apps IGDB has no game for (tools, soundtracks, demos) are skipped and counted in `notInCatalogue`.

```json
{
  "ownedOnSteam": 214,
  "added": 187,
  "hoursUpdated": 3,
  "unchanged": 9,
  "notInCatalogue": 15,
  "games": [ /* a Game object for each game added, so the app can show them straight away */ ]
}
```

| Status | Meaning |
|---|---|
| 200 | Imported, as above |
| 401 | `{"error":"Sign in to import your Steam games"}` |
| 403 | The Steam profile, or just its "Game details", is private, so Steam won't list the games |
| 404 | `{"error":"No Steam account is linked"}` |
| 429 | Imported less than 2 minutes ago. The `Retry-After` header says how many seconds to wait. Steam isn't asked |
| 502 | Steam or IGDB is down. Nothing was changed, and the user can retry straight away |

**API calls per import.** An import is kept as cheap as possible, because Steam allows 100,000 calls a day per key and IGDB 4 requests a second:

| | First import of a 300-game library | Importing again, nothing new |
|---|---|---|
| Steam | 1 (`GetOwnedGames` lists every game at once; names are left out because IGDB supplies them) | 1 |
| IGDB | about 4: Steam app → game matches in batches of 250, then details and time to beat in batches of 500, spaced 260 ms apart | 0 |

Which IGDB game each Steam app is gets stored in the `steam_apps` table and shared by every user, including the apps IGDB has no game for (asked about again after 30 days). Details are fetched only for games new to that user's library. The achievement lookups (`GET /games/{id}/achievements`, and the Steam check when a game is completed) read a game's Steam app from the same table, so after an import they don't ask IGDB either.

On the phone, the import's games are cached straight from this answer and the library is synced once. The library screen fills in achievement totals a few games at a time (at most 5 per load, played games first, one request at a time), so a large import never sends hundreds of requests at once.

## Planned endpoints

Routes follow the Part 1 design document, the below routes are yet to be built:

| Route | Purpose |
|---|---|
| `POST /auth/sso/google` | Sign in or register with Google |
| `GET /users/me` | The signed-in user's profile |
| `PATCH /users/me` | Change settings |
| `GET /users/me/library` | The user's library |
| `POST /users/me/library` | Add a game to the library |
| `PATCH /users/me/library/{id}` | Update hours, status, unlocked achievements |
| `DELETE /users/me/library/{id}` | Remove a library entry |
| `POST /users/me/library/{id}/complete` | Mark a game complete and award points |
| `GET /leaderboard/monthly` | This month's standings |
| `GET /users/me/progression` | XP, level and badges |
| `GET /users/me/streak` | Login streak |
| Season reset and achievement pushes | Pushes for the two notification switches the four live pushes don't cover |

Conventions once these exist:

- Enum values use the app's names: `Platform` (`PC`, `PLAYSTATION`, `XBOX`, `SWITCH`, `OTHER`), `LibraryStatus` (`BACKLOG`, `PLAYING`, `COMPLETED`, `ABANDONED`) and `CompletionType` (`MAIN_STORY`, `MAIN_EXTRA`, `COMPLETIONIST`).
- Timestamps are epoch milliseconds, matching the app's `Long` fields. Dates are `yyyy-MM-dd`.

## Logging

Every request is logged to the console with the request ID, HTTP status code, method, path, response time in milliseconds, and the signed in user's UUID (or `-` when unauthenticated).

```
14:02:11.532 INFO  [3f9a1c7e2b40] RequestLogging - 201 POST /users/me/library in 84ms user=7f3c2a1e-5b9d-4c11-9a0e-2d6f8b7e1c34
14:02:11.530 INFO  [3f9a1c7e2b40] LibraryRoutes - User 7f3c2a1e-… added game 113112 on PC as library entry 0c5e…
```

- **Environment level:** `LOG_LEVEL` sets the root logger level. Defaults to `INFO` on hosted environments; set to `DEBUG` locally for detailed output.
- **Excluded from logging:** `/health` check requests are omitted from request logging to keep log streams clean.
- **Secrets and personal data:** Authorization headers, access tokens, refresh tokens, Google ID tokens, emails, display names, query parameters, and request bodies are never logged.
- **Slow request warnings:** Any request taking longer than 1000ms triggers a `WARN` log line identifying the slow endpoint.

## How to run the API

Requires JDK 21. From the repo root:

```
cd api
.\gradlew.bat run
```

Run the tests and the build with `.\gradlew.bat build`. Needless to say, never commit secrets. Keys live in environment variables only.

| Variable | Purpose |
|---|---|
| `APP_ENV` | Application environment. When set to `production`, `DB_URL` is required and the server refuses to start on an in-memory database. |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | IGDB access, from a free application at [dev.twitch.tv/console](https://dev.twitch.tv/console). Without them the API still starts, but `/games` routes return 502. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | The database, for example `jdbc:postgresql://host/dbname?sslmode=require`. Production uses Azure Database for PostgreSQL (flexible server) over TLS. Without `DB_URL` in development the API uses an in-memory database and loses its data on restart. |
| `PORT` | Port to listen on. Defaults to 8080. |

## Connecting from the Android app during development

The Android app points to the Azure-hosted API by default. This section is only needed when pointing a local build at a developer's laptop:

- The phone and laptop must be on the same Wi-Fi. Set `apiBaseUrl=http://<laptop IPv4 address>:8080` in `local.properties` (find your address with `ipconfig`).
- The server listens on all network interfaces. If Windows Firewall asks about Java, allow it on private networks.
- The API speaks plain HTTP locally, so the debug build of the app allows cleartext traffic (`android:usesCleartextTraffic="true"` in the debug manifest). This is for development only; release builds connect over HTTPS to Azure.
