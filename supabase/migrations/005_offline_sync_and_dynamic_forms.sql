-- ============================================================
-- TASKZ - MIGRATION 005: OFFLINE SYNC AND DYNAMIC FORMS
-- Supabase PostgreSQL Migration
-- ============================================================

begin;

-- BATCH / OUTBOX SYNC RPC FUNCTION
-- Processes queued offline operations atomically with idempotency protection
create or replace function public.sync_offline_events(
    p_events jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_event jsonb;
    v_results jsonb := '[]'::jsonb;
    v_op_type text;
    v_event_id text;
    v_task_id uuid;
    v_resp_res jsonb;
    v_ack_res boolean;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    for v_event in select * from jsonb_array_elements(p_events)
    loop
        v_op_type := v_event->>'operation_type';
        v_event_id := v_event->>'event_id';
        v_task_id := (v_event->>'task_id')::uuid;

        begin
            if v_op_type in ('TASK_RESPONSE', 'PROGRESS_UPDATE', 'LAN_CONFIRMATION', 'COMPLETION_REQUEST') then
                v_resp_res := public.submit_task_response(
                    p_task_id := v_task_id,
                    p_response_type := coalesce(v_event->>'response_type', 'COMPLETED_NOT_COMPLETED'),
                    p_response_value := v_event->>'response_value',
                    p_percentage := (v_event->>'percentage')::integer,
                    p_numeric_1 := (v_event->>'numeric_1')::numeric,
                    p_numeric_2 := (v_event->>'numeric_2')::numeric,
                    p_text := v_event->>'text_value',
                    p_lan_number := v_event->>'lan_number',
                    p_idempotency_key := v_event_id,
                    p_device_timestamp := (v_event->>'device_timestamp')::timestamptz,
                    p_dynamic_values := coalesce(v_event->'dynamic_values', '{}'::jsonb)
                );

                v_results := v_results || jsonb_build_object(
                    'event_id', v_event_id,
                    'status', 'SYNCED',
                    'server_result', v_resp_res,
                    'error', null
                );

            elsif v_op_type = 'TASK_ACKNOWLEDGEMENT' then
                v_ack_res := public.acknowledge_task_alarm(
                    p_task_id := v_task_id,
                    p_event_id := v_event_id,
                    p_device_timestamp := coalesce((v_event->>'device_timestamp')::timestamptz, now())
                );

                v_results := v_results || jsonb_build_object(
                    'event_id', v_event_id,
                    'status', 'SYNCED',
                    'server_result', jsonb_build_object('acknowledged', true),
                    'error', null
                );
            else
                v_results := v_results || jsonb_build_object(
                    'event_id', v_event_id,
                    'status', 'FAILED_PERMANENT',
                    'error', 'Unsupported offline operation type: ' || coalesce(v_op_type, 'unknown')
                );
            end if;

        exception when others then
            v_results := v_results || jsonb_build_object(
                'event_id', v_event_id,
                'status', 'FAILED_RETRYABLE',
                'error', SQLERRM
            );
        end;
    end loop;

    return jsonb_build_object(
        'success', true,
        'synced_count', jsonb_array_length(v_results),
        'results', v_results
    );
end;
$$;

revoke all on function public.sync_offline_events(jsonb) from public;
grant execute on function public.sync_offline_events(jsonb) to authenticated;

commit;
