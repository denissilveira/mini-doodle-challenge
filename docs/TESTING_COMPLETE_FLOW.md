# Complete API Test Flow

This guide tests the Mini Doodle API from user creation through meeting cancellation.

The flow will:

1. check application health;
2. create an organiser;
3. create a participant;
4. retrieve users (by ID, by email, paginated list);
5. create an available slot;
6. retrieve the slot;
7. list the organiser's slots;
8. verify overlap validation;
9. schedule a meeting;
10. retrieve the meeting;
11. update the meeting;
12. list the meeting for both the organiser and participant, then query availability and summary;
13. confirm that the slot is `BOOKED`;
14. verify duplicate booking protection;
15. verify organiser validation;
16. cancel the meeting;
17. confirm that the meeting no longer exists;
18. confirm that the slot is `FREE` again;
19. block the slot;
20. verify that a blocked slot cannot be booked;
21. unblock the slot;
22. update the slot's time range;
23. update the organiser's profile;
24. delete the slot.

## Requirements

The application must be running at:

```text
http://localhost:8080
```

The examples use Bash or Zsh, `curl`, and `jq`.

```bash
BASE_URL=http://localhost:8080
```

## 1. Check application health

```bash
curl --silent \
  "$BASE_URL/actuator/health" \
  | jq
```

Expected:

```json
{
  "status": "UP"
}
```

## 2. Create the organiser

```bash
ORGANIZER_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/users" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Denis Organiser",
    "email": "denis.organizer@example.com",
    "timezone": "Europe/Madrid"
  }')

echo "$ORGANIZER_RESPONSE" | jq
```

Store the generated ID:

```bash
ORGANIZER_ID=$(echo "$ORGANIZER_RESPONSE" | jq -r '.id')
echo "ORGANIZER_ID=$ORGANIZER_ID"
```

## 3. Create the participant

```bash
PARTICIPANT_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/users" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Fabiana Participant",
    "email": "fabiana.participant@example.com",
    "timezone": "Europe/Madrid"
  }')

echo "$PARTICIPANT_RESPONSE" | jq
```

Store the participant ID:

```bash
PARTICIPANT_ID=$(echo "$PARTICIPANT_RESPONSE" | jq -r '.id')
echo "PARTICIPANT_ID=$PARTICIPANT_ID"
```

## 4. Retrieve the users

Organiser by ID:

```bash
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID" \
  | jq
```

Participant by ID:

```bash
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/users/$PARTICIPANT_ID" \
  | jq
```

Organiser by email:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/email" \
  --data-urlencode "email=denis.organizer@example.com" \
  | jq
```

List users:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq
```

## 5. Create a free slot

This example uses 15 August 2026 from 09:00 to 10:00 UTC.

```bash
SLOT_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots" \
  --header "Content-Type: application/json" \
  --data '{
    "startAt": "2026-08-15T09:00:00Z",
    "endAt": "2026-08-15T10:00:00Z"
  }')

echo "$SLOT_RESPONSE" | jq
```

Store the slot ID:

```bash
SLOT_ID=$(echo "$SLOT_RESPONSE" | jq -r '.id')
echo "SLOT_ID=$SLOT_ID"
```

Expected status:

```json
{
  "status": "FREE"
}
```

## 6. Retrieve the slot

```bash
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/slots/$SLOT_ID" \
  | jq
```

## 7. List the organiser's slots

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq
```

Filter by `FREE`:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  --data-urlencode "status=FREE" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq
```

## 8. Verify overlap validation

This slot overlaps the existing interval:

```bash
curl --silent \
  --request POST \
  --url "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots" \
  --header "Content-Type: application/json" \
  --data '{
    "startAt": "2026-08-15T09:30:00Z",
    "endAt": "2026-08-15T10:30:00Z"
  }' \
  | jq
```

Expected HTTP status:

```text
409 Conflict
```

```json
{
  "code": "SLOT_OPERATION_CONFLICT"
}
```

## 9. Schedule the meeting

