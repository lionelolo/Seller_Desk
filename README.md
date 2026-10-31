# SellerDesk

A simple Android app (Kotlin + Jetpack Compose) to watch and manage Eldorado orders from your phone,
with a loud alarm when a new order arrives.

## What it does

- Log in with the store's Eldorado **Seller API** credentials (Client ID + Client secret)
- See orders ("Needs action" / "All"), open one, **Mark as delivered** or **Cancel**
- Background watcher checks Eldorado every 20 s and rings a **looping alarm** (alarm volume, vibration,
  full-screen over the lock screen) until you tap **Stop alarm**. It re-rings every 2 min if ignored.
- Settings has an "Alert readiness" checklist, a "Test the alarm" button and Diagnostics

## What it does NOT do (yet)

Eldorado's Seller API cannot do withdrawals/balance, chat with buyers, disputes, feedback or sales reports,
so the app can't either. Offer management (create/pause/edit offers) is possible through the API but not built yet.

## IMPORTANT: verify 3 things before first real use

Eldorado's public help pages confirm the token URL and the 15-minute token life, but do not list the order
endpoint paths. Open `app/src/main/java/com/sellerdesk/app/data/EldoradoApi.kt`, find `object Endpoints`,
and compare the lines marked `VERIFY` with the Seller API reference you can reach from
https://www.eldorado.gg/dashboard/seller-api :

1. `ORDERS` (list orders)
2. `DELIVER` and `CANCEL` (the order action paths)
3. `AUTH_PREFIX` (usually `"Bearer "`)

Also, the token request body (`clientId`/`clientSecret` JSON, with a form-encoded fallback) is a best guess.
Settings > Diagnostics shows the last call and Eldorado's reply, so mismatches are easy to spot.
There is **no Eldorado test environment**: every call hits live orders. Test with one cheap order first.

## Build the APK (easiest: GitHub, no installs)

1. Create a free GitHub account, make a **new private repository**, and upload this whole folder
   (keep the `.github` folder).
2. Open the repo > **Actions** tab > **Build APK** > **Run workflow**.
3. Wait ~5-8 minutes, open the finished run, download **SellerDesk-apk** at the bottom (a zip containing `app-debug.apk`).
4. Send `app-debug.apk` to your friend (Telegram/Drive/WhatsApp as a document).

## Build the APK (alternative: Android Studio)

1. Install Android Studio, choose **Open**, pick this folder, wait for Gradle sync.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file is in `app/build/outputs/apk/debug/`.

## Install on your friend's phone

1. Open the .apk > allow "Install unknown apps" for the app used to open it > Install.
2. Open SellerDesk, log in, allow notifications.
3. **Settings > Alert readiness**: make every dot green (notifications, lock-screen alert, battery unrestricted).
4. Phone settings: allow alarms in Do Not Disturb; on Xiaomi/Oppo/Vivo/Samsung/Huawei turn on Autostart and
   lock the app in the Recents screen so the system doesn't kill it.
5. Tap **Test the alarm** with the screen locked to confirm it is loud and appears over the lock screen.

## Getting the credentials (store owner does this)

1. Eldorado > Dashboard > **Seller API** > Generate API client credentials
2. Name it (e.g. "SellerDesk phone"), choose an expiration (1/14/30/90/365 days), generate.
3. Copy the **Client ID** and **Client secret** right away. The secret is shown only once.
4. Give them to your friend privately (never in a public chat or screenshot).

Notes: only **verified sellers** can use the API, max **2 active credentials** per account, and anyone holding the
secret can manage your offers and orders. Revoke it on the Seller API page if a phone is lost. Put a reminder
before the expiration date; when it expires the app stops working until you enter new credentials.

## How the alarm works (and its limits)

Eldorado documents no webhooks or push notifications for the Seller API, so the app **polls** from a foreground
service (permanent low-priority "watching" notification). That is the most reliable approach without running your
own server, but phone makers can still kill background apps. The readiness checklist and the steps above reduce that.
If you need guaranteed delivery, the upgrade path is a small always-on server that polls Eldorado and sends
Firebase push messages to the phones.

## Security notes

- Credentials are stored with Android Keystore-backed encryption and are sent only to eldorado.gg over HTTPS.
- Backups are disabled so the secret never lands in a cloud backup.
- The debug APK is signed with a debug key: fine for sideloading to friends. Don't publish it to the Play Store as is.

## Project layout

```
app/src/main/java/com/sellerdesk/app/
  data/EldoradoApi.kt        all Eldorado URLs + token handling + order calls
  data/Models.kt             order parsing
  data/CredentialStore.kt    encrypted credentials + settings
  OrderMonitorService.kt     background polling
  alert/AlertCenter.kt       notification + full-screen alert
  alert/AlarmPlayer.kt       looping alarm sound + vibration
  AlertActivity.kt           the full-screen alarm screen
  ui/Screens.kt, ui/Theme.kt login, orders, detail, settings, theme
```
