# Backlog Battlers API

## Quick facts

| | |
|---|---|
| Format | JSON, UTF-8 |
| Base URL (phone on the same Wi-Fi) | `http://<laptop IPv4 address>:8080` |
| Base URL (Android emulator) | `http://10.0.2.2:8080` |
| Authentication | Nothing so far, though once login is built, every route except `/health` is expected to need an `Authorization: Bearer <token>` header. |
| Errors | Always `{"error": "message"}` |

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

Reports that the API is running.

```json
{"status":"ok"}
```

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

Conventions once these exist:

- Enum values use the app's names: `Platform` (`PC`, `PLAYSTATION`, `XBOX`, `SWITCH`, `OTHER`), `LibraryStatus` (`BACKLOG`, `PLAYING`, `COMPLETED`, `ABANDONED`) and `CompletionType` (`MAIN_STORY`, `MAIN_EXTRA`, `COMPLETIONIST`).
- Timestamps are epoch milliseconds, matching the app's `Long` fields. Dates are `yyyy-MM-dd`.

## How to run the API

Requires JDK 21. From the repo root:

```
cd api
.\gradlew.bat run
```

Run the tests and the build with `.\gradlew.bat build`. Needless to say, never commit secrets. Keys live in environment variables only.

| Variable | Purpose |
|---|---|
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | IGDB access, from a free application at [dev.twitch.tv/console](https://dev.twitch.tv/console). Without them the API still starts, but `/games` routes return 502. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | The database, for example `jdbc:postgresql://host/dbname?sslmode=require`. Without `DB_URL` the API uses an in-memory database and loses its data on restart. |
| `PORT` | Port to listen on. Defaults to 8080. |

## Connecting from the Android app during development

- The phone and laptop must be on the same Wi-Fi. Use the laptop's IPv4 address (`ipconfig`) with port 8080.
- The server listens on all network interfaces. If Windows Firewall asks about Java, allow it on private networks.
- The API speaks plain HTTP, so the debug build of the app must allow cleartext traffic, for example `android:usesCleartextTraffic="true"` in the debug manifest. This is for development only.
- The Android emulator reaches the laptop at `10.0.2.2`, not `localhost`.