```bash
MEETING_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/meetings" \
  --header "Content-Type: application/json" \
  --data "{
    \"title\": \"Mini Doodle architecture review\",
    \"description\": \"Review the scheduling domain and concurrency decisions.\",
    \"participantIds\": [
      \"$PARTICIPANT_ID\"
    ]
  }")

echo "$MEETING_RESPONSE" | jq
```

Store the meeting ID:

```bash
MEETING_ID=$(echo "$MEETING_RESPONSE" | jq -r '.id')
echo "MEETING_ID=$MEETING_ID"
```

Check that the response contains:

- the generated meeting ID;
- the correct slot ID;
- the slot owner as organiser;
- the participant;
- the slot start and end times.

## 10. Retrieve the meeting

```bash
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/meetings/$MEETING_ID" \
  | jq
```

## 11. Update the meeting

```bash
UPDATED_MEETING_RESPONSE=$(curl --silent --fail-with-body \
  --request PUT \
  --url "$BASE_URL/api/v1/meetings/$MEETING_ID" \
  --header "Content-Type: application/json" \
  --data "{
    \"title\": \"Updated Mini Doodle review\",
    \"description\": \"Updated meeting description.\",
    \"participantIds\": [
      \"$PARTICIPANT_ID\"
    ]
  }")

echo "$UPDATED_MEETING_RESPONSE" | jq
```

Expected title:

```bash
echo "$UPDATED_MEETING_RESPONSE" | jq -r '.title'
```

```text
Updated Mini Doodle review
```

## 12. List the organiser's meetings

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/meetings" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq
```

Expected: one meeting in the list with the organiser's slot.

### List the participant's meetings

The same meeting is returned because this user was invited:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$PARTICIPANT_ID/meetings" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq
```

### Query availability and slot summary

The participant's effective `busy` intervals include the invitation even though the participant
does not own the organiser's slot:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$PARTICIPANT_ID/slots/availability" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  | jq
```

The organiser's persisted slot summary contains one booked slot:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots/summary" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  | jq
```

## 13. Confirm that the slot became booked

```bash
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/slots/$SLOT_ID" \
  | jq
```

Expected:

```json
{
  "status": "BOOKED"
}
```

List booked slots:

```bash
curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  --data-urlencode "status=BOOKED" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq
```

## 14. Verify duplicate booking protection

Try to create another meeting in the same slot:

```bash
curl --silent \
  --request POST \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/meetings" \
  --header "Content-Type: application/json" \
  --data "{
    \"title\": \"Second meeting\",
    \"participantIds\": [
      \"$PARTICIPANT_ID\"
    ]
  }" \
  | jq
```

Expected:

```text
409 Conflict
```

```json
{
  "code": "SLOT_NOT_AVAILABLE"
}
```

## 15. Verify organiser validation

The organiser cannot also be an invited participant:

```bash
curl --silent \
  --request PUT \
  --url "$BASE_URL/api/v1/meetings/$MEETING_ID" \
  --header "Content-Type: application/json" \
  --data "{
    \"title\": \"Invalid participant test\",
    \"description\": null,
    \"participantIds\": [
      \"$ORGANIZER_ID\"
    ]
  }" \
  | jq
```

Expected:

```text
400 Bad Request
```

```json
{
  "code": "ORGANIZER_CANNOT_BE_PARTICIPANT"
}
```

## 16. Cancel the meeting

```bash
curl --silent --fail-with-body \
  --request DELETE \
  --url "$BASE_URL/api/v1/meetings/$MEETING_ID" \
  --write-out "\nHTTP status: %{http_code}\n"
```

Expected:

```text
HTTP status: 204
```

## 17. Confirm that the meeting no longer exists

```bash
curl --silent \
  "$BASE_URL/api/v1/meetings/$MEETING_ID" \
  | jq
```

Expected:

```text
404 Not Found
```

```json
{
  "code": "MEETING_NOT_FOUND"
}
```

## 18. Confirm that the slot became free again

```bash
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/slots/$SLOT_ID" \
  | jq
```

Expected:

```json
{
  "status": "FREE"
}
```

## 19. Block the slot

```bash
curl --silent --fail-with-body \
  --request PATCH \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/status" \
  --header "Content-Type: application/json" \
  --data '{
    "status": "BLOCKED"
  }' \
  | jq
```

