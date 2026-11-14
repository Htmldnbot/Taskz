import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function normalizeEmployeeId(value: string): string {
  return value.trim().split("@")[0].trim().toUpperCase();
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse(405, { error: "Method not allowed" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

  if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceRoleKey) {
    return jsonResponse(500, { error: "Server authentication configuration incomplete." });
  }

  let employeeIdRaw = "";
  let password = "";
  try {
    const body = await req.json();
    employeeIdRaw = typeof body?.employee_id === "string" ? body.employee_id : "";
    password = typeof body?.password === "string" ? body.password : "";
  } catch {
    return jsonResponse(400, { error: "Invalid JSON request body." });
  }

  const normalizedEmpId = normalizeEmployeeId(employeeIdRaw);
  if (!/^[A-Z0-9._-]{2,40}$/.test(normalizedEmpId) || password.length === 0) {
    return jsonResponse(400, { error: "Valid Employee ID and password are required." });
  }

  const adminClient = createClient(supabaseUrl, supabaseServiceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: profile, error: profileErr } = await adminClient
    .from("profiles")
    .select("id, employee_id, is_active")
    .eq("employee_id", normalizedEmpId)
    .maybeSingle();

  if (profileErr || !profile?.id) {
    // Same generic error regardless of whether Employee ID exists to prevent enumeration
    return jsonResponse(401, { error: "Invalid Employee ID or password." });
  }

  const { data: authData, error: userErr } = await adminClient.auth.admin.getUserById(profile.id);
  const resolvedEmail = authData?.user?.email;
  if (userErr || !resolvedEmail) {
    return jsonResponse(401, { error: "Invalid Employee ID or password." });
  }

  const anonClient = createClient(supabaseUrl, supabaseAnonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: signInData, error: signInErr } = await anonClient.auth.signInWithPassword({
    email: resolvedEmail,
    password,
  });

  if (signInErr || !signInData.session || !signInData.user) {
    return jsonResponse(401, { error: "Invalid Employee ID or password." });
  }

  if (!profile.is_active) {
    return jsonResponse(403, {
      error: "Your Taskz account is currently inactive. Please contact the administrator.",
    });
  }

  // Return authenticated Supabase session without exposing user's email
  return jsonResponse(200, {
    access_token: signInData.session.access_token,
    refresh_token: signInData.session.refresh_token,
    expires_in: signInData.session.expires_in,
    token_type: signInData.session.token_type,
    employee_id: profile.employee_id,
    user: {
      id: signInData.user.id,
      user_metadata: signInData.user.user_metadata ?? {},
    },
  });
});
