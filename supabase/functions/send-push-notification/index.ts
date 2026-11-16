// Supabase Edge Function: send-push-notification
// Dispatches FCM HTTP v1 push notifications server-side ONLY to authorized, active device tokens.
// Supports both:
//   1. Direct / webhook push payload ({ user_ids, title, event_key, ... })
//   2. Scheduled / invoked queue drain ({ mode: "drain_queue", batch_size?: 25 })
// Deploy: supabase functions deploy send-push-notification --no-verify-jwt
// (Auth is verified strictly inside handler via PUSH_WEBHOOK_SECRET or SUPABASE_SERVICE_ROLE_KEY.)
// Required Secrets:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//   PUSH_WEBHOOK_SECRET       (shared random secret for Postgres pg_net / Database Webhook)
//   FCM_SERVICE_ACCOUNT_JSON  (JSON string of Firebase service account with firebase.messaging scope)

import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

interface PushPayload {
  mode?: "drain_queue" | "single";
  batch_size?: number;
  queue_id?: string;
  notification_id?: string;
  user_ids?: string[];
  category?:
    | "task_assignment"
    | "task_reminder"
    | "task_update"
    | "incident"
    | "incident_escalation"
    | "system_alert"
    | "admin_alert";
  title?: string;
  body?: string;
  event_key?: string;
  deep_link?: string;
  task_id?: string;
  incident_id?: string;
  scope_level?: string;
  importance?: "HIGH" | "NORMAL" | "LOW";
}

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

// Warm-isolate OAuth token cache (~55 minutes)
let cachedOAuthToken: { token: string; expiresAtEpochSec: number; clientEmail: string } | null = null;

function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length || a.length === 0) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function base64UrlEncode(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const raw = atob(b64);
  const buf = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    buf[i] = raw.charCodeAt(i);
  }
  return buf.buffer;
}