Expected:

```json
{
  "status": "BLOCKED"
}
```

## 20. Verify that blocked slots cannot be booked

```bash
curl --silent \
  --request POST \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/meetings" \
  --header "Content-Type: application/json" \
  --data "{
    \"title\": \"Blocked slot test\",
    \"participantIds\": [
      \"$PARTICIPANT_ID\"
    ]
  }" \
  | jq
```

Expected:

```text
409 Conflict
```

## 21. Unblock the slot

```bash
curl --silent --fail-with-body \
  --request PATCH \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/status" \
  --header "Content-Type: application/json" \
  --data '{
    "status": "FREE"
  }' \
  | jq
```

## 22. Update the slot's time range

```bash
curl --silent --fail-with-body \
  --request PUT \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID" \
  --header "Content-Type: application/json" \
  --data '{
    "startAt": "2026-08-15T11:00:00Z",
    "endAt": "2026-08-15T12:00:00Z"
  }' \
  | jq
```

Expected: the response reflects the updated `startAt` and `endAt`.

## 23. Update the organiser's profile

```bash
curl --silent --fail-with-body \
  --request PUT \
  --url "$BASE_URL/api/v1/users/$ORGANIZER_ID" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Denis Organiser Updated",
    "email": "denis.organizer@example.com",
    "timezone": "America/Sao_Paulo"
  }' \
  | jq
```

Expected: the response reflects the updated name and timezone.

## 24. Delete the slot

```bash
curl --silent --fail-with-body \
  --request DELETE \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID" \
  --write-out "\nHTTP status: %{http_code}\n"
```

Expected:

```text
HTTP status: 204
```

## Complete executable script

Save the following as `scripts/test-complete-flow.sh`:

