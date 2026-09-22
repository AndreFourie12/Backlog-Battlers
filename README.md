<div align="center">
  <a href="https://github.com/AndreFourie12/Backlog-Battlers">
    <img src="images/BacklogBattlers_Logo.png" alt="Logo" width="120" height="90">
  </a>

<h3 align="center">Backlog Battlers</h3>
  <p align="center">
    PROG7314 Repo for Backlog Battlers, a mobile app designed to help gamers complete unfinished games.
    <br />
    <br />
    <a href="https://github.com/AndreFourie12/Backlog-Battlers/actions/workflows/android-ci.yml">
      <img src="https://github.com/AndreFourie12/Backlog-Battlers/actions/workflows/android-ci.yml/badge.svg" alt="Android CI status">
    </a>
  </p>
</div>

## Team Members
<table>
  <tr>
    <td align="center"><img src="https://github.com/ST10449392.png" width="80"/></td>
    <td>
      <b>Mihir Jagdaw</b><br/>
      Student Number: 10449392<br/>
      <a href="https://github.com/ST10449392">@ST10449392</a>
    </td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/AndreFourie12.png" width="80"/></td>
    <td>
      <b>Andre Fourie</b><br/>
      Student Number: 10438312<br/>
      <a href="https://github.com/AndreFourie12">@AndreFourie12</a>
    </td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/Doingle.png" width="80"/></td>
    <td>
      <b>Dylan Jurgens</b><br/>
      Student Number: 10434135<br/>
      <a href="https://github.com/Doingle">@Doingle</a>
    </td>
  </tr>
</table>


##  Table of Contents
<ol>
  <li>
    <a href="#project-overview">Project Overview</a>
    <ul>
      <li><a href="#built-with">Built With</a></li>
    </ul>
  </li>
  <li><a href="#core-purpose-and-scope">Core Purpose and Scope</a></li>
  <li><a href="#design-consideration-and-architectural-choices">Design Consideration and Architectural Choices</a></li>
  <li>
    <a href="#comprehensive-summary">Comprehensive Summary</a>
    <ul>
      <li><a href="#implementation-of-version-control">Implementation of Version Control</a></li>
      <li><a href="#github-actions">GitHub Actions</a></li>
    </ul>
  </li>

  <li><a href="#video-demonstration">Video Demonstration</a></li>
  <li><a href="#references">References</a></li>
</ol>

## Project Overview
To see full commit history, pipeline, and tests go to -> https://github.com/AndreFourie12/Backlog-Battlers.git