async function getFcmAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (
    cachedOAuthToken &&
    cachedOAuthToken.clientEmail === sa.client_email &&
    cachedOAuthToken.expiresAtEpochSec > now + 120
  ) {
    return cachedOAuthToken.token;
  }

  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64UrlEncode(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      exp: now + 3600,
      iat: now,
    }),
  );
  const unsigned = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const assertion = `${unsigned}.${base64UrlEncode(new Uint8Array(signature))}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenRes.ok) {
    throw new Error(`OAuth token exchange failed: ${tokenRes.status}`);
  }
  const tokenJson = await tokenRes.json();
  cachedOAuthToken = {
    token: tokenJson.access_token,
    expiresAtEpochSec: now + (Number(tokenJson.expires_in) || 3600),
    clientEmail: sa.client_email,
  };
  return tokenJson.access_token;
}

function isPermanentTokenInvalidation(status: number, errBody: string): boolean {
  if (status === 404) return true;
  if (errBody.includes("UNREGISTERED")) return true;
  if (status === 400 && errBody.includes("INVALID_ARGUMENT") && errBody.includes("registration token")) {
    return true;
  }
  return false;
}

function isTransientFcmError(status: number): boolean {
  return status === 429 || status >= 500;
}

async function dispatchSinglePayload(
  supabase: SupabaseClient,
  sa: ServiceAccount,
  fcmToken: string,
  payload: PushPayload,
): Promise<{ delivered: number; deactivated: number; transientFailures: number; permanentFailures: number }> {
  const userIds = payload.user_ids ?? [];
  if (userIds.length === 0 || !payload.title || !payload.event_key) {
    return { delivered: 0, deactivated: 0, transientFailures: 0, permanentFailures: 1 };
  }

  const { data: tokens, error } = await supabase
    .from("device_tokens")
    .select("id, user_id, device_id, token")
    .in("user_id", userIds)
    .eq("is_active", true);

  if (error) {
    throw new Error(`Device token lookup failed: ${error.message}`);
  }

  const activeTokens = (tokens ?? []).filter((t) => t.token && !t.token.startsWith("local:"));
  if (activeTokens.length === 0) {
    return { delivered: 0, deactivated: 0, transientFailures: 0, permanentFailures: 0 };
  }

  const endpoint = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const category = payload.category || "task_update";
  const channelId = `taskz_${category}`;
  const isUrgent =
    payload.importance === "HIGH" ||
    category === "incident_escalation" ||
    category === "system_alert" ||
    category === "task_reminder";

  let delivered = 0;
  let transientFailures = 0;
  let permanentFailures = 0;
  const deactivatedIds: string[] = [];

  const CHUNK_SIZE = 20;
  for (let i = 0; i < activeTokens.length; i += CHUNK_SIZE) {
    const chunk = activeTokens.slice(i, i + CHUNK_SIZE);
    await Promise.all(
      chunk.map(async (row) => {
        try {
          const fcmRes = await fetch(endpoint, {
            method: "POST",
            headers: {
              Authorization: `Bearer ${fcmToken}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              message: {
                token: row.token,
                notification: {
                  title: payload.title,
                  body: payload.body ?? "",
                },
                data: {
                  event_key: payload.event_key ?? "",
                  category,
                  deep_link: payload.deep_link ?? "",
                  task_id: payload.task_id ?? "",
                  incident_id: payload.incident_id ?? "",
                  scope_level: payload.scope_level ?? "",
                  notification_id: payload.notification_id ?? "",
                  importance: payload.importance ?? "NORMAL",
                  title: payload.title ?? "",
                  body: payload.body ?? "",
                },
                android: {
                  priority: isUrgent ? "high" : "normal",
                  collapse_key: payload.event_key,
                  notification: {
                    channel_id: channelId,
                    tag: payload.event_key,
                  },
                },
              },
            }),
          });

          if (fcmRes.ok) {
            delivered++;
          } else {
            const errBody = await fcmRes.text();
            if (isPermanentTokenInvalidation(fcmRes.status, errBody)) {
              deactivatedIds.push(row.id);
              permanentFailures++;
            } else if (isTransientFcmError(fcmRes.status)) {
              transientFailures++;
            } else {
              permanentFailures++;
            }
          }
        } catch {
          transientFailures++;
        }
      }),
    );
  }

  if (deactivatedIds.length > 0) {
    await supabase.from("device_tokens").update({ is_active: false }).in("id", deactivatedIds);
  }

  if (payload.notification_id && delivered > 0) {
    await supabase
      .from("notifications")
      .update({ push_dispatched_at: new Date().toISOString() })
      .eq("id", payload.notification_id);
    await supabase
      .from("push_dispatch_queue")
      .update({
        status: "SENT",
        dispatched_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      })
      .eq("notification_id", payload.notification_id)
      .neq("status", "SENT");
  }

  return { delivered, deactivated: deactivatedIds.length, transientFailures, permanentFailures };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const webhookSecret = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
    const fcmSaRaw = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "";

    const authHeader = req.headers.get("Authorization") ?? "";
    const incomingWebhookSecret = req.headers.get("x-push-webhook-secret") ?? "";

    const authorizedByServiceRole =
      serviceRoleKey.length > 0 && constantTimeEquals(authHeader, `Bearer ${serviceRoleKey}`);
    const authorizedByWebhook =
      webhookSecret.length > 0 && constantTimeEquals(incomingWebhookSecret, webhookSecret);

    if (!authorizedByServiceRole && !authorizedByWebhook) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
    }

    if (!supabaseUrl || !serviceRoleKey) {
      return new Response(JSON.stringify({ error: "Supabase service credentials missing" }), { status: 500 });
    }

    const payload: PushPayload = await req.json();
    const supabase = createClient(supabaseUrl, serviceRoleKey);

    if (!fcmSaRaw) {
      return new Response(
        JSON.stringify({
          delivered: 0,
          skipped: true,
          reason: "FCM_SERVICE_ACCOUNT_JSON secret is not configured",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }

    const sa: ServiceAccount = JSON.parse(fcmSaRaw);
    const fcmToken = await getFcmAccessToken(sa);

    if (payload.mode === "drain_queue") {
      const batchSize = Math.min(Math.max(Number(payload.batch_size) || 25, 1), 100);
      const { data: claimed, error: claimErr } = await supabase.rpc("claim_pending_push_dispatches", {
        p_batch_size: batchSize,
      });
      if (claimErr) {
        return new Response(JSON.stringify({ error: claimErr.message }), { status: 500 });
      }

      let totalDelivered = 0;
      let totalDeactivated = 0;
      let processed = 0;

      for (const item of claimed ?? []) {
        processed++;
        try {
          const itemPayload = item.payload as PushPayload;
          const res = await dispatchSinglePayload(supabase, sa, fcmToken, itemPayload);
          totalDelivered += res.delivered;
          totalDeactivated += res.deactivated;

          const nextStatus =
            res.delivered > 0 || res.transientFailures === 0
              ? "SENT"
              : Number(item.attempt_count ?? 1) >= 6
              ? "FAILED_PERMANENT"
              : "FAILED_RETRYABLE";
          const delaySeconds = Math.min(3600, 30 * Math.pow(2, Math.min(Number(item.attempt_count ?? 1), 6)));

          await supabase.rpc("complete_push_dispatch", {
            p_queue_id: item.id,
            p_status: nextStatus,
            p_error: res.transientFailures > 0 ? `${res.transientFailures} transient FCM errors` : null,
            p_retry_delay_seconds: delaySeconds,
          });
        } catch (err) {
          const msg = err instanceof Error ? err.message : String(err);
          const nextStatus = Number(item.attempt_count ?? 1) >= 6 ? "FAILED_PERMANENT" : "FAILED_RETRYABLE";
          await supabase.rpc("complete_push_dispatch", {
            p_queue_id: item.id,
            p_status: nextStatus,
            p_error: msg,
            p_retry_delay_seconds: 120,
          });
        }
      }

      return new Response(
        JSON.stringify({ mode: "drain_queue", processed, delivered: totalDelivered, deactivated: totalDeactivated }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }

    if (!payload.user_ids?.length || !payload.title || !payload.event_key) {
      return new Response(JSON.stringify({ error: "Missing required push payload fields" }), { status: 400 });
    }

    const result = await dispatchSinglePayload(supabase, sa, fcmToken, payload);
    return new Response(JSON.stringify(result), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(
      JSON.stringify({ error: err instanceof Error ? err.message : String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }
});
