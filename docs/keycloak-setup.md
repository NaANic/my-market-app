# Keycloak Setup Runbook

One-time manual setup required before running the full Docker Compose stack.
Keycloak admin console is at **http://localhost:8082** (mapped from container port 8080).

---

## Prerequisites

Start the stack first so Keycloak is running:

```bash
docker compose up keycloak -d
# Wait ~30 seconds for Keycloak to finish starting, then verify:
curl -s http://localhost:8082/health/ready
# Expected: {"status":"UP"}
```

---

## Step 1 — Log in to the admin console

Open **http://localhost:8082/admin/** in your browser.

- Username: `admin`
- Password: `admin`

---

## Step 2 — Create the `market` realm

1. Click the realm picker in the top-left corner (shows **Keycloak** by default).
2. Click **Create realm**.
3. Set **Realm name** to `market`.
4. Click **Create**.

You are now inside the `market` realm. All subsequent steps are performed here.

---

## Step 3 — Create the `market-app` client

This client is used by `market-app` to obtain tokens via the `client_credentials` grant
and attach them to requests sent to `payment-service`.

1. In the left sidebar go to **Clients → Create client**.
2. Fill in:
    - **Client type:** OpenID Connect
    - **Client ID:** `market-app`
3. Click **Next**.
4. On the **Capability config** screen:
    - **Client authentication:** ON (makes this a confidential client with a secret)
    - **Authentication flow:** tick **Service accounts roles** only.
    - Untick **Standard flow** and **Direct access grants** — neither is used.
5. Click **Next**, then **Save**.

### Copy the client secret

1. Open the `market-app` client → **Credentials** tab.
2. Copy the value of **Client secret**.
3. Paste it into your local `.env` file:

```dotenv
KEYCLOAK_CLIENT_SECRET=<paste-secret-here>
```

---

## Step 4 — Verify token issuance

From a shell (with `.env` sourced or the secret value substituted):

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8082/realms/market/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=market-app \
  -d client_secret=$KEYCLOAK_CLIENT_SECRET \
  | jq -r .access_token)

echo $TOKEN
```

Decode the token at **https://jwt.io** and confirm:

| Claim | Expected value |
|---|---|
| `iss` | `http://localhost:8082/realms/market` |
| `azp` | `market-app` |

---

## Step 5 — Verify payment-service protection

```bash
# 1. No token → should return 401
curl -i http://localhost:8081/balance

# 2. With token → should return 200
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8081/balance
```

If step 1 returns 200, the resource server security config is not active yet (check Phase 5).
If step 2 returns 401, the most likely cause is an issuer URI mismatch — confirm that
`payment-service` uses `http://keycloak:8080/realms/market` (internal network URL), not
`http://localhost:8082/realms/market`.

---

## Notes

- **No realm export script:** setup is intentionally manual as required by the assignment.
- **KC_HOSTNAME_STRICT=false** is set in Compose so that the internal hostname
  (`keycloak:8080`) and the external hostname (`localhost:8082`) are both accepted by
  Keycloak without redirect errors.
- **Local dev only:** the `admin/admin` credentials and `start-dev` mode are not suitable
  for production.