### Built With
- Kotlin (Both the Android app and the REST API)
- [Ktor] (https://ktor.io) - served as the framework for the API server and it's HTTP client
- [Exposed](https://www.jetbrains.com/help/exposed/home.html) - Enables access to the SQL for the API
- [IGDB API](https://api-docs.igdb.com/) - provided the game catalogue and average time to complete a game
- [Steam Web API](https://partner.steamgames.com/doc/webapi/ISteamUserStats) - provided the achievements and their rarity
- Google Sign-In, which is verified server-side with JWT
- Github Actions - CI was used for both the API and the App
<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Core Purpose and Scope

Backlog Battlers' provides a rarity and time to complete-weighted scoring for it's users. Meaning that if someone were to acquire an achievement only 0.8% of players have, they will be awarded more points compared to an achievement 40% of players have. Similarly, if a user finished a game in 70 hours, whereas the average time to beat is 100 hours, that user will be awarded more points than someone who finished the same game in 95 hours. To accomplish this a REST API did most of the heavy lifting. 

The scope for this part was the Google sign in, settings, the REST API and database, game search, library management, achievement scoring and the leaderboard. More features are to come in the final POE.
<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Design Consideration and Architectural Choices
**Two separate Gradle builds.** The API (`api/`) is its own Ktor project, to prevent the Android Gradle Plugin and the JVM/Kotlin toolchain from conflicting. Each of which have its own CI job.

**Authentication.** The app signs in with Google, which the API verifies the resulting ID token and issues its own short-lived JWT access token plus a longer-lived refresh token. Additionally, every endpoint that needs a signed-in user reads that token from the `Authorization: Bearer` header. If invalid, expired, or wrong-type token is rejected without leaking why and a deleted user's still-valid token is also rejected.

**Scoring.** Achievement points come from five rarity tiers (Common through Ultra Rare), which we sourced from Steam's global unlock percentages. Completion points scale with IGDB's average time-to-beat for that game, and a separate speed bonus rewards finishing faster than that average. An entry can be completed twice (story, then 100%) without ever being paid for the same achievement twice.

**Data.** Exposed tables mirror the domain closely: users, games, achievements, library entries, unlocked achievements, completion records and monthly leaderboard entries. Games and achievements are fetched from IGDB/Steam once and cached in our own database, so repeat requests never re-hit those APIs.

<p align="right">(<a href="#readme-top">back to top</a>)</p>


## Comprehensive Summary

### Implementation of Version Control

The format of branches were `feat/<initials>/<topic>`, then merged into `main` via pull requests which were reviewed before merging. 

**<img width="1283" height="569" alt="image" src="https://github.com/user-attachments/assets/217b2679-00fd-4e73-94d9-eccd71695334" />**

<p align="right">(<a href="#readme-top">back to top</a>)</p>



### GitHub Actions

There are two independent workflows run on every push: `api-ci.yml` builds the API tehn runs its unit and integration tests, `android-ci.yml`, similarly, builds the ANdroid app and runs its unit tests. Keeping the two separate matches the previously described Gradle builds.

**<img width="1352" height="571" alt="image" src="https://github.com/user-attachments/assets/11478388-1659-45cf-9ecd-93622960c4bb" />**


<p align="right">(<a href="#readme-top">back to top</a>)</p>


## Video Demonstration

** add unlisted video link here chuds **

## References 

Andrews, A., Irigoyen, M., Wiscombe, C., Richins, M., Simran, GreenTurtwig, Anders, J., Noble, P., Coyle, J. and Stoeckel, Q. (2026). Material design icons - icon library - pictogrammers. [online] pictogrammers.com. Available at: https://pictogrammers.com/library/mdi/ [Accessed 19 Sept. 2026].

---

Gleap Team (2025). 7 Best practices to help you build better mobile apps. [online] Gleap. Available at: https://www.gleap.ai/blog/7-best-practices-to-help-you-build-better-mobile-apps [Accessed 20 Sept. 2026].

---

Gunawan, H. (2023). User friends system & database design. [online] Coderbased.com. Available at: https://www.coderbased.com/p/user-friends-system-and-database [Accessed 19 Sept. 2026].

---

naveen.ctebs (2026). The UI.UX playbook. Tips tricks for exceptional design. [online] Scribd. Available at: https://www.scribd.com/document/805328183/The-UI-UX-Playbook-Tips-Tricks-for-Exceptional-Design [Accessed 20 Sept. 2026].

---

Nishant (2023). UX case study — Enhancing steam’s navigation to increase user retention. [online] Medium. Available at: https://medium.com/design-bootcamp/enhancing-steams-navigation-to-increase-user-retention-product-design-case-study-72a165d9572f [Accessed 19 Sept. 2026].

---

OpenAI (2025). ChatGPT. [online] ChatGPT. Available at: https://chatgpt.com/ [Accessed 19 Sept. 2026].

---

Saini, R. (2025). Implementing Google sign-in in your android app (complete 2025 guide — Java + kotlin). [online] Medium. Available at: https://medium.com/@rohitsaini3342/implementing-google-sign-in-in-your-android-app-complete-2025-guide-java-kotlin-7f314689c137 [Accessed 20 Sept. 2026].

---

SHOUT (2025). What makes a successful mobile app? | Shout Digital. [online] Shoutdigital.com. Available at: https://www.shoutdigital.com/insights/what-makes-a-successful-mobile-app/ [Accessed 19 Sept. 2026].

---

Ktor, 2026. Ktor Documentation. [Online] Available at: https://ktor.io/docs/ [Accessed 22 September 2026].

---

JetBrains, 2026. Exposed - Kotlin SQL Framework. [Online] Available at: https://www.jetbrains.com/help/exposed/home.html [Accessed 22 September 2026].

---

IGDB, 2026. IGDB API Documentation. [Online] Available at: https://api-docs.igdb.com/ [Accessed 22 September 2026].

---

Steam, 2026. Steam Web API - ISteamUserStats. [Online] Available at: https://partner.steamgames.com/doc/webapi/ISteamUserStats [Accessed 22 September 2026].

---

Twitch, 2026. Client Credentials Grant Flow. [Online] Available at: https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#client-credentials-grant-flow [Accessed 22 September 2026].

---

HikariCP, 2026. HikariCP - A solid, high-performance, JDBC connection pool. [Online] Available at: https://github.com/brettwooldridge/HikariCP [Accessed 22 September 2026].

---

GitHub, 2026. GitHub Actions Documentation. [Online] Available at: https://docs.github.com/en/actions [Accessed 22 September 2026].

---

Gradle, 2026. Gradle User Manual. [Online] Available at: https://docs.gradle.org/current/userguide/userguide.html [Accessed 22 September 2026].

## Declaration of AI Usage:

Throughout this project, members of our team utilised ChatGPT 5.0 LLM to assist with planning, brainstorming, architecture structuring, feature implementation, debugging and code review. All work involving AI usage has, to the best of our abilities, been credited where due or reworked to be made our own. 
Please find the links to our conversations below.

*Links to chats and detail:*

Friends functionality assistance: <br>
Assistance describing architecture/ design implementations as well as debugging: friendsrepository, FriendsFragment.kt, FriendsApi.kt, item_friend_request.xml <br> 
https://chatgpt.com/share/6ab25643-70c0-83ea-89db-eef5f5e47bd7

Google SSO assitance:<br>
SSO implementation planning, assistance, snippet design, API routing and debugging within: GoogleIdTokenVerifier.kt, JwtService.kt, AuthRoutes.kt <br>
https://chatgpt.com/share/6ab25b54-4b38-83ea-a847-3833c2b818a5

Ktor Setup and miscellaneous: <br>
REST API structure, Android Studio, GitHub, README <br>
https://chatgpt.com/share/6ab2832b-ffe8-83ea-acc4-661d38f2964a

