import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface ResetPasswordPayload {
  requestId?: string;
  employeeId?: string;
  newTemporaryPassword: string;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({ error: "Missing server environment variables" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Authenticate caller
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: "Missing authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const userClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) {
      return new Response(
        JSON.stringify({ error: "Unauthorized caller session" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Verify caller is active admin using service role
    const adminClient = createClient(supabaseUrl, supabaseServiceRoleKey);

    const { data: callerProfile, error: profileError } = await adminClient
      .from("profiles")
      .select("id, app_role, is_active")
      .eq("id", user.id)
      .single();

    if (profileError || !callerProfile || callerProfile.app_role !== "admin" || !callerProfile.is_active) {
      return new Response(
        JSON.stringify({ error: "Caller does not have administrative privileges" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Parse input
    const payload: ResetPasswordPayload = await req.json();
    const { requestId, employeeId, newTemporaryPassword } = payload;

    if (!newTemporaryPassword || newTemporaryPassword.length < 6) {
      return new Response(
        JSON.stringify({ error: "Temporary password must be at least 6 characters" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!employeeId && !requestId) {
      return new Response(
        JSON.stringify({ error: "Either employeeId or requestId is required" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Find target profile: profiles.id = auth.users.id
    let targetProfileId: string | null = null;
    let targetEmployeeId = employeeId;

    if (requestId) {
      const { data: requestRecord, error: reqErr } = await adminClient
        .from("password_reset_requests")
        .select("id, target_user_id, employee_id")
        .eq("id", requestId)
        .single();

      if (reqErr || !requestRecord) {
        return new Response(
          JSON.stringify({ error: "Password reset request not found" }),
          { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
      targetProfileId = requestRecord.target_user_id;
      if (!targetEmployeeId) {
        targetEmployeeId = requestRecord.employee_id;
      }
    }

    let targetProfile: { id: string; employee_id: string; full_name: string } | null = null;

    if (targetProfileId) {
      const { data, error } = await adminClient
        .from("profiles")
        .select("id, employee_id, full_name")
        .eq("id", targetProfileId)
        .single();
      if (!error && data) targetProfile = data;
    }

    if (!targetProfile && targetEmployeeId) {
      const normalized = targetEmployeeId.trim().toUpperCase();
      const { data, error } = await adminClient
        .from("profiles")
        .select("id, employee_id, full_name")
        .eq("employee_id", normalized)
        .single();
      if (!error && data) targetProfile = data;
    }

    if (!targetProfile) {
      return new Response(
        JSON.stringify({ error: `No profile matching Employee ID: ${targetEmployeeId || targetProfileId}` }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Update user password in auth.users (targetProfile.id IS the auth user id!)
    const { error: resetAuthError } = await adminClient.auth.admin.updateUserById(
      targetProfile.id,
      { password: newTemporaryPassword }
    );

    if (resetAuthError) {
      return new Response(
        JSON.stringify({ error: `Auth reset failed: ${resetAuthError.message}` }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 6. Set must_change_password = true
    await adminClient
      .from("profiles")
      .update({ must_change_password: true, updated_at: new Date().toISOString() })
      .eq("id", targetProfile.id);

    // 7. Update password_reset_requests
    if (requestId) {
      await adminClient
        .from("password_reset_requests")
        .update({
          status: "resolved",
          resolved_at: new Date().toISOString(),
          resolved_by: user.id,
        })
        .eq("id", requestId);
    } else {
      await adminClient
        .from("password_reset_requests")
        .update({
          status: "resolved",
          resolved_at: new Date().toISOString(),
          resolved_by: user.id,
        })
        .eq("employee_id", targetProfile.employee_id)
        .eq("status", "pending");
    }

    // 8. Record in admin_audit_log
    await adminClient.from("admin_audit_log").insert({
      admin_user_id: user.id,
      target_user_id: targetProfile.id,
      action: "ADMIN_RESET_PASSWORD",
      entity_type: "user",
      entity_id: targetProfile.id,
      metadata: { employee_id: targetProfile.employee_id }
    });

    return new Response(
      JSON.stringify({
        success: true,
        message: `Temporary password set for ${targetProfile.full_name} (${targetProfile.employee_id})`,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: err.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
