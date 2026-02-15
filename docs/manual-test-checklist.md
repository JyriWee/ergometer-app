# Manual Test Checklist: BLE Device Selection and Connection Recovery

## Test Run Metadata

- Date:
- Tester:
- App branch / commit:
- Device model:
- Android version:
- Trainer model:
- HR strap model (optional):

## Preconditions

- Workout file is available for selection.
- Bluetooth is enabled.
- Trainer address can be tested in both states:
  - reachable (powered on)
  - unreachable (powered off / out of range)

## Legend

- Status values: `PASS`, `FAIL`, `BLOCKED`, `N/A`

## Test Cases

| ID | Scenario | Steps | Expected Result | Status | Notes |
|---|---|---|---|---|---|
| TC01 | Start blocked when FTMS MAC is empty | Select valid workout, leave trainer MAC empty, press Start session | Start does not begin, required MAC error is shown |  |  |
| TC02 | Invalid FTMS MAC format validation | Enter invalid trainer MAC (example `AABB`) | Invalid format error is shown and start stays disabled |  |  |
| TC03 | Valid FTMS MAC accepted | Enter valid trainer MAC in canonical format | Validation clears and start can be enabled when workout is valid |  |  |
| TC04 | FTMS MAC persists across restart | Close app fully and relaunch | Saved trainer MAC is restored |  |  |
| TC05 | Normal start with reachable saved trainer | Power on trainer and press Start session | Flow goes MENU -> CONNECTING -> SESSION |  |  |
| TC06 | Auto-connect failure prompt | Power off trainer, keep saved trainer MAC, press Start session | App returns to menu and shows connection issue dialog |  |  |
| TC07 | Dismiss connection issue prompt | In dialog, choose Not now | Dialog closes and user stays on menu |  |  |
| TC08 | Search again from failure prompt | In dialog, choose Search again | Trainer device search opens and scan starts |  |  |
| TC09 | Scan permission denial handling | Revoke BLUETOOTH_SCAN, press Search trainer devices, deny permission | Scan status indicates permission is required |  |  |
| TC10 | Scan starts after permission grant | Grant BLUETOOTH_SCAN and press Search trainer devices | Scan status shows active scanning |  |  |
| TC11 | Trainer appears in scan results | Keep trainer on during scan | Device list shows trainer entry with MAC and RSSI |  |  |
| TC12 | Trainer picked from list | Tap trainer item in results | Picker closes, trainer MAC field is populated and valid |  |  |
| TC13 | No trainer found path | Keep trainer off and run trainer scan | No-results status is shown |  |  |
| TC14 | Cancel active scan | Start scan and press Cancel scan | Scan stops and picker closes |  |  |
| TC15 | Optional HR MAC left empty | Keep HR MAC empty and start session with valid trainer | Session start works normally |  |  |
| TC16 | Invalid HR MAC only affects HR field | Enter invalid HR MAC while trainer setup is valid | HR MAC field shows error, trainer start path still works |  |  |
| TC17 | HR device picked from list | Run HR search and pick an HR device | HR MAC field is populated and persists across restart |  |  |
| TC18 | Start path not blocked by stale prompt | Trigger connection issue once, then fix trainer and start again | Start flow works and prompt does not block |  |  |
| TC19 | Orientation resilience on menu/scan UI | Rotate between portrait and landscape during/after scan | No crash, UI remains usable |  |  |
| TC20 | End-to-end happy path | Select workout + trainer from scan list, start session, end session | Full flow completes without regression |  |  |

## Summary

- Total PASS:
- Total FAIL:
- Total BLOCKED:
- Overall verdict:

## Defects Found

| Defect ID | Related TC | Severity | Description | Repro Steps | Screenshot / Log |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

