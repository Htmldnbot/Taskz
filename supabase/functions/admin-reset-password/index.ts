// Follow Deno deploy standards for Supabase Edge Functions
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
  // Handle CORS preflight
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

    // 1. Authenticate the caller using the Bearer token
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: "Missing authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // User client with caller's token to verify identity
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

    // Admin client with service_role privileges
    const adminClient = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 2. Verify caller has admin role and is active
    const { data: callerProfile, error: profileError } = await adminClient
      .from("profiles")
      .select("app_role, is_active")
      .eq("auth_user_id", user.id)
      .single();

    if (profileError || !callerProfile || callerProfile.app_role !== "admin" || !callerProfile.is_active) {
      return new Response(
        JSON.stringify({ error: "Caller does not have administrative privileges" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Parse request payload
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

    // 4. Find the target profile
    let targetEmployeeId = employeeId;
    if (!targetEmployeeId && requestId) {
      const { data: requestRecord, error: reqErr } = await adminClient
        .from("password_reset_requests")
        .select("employee_id")
        .eq("id", requestId)
        .single();

      if (reqErr || !requestRecord) {
        return new Response(
          JSON.stringify({ error: "Password reset request not found" }),
          { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
      targetEmployeeId = requestRecord.employee_id;
    }

    const { data: targetProfile, error: targetError } = await adminClient
      .from("profiles")
      .select("id, auth_user_id, employee_id, full_name")
      .ilike("employee_id", targetEmployeeId!.trim())
      .single();

    if (targetError || !targetProfile) {
      return new Response(
        JSON.stringify({ error: `No profile matching Employee ID: ${targetEmployeeId}` }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Update password using Supabase Auth Admin API
    const { error: resetAuthError } = await adminClient.auth.admin.updateUserById(
      targetProfile.auth_user_id,
      { password: newTemporaryPassword }
    );

    if (resetAuthError) {
      return new Response(
        JSON.stringify({ error: `Auth reset failed: ${resetAuthError.message}` }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 6. Mark profile flag must_change_password = true
    await adminClient
      .from("profiles")
      .update({ must_change_password: true, updated_at: new Date().toISOString() })
      .eq("id", targetProfile.id);

    // 7. If there is a request ID, update its status
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
      // Mark any pending request for this employee as resolved
      await adminClient
        .from("password_reset_requests")
        .update({
          status: "resolved",
          resolved_at: new Date().toISOString(),
          resolved_by: user.id,
        })
        .ilike("employee_id", targetEmployeeId!.trim())
        .eq("status", "pending");
    }

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