```bash
#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SUFFIX="$(date +%s)"

echo "Checking application..."
curl --silent --fail-with-body \
  "$BASE_URL/actuator/health" \
  | jq

echo "Creating organiser..."
ORGANIZER_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/users" \
  --header "Content-Type: application/json" \
  --data "{
    \"name\": \"Denis Organiser\",
    \"email\": \"denis.organizer+$SUFFIX@example.com\",
    \"timezone\": \"Europe/Madrid\"
  }")

ORGANIZER_ID=$(echo "$ORGANIZER_RESPONSE" | jq -r '.id')
echo "ORGANIZER_ID=$ORGANIZER_ID"

echo "Creating participant..."
PARTICIPANT_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/users" \
  --header "Content-Type: application/json" \
  --data "{
    \"name\": \"Fabiana Participant\",
    \"email\": \"fabiana.participant+$SUFFIX@example.com\",
    \"timezone\": \"Europe/Madrid\"
  }")

PARTICIPANT_ID=$(echo "$PARTICIPANT_RESPONSE" | jq -r '.id')
echo "PARTICIPANT_ID=$PARTICIPANT_ID"

echo "Creating slot..."
SLOT_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots" \
  --header "Content-Type: application/json" \
  --data '{
    "startAt": "2026-08-15T09:00:00Z",
    "endAt": "2026-08-15T10:00:00Z"
  }')

SLOT_ID=$(echo "$SLOT_RESPONSE" | jq -r '.id')
echo "SLOT_ID=$SLOT_ID"

echo "Creating meeting..."
MEETING_RESPONSE=$(curl --silent --fail-with-body \
  --request POST \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/meetings" \
  --header "Content-Type: application/json" \
  --data "{
    \"title\": \"Mini Doodle architecture review\",
    \"description\": \"Complete API test.\",
    \"participantIds\": [
      \"$PARTICIPANT_ID\"
    ]
  }")

MEETING_ID=$(echo "$MEETING_RESPONSE" | jq -r '.id')
echo "MEETING_ID=$MEETING_ID"

echo "Reading meeting..."
curl --silent --fail-with-body \
  "$BASE_URL/api/v1/meetings/$MEETING_ID" \
  | jq

echo "Listing organiser meetings..."
MEETING_COUNT=$(curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/meetings" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq '.totalElements')

if [[ "$MEETING_COUNT" -lt 1 ]]; then
  echo "Expected at least one meeting but received $MEETING_COUNT"
  exit 1
fi

echo "Listing participant meetings..."
PARTICIPANT_MEETING_COUNT=$(curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$PARTICIPANT_ID/meetings" \
  --data-urlencode "page=0" \
  --data-urlencode "size=20" \
  | jq '.totalElements')

if [[ "$PARTICIPANT_MEETING_COUNT" -lt 1 ]]; then
  echo "Expected the participant meeting but received $PARTICIPANT_MEETING_COUNT"
  exit 1
fi

echo "Checking participant effective availability..."
PARTICIPANT_BUSY_COUNT=$(curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$PARTICIPANT_ID/slots/availability" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  | jq '.busy | length')

if [[ "$PARTICIPANT_BUSY_COUNT" -ne 1 ]]; then
  echo "Expected one participant busy interval but received $PARTICIPANT_BUSY_COUNT"
  exit 1
fi

echo "Checking organiser slot summary..."
BOOKED_SLOT_COUNT=$(curl --silent --fail-with-body \
  --get \
  "$BASE_URL/api/v1/users/$ORGANIZER_ID/slots/summary" \
  --data-urlencode "from=2026-08-15T00:00:00Z" \
  --data-urlencode "to=2026-08-16T00:00:00Z" \
  | jq '.booked')

if [[ "$BOOKED_SLOT_COUNT" -ne 1 ]]; then
  echo "Expected one booked slot but received $BOOKED_SLOT_COUNT"
  exit 1
fi

echo "Checking booked slot..."
SLOT_STATUS=$(curl --silent --fail-with-body \
  "$BASE_URL/api/v1/slots/$SLOT_ID" \
  | jq -r '.status')

if [[ "$SLOT_STATUS" != "BOOKED" ]]; then
  echo "Expected BOOKED but received $SLOT_STATUS"
  exit 1
fi

echo "Cancelling meeting..."
curl --silent --fail-with-body \
  --request DELETE \
  --url "$BASE_URL/api/v1/meetings/$MEETING_ID"

echo "Checking released slot..."
SLOT_STATUS=$(curl --silent --fail-with-body \
  "$BASE_URL/api/v1/slots/$SLOT_ID" \
  | jq -r '.status')

if [[ "$SLOT_STATUS" != "FREE" ]]; then
  echo "Expected FREE but received $SLOT_STATUS"
  exit 1
fi

echo "Blocking slot..."
BLOCKED_STATUS=$(curl --silent --fail-with-body \
  --request PATCH \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/status" \
  --header "Content-Type: application/json" \
  --data '{"status": "BLOCKED"}' \
  | jq -r '.status')

if [[ "$BLOCKED_STATUS" != "BLOCKED" ]]; then
  echo "Expected BLOCKED but received $BLOCKED_STATUS"
  exit 1
fi

echo "Unblocking slot..."
FREE_STATUS=$(curl --silent --fail-with-body \
  --request PATCH \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID/status" \
  --header "Content-Type: application/json" \
  --data '{"status": "FREE"}' \
  | jq -r '.status')

if [[ "$FREE_STATUS" != "FREE" ]]; then
  echo "Expected FREE but received $FREE_STATUS"
  exit 1
fi

echo "Updating slot time range..."
curl --silent --fail-with-body \
  --request PUT \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID" \
  --header "Content-Type: application/json" \
  --data '{
    "startAt": "2026-08-15T11:00:00Z",
    "endAt": "2026-08-15T12:00:00Z"
  }' \
  | jq

echo "Deleting slot..."
curl --silent --fail-with-body \
  --request DELETE \
  --url "$BASE_URL/api/v1/slots/$SLOT_ID"

echo "Flow completed successfully."
```

Make it executable:

```bash
chmod +x scripts/test-complete-flow.sh
```

Run it:

```bash
./scripts/test-complete-flow.sh
```

## Troubleshooting

### Duplicate email

Use different email addresses or generate a suffix:

```bash
SUFFIX=$(date +%s)
```

### Invalid timestamp format

Use an explicit UTC offset:

```text
2026-08-15T09:00:00Z
```
