// Supabase Edge Function: send-push-notification
// Dispatches FCM HTTP v1 push notifications server-side ONLY to authorized, active device tokens.
// Deploy: supabase functions deploy send-push-notification
// Required Secrets:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//   FCM_SERVICE_ACCOUNT_JSON  (JSON string of Google Cloud Firebase service account with firebase.messaging scope)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface PushPayload {
  notification_id?: string;
  user_ids: string[];
  category:
    | "task_assignment"
    | "task_reminder"
    | "task_update"
    | "incident"
    | "incident_escalation"
    | "system_alert"
    | "admin_alert";
  title: string;
  body: string;
  event_key: string;
  deep_link: string;
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
  return tokenJson.access_token;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const fcmSaRaw = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "";

    // Require caller Authorization header matching service role or verified webhook secret
    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ") || (serviceRoleKey && authHeader !== `Bearer ${serviceRoleKey}`)) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
    }

    const payload: PushPayload = await req.json();
    if (!payload.user_ids?.length || !payload.title || !payload.event_key) {
      return new Response(JSON.stringify({ error: "Missing required push payload fields" }), { status: 400 });
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const { data: tokens, error } = await supabase
      .from("device_tokens")
      .select("id, user_id, device_id, token")
      .in("user_id", payload.user_ids)
      .eq("is_active", true);

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), { status: 500 });
    }

    const activeTokens = (tokens ?? []).filter((t) => t.token && !t.token.startsWith("local:"));
    if (!fcmSaRaw || activeTokens.length === 0) {
      return new Response(
        JSON.stringify({
          dispatched: 0,
          skipped: tokens?.length ?? 0,
          reason: !fcmSaRaw ? "FCM_SERVICE_ACCOUNT_JSON not configured" : "No external FCM tokens",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }

    const sa: ServiceAccount = JSON.parse(fcmSaRaw);
    const fcmToken = await getFcmAccessToken(sa);
    const endpoint = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

    const channelId = `taskz_${payload.category || "task_update"}`;
    const isUrgent =
      payload.importance === "HIGH" ||
      payload.category === "incident_escalation" ||
      payload.category === "system_alert";

    let delivered = 0;
    const deactivatedIds: string[] = [];

    for (const row of activeTokens) {
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
              event_key: payload.event_key,
              category: payload.category,
              deep_link: payload.deep_link ?? "",
              task_id: payload.task_id ?? "",
              incident_id: payload.incident_id ?? "",
              scope_level: payload.scope_level ?? "",
              notification_id: payload.notification_id ?? "",
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
        if (errBody.includes("UNREGISTERED") || errBody.includes("INVALID_ARGUMENT")) {
          deactivatedIds.push(row.id);
        }
      }
    }

    if (deactivatedIds.length > 0) {
      await supabase.from("device_tokens").update({ is_active: false }).in("id", deactivatedIds);
    }

    if (payload.notification_id) {
      await supabase
        .from("notifications")
        .update({ push_dispatched_at: new Date().toISOString() })
        .eq("id", payload.notification_id);
    }

    return new Response(
      JSON.stringify({ delivered, deactivated: deactivatedIds.length }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ error: err instanceof Error ? err.message : String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }
});
