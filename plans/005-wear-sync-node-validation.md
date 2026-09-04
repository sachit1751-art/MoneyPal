# 005 — Validate the Wear message source node before ingest / snapshot replies

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` first.

## Why this matters

The phone app exposes a `WearableListenerService` that (a) **inserts
expenses** into the Room database from `EXPENSE_ADD` messages and (b) **replies
with up to 50 recent transactions** (amounts + comments) to
`EXPENSE_SNAPSHOT` requests. Both handlers currently act on any message that
arrives on the known path, from any source node, with no check that the
sender is the user's paired MoneyPal watch app. Any app installed on the
user's watch (or another node that can reach the Wearable DataClient) that
knows the message path can insert bogus expenses or read the user's spending
history. The Wear DataClient message surface is app-scoped on the watch side,
but defense-in-depth costs little here: verify the sender node against the
paired device's declared capability before acting. This is the standard Wear
OS companion-app pattern (`CapabilityClient`).

## Recon required before editing (do this first)

1. Find the capability name the Wear app declares:
   `grep -rn "android_wear_capabilities\|capabilities" wear/src/main/res/ wear/src/main/AndroidManifest.xml`
   — the manifest references `@array/android_wear_capabilities`; open the
   array to get the exact capability string(s).
2. Check whether the phone (`:app`) declares the same capability anywhere
   (`grep -rn "android_wear_capabilities" app/src`). The phone app's Wear
   bridge lives under `app/src/wear/java/.../wearsync/` and
   `app/src/foss/java/.../wearsync/` (FOSS flavor stubs the Play Services
   bridge — note: `PhoneWearMessageListener.kt` exists in **both** the foss
   and wear source sets; only the `wear` flavor has real Play Services).
3. Read these files fully before editing:
   - `app/src/wear/java/com/sachit/moneypal/wearsync/PhoneWearListenerService.kt` (the handlers below)
   - `app/src/wear/java/com/sachit/moneypal/wearsync/PhoneWearMessageListener.kt` (if it also receives messages — likely the FOSS-flavor abstraction)
   - `wear/src/main/java/com/sachit/moneypal/wear/sync/WearWatchListenerService.kt`
   - `wear/src/main/java/com/sachit/moneypal/wear/sync/WearSyncManager.kt` (how the watch sends; capability usage)

## Current state (key excerpt)

`app/src/wear/java/com/sachit/moneypal/wearsync/PhoneWearListenerService.kt`:

```kotlin
override fun onMessageReceived(messageEvent: MessageEvent) {
    logcat { "onMessageReceived: path=${messageEvent.path}, sourceNode=${messageEvent.sourceNodeId}" }
    when (messageEvent.path) {
        WearPaths.EXPENSE_ADD -> handleExpenseAdd(messageEvent)
        WearPaths.EXPENSE_SNAPSHOT -> handleSnapshotRequest(messageEvent)
        else -> { logcat { "unhandled path=..." }; super.onMessageReceived(messageEvent) }
    }
}
```

`handleExpenseAdd` decodes `ExpensePayload`, ingests via
`WearSyncEntryPoint.wearExpenseIngestor()`, acks. `handleSnapshotRequest`
queries `budgetRepository.getRecentTransactions(request.limit.coerceIn(1, 50))`
and sends the snapshot back. **No node check anywhere.**

## Chosen approach

Gate both handlers on the sender being a known node of the phone app's Wear
capability (the same capability the watch app advertises). If the check fails,
drop the message and log — do not ack, do not ingest, do not reply.

Concretely, in `PhoneWearListenerService` add a suspend helper:

```kotlin
private suspend fun isTrustedSource(nodeId: String): Boolean = runCatching {
    val capability = Wearable.getCapabilityClient(applicationContext)
        .getCapability(CAPABILITY_WEAR_COMPANION, CapabilityClient.FILTER_REACHABLE)
        .await()
    capability.nodes.any { it.id == nodeId }
}.getOrDefault(false)
```

with `CAPABILITY_WEAR_COMPANION` = the exact capability string the watch
declares (from recon step 1). In `onMessageReceived`, wrap the dispatch:

```kotlin
override fun onMessageReceived(messageEvent: MessageEvent) {
    when (messageEvent.path) {
        WearPaths.EXPENSE_ADD, WearPaths.EXPENSE_SNAPSHOT -> {
            scope.launch {
                if (isTrustedSource(messageEvent.sourceNodeId)) {
                    handle(messageEvent)
                } else {
                    logcat { "Dropping message from untrusted node=${messageEvent.sourceNodeId} path=${messageEvent.path}" }
                }
            }
        }
        else -> super.onMessageReceived(messageEvent)
    }
}
```

(Refactor the two handlers to take the already-validated event, or call them
from inside the launch — keep their current logic intact.)

**If recon step 2 shows the phone app does not declare the capability:**
add it — a `values/wear.xml` (or `values/arrays.xml` in the `wear` source
set of `:app`) declaring an `android_wear_capabilities` array with the same
string, and register it in `app/src/wear/AndroidManifest.xml`
(`<meta-data android:name="com.google.android.gms.wearable.capabilities"
android:resource="@array/android_wear_capabilities"/>` inside `<application>`),
mirroring `wear/src/main/AndroidManifest.xml`. The capability must match on
both sides or the phone will never see the watch as a reachable node and all
sync will break — verify the exact string from the watch's array.

## Steps

1. Recon (above). Record the capability string(s) and whether the phone
   declares them.
2. Implement the phone-side capability declaration if missing.
3. Implement `isTrustedSource` + dispatch gating in `PhoneWearListenerService`.
4. If `PhoneWearMessageListener.kt` (wear flavor) turns out to be the actual
   entry point for message handling (read it — if it forwards/acks), apply
   the same gate there instead/in addition; keep exactly one gating point per
   sensitive path.
5. Compile the wear flavor:
   `./gradlew :app:compileWearDebugKotlin :wear:compileDebugKotlin` → `BUILD SUCCESSFUL`.
6. (If feasible) on-device sanity check: watch → phone expense add still
   lands; the FOSS flavor still compiles:
   `./gradlew :app:compileFossDebugKotlin`.

## Done criteria

- [ ] Every `EXPENSE_ADD` / `EXPENSE_SNAPSHOT` handling path in the wear
      flavor checks `isTrustedSource(sourceNodeId)` (or equivalent) before
      ingest/reply; untrusted messages are dropped with a log line.
- [ ] Capability string is identical on watch and phone sides (grep both).
- [ ] `:app:compileWearDebugKotlin :wear:compileDebugKotlin :app:compileFossDebugKotlin` all `BUILD SUCCESSFUL`.
- [ ] No behavior change for the trusted path (watch still syncs when paired).

## Test plan

Unit tests are impractical for `WearableListenerService` without Play Services
fakes; rely on compile gates plus the on-device check if available. If the
project later adds Play-Services test doubles, the gate function
(`isTrustedSource`) is the unit to cover: trusted node → allowed, unknown
node → false, capability lookup failure → false (fail closed).

## Maintenance note

- New Wear message paths added later must be routed through the same gate.
- The FOSS flavor has no Play Services; keep any import of
  `com.google.android.gms.wearable` confined to the `wear` source set
  (compile of `compileFossDebugKotlin` proves you did).

## Escape hatches

- If the watch app's capability is **not** discoverable on the phone when
  they are paired normally (e.g. capabilities only resolve node-side), STOP
  and report — do not ship a gate that silently blocks all sync. An
  alternative fallback is validating that `sourceNodeId` equals the
  capability's node **or** falls back to allowing when the capability set is
  empty on first pair, with a log — but only adopt that with the report.
- If recon reveals the messages actually arrive through a different class
  than expected, adjust the gating file and say so in the report.

## Out of scope / boundaries

- No changes to `:sync-contract` protocol/payloads, paths, or schema.
- No changes to ack semantics on the trusted path.
- No changes to the watch-side sender logic beyond what's needed for the
  phone-side gate to work (capability string alignment).
