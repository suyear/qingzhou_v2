package com.qingzhou.modules.dashboard.mapper;

import com.qingzhou.modules.dashboard.dto.NamedCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ModuleStatsMapper {

    @Select("SELECT COUNT(*) FROM qz_api_component WHERE deleted = 0")
    long countComponents();

    @Select("SELECT COUNT(*) FROM qz_api_component WHERE deleted = 0 AND category = 'DATABASE'")
    long countDatabaseComponents();

    @Select("SELECT COUNT(*) FROM qz_api_component WHERE deleted = 0 AND status = 1")
    long countEnabledComponents();

    @Select("""
            SELECT COUNT(*) FROM qz_workflow w
            WHERE w.deleted = 0
              AND w.graph_json LIKE CONCAT('%', #{componentId}, '%')
            """)
    long countWorkflowsReferencingComponent(@Param("componentId") Long componentId);

    @Select("SELECT COUNT(*) FROM qz_credential WHERE deleted = 0")
    long countCredentials();

    @Select("SELECT COUNT(*) FROM qz_credential WHERE deleted = 0 AND status = 1")
    long countEnabledCredentials();

    @Select("""
            SELECT credential_type AS name, COUNT(*) AS count
            FROM qz_credential WHERE deleted = 0
            GROUP BY credential_type
            """)
    List<NamedCountVO> countCredentialsByType();

    @Select("""
            SELECT COUNT(*) FROM qz_workflow w
            WHERE w.deleted = 0
              AND (
                w.credential_id = #{credentialId}
                OR w.graph_json LIKE CONCAT('%', #{credentialId}, '%')
              )
            """)
    long countWorkflowsReferencingCredential(@Param("credentialId") Long credentialId);

    @Select("SELECT COUNT(*) FROM qz_workflow WHERE deleted = 0")
    long countWorkflows();

    @Select("SELECT COUNT(*) FROM qz_workflow WHERE deleted = 0 AND status = #{status}")
    long countWorkflowsByStatus(@Param("status") String status);

    @Select("""
            SELECT CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED','TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
            """)
    Map<String, Object> executionOutcomeSince(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM qz_schedule_job WHERE deleted = 0")
    long countSchedules();

    @Select("SELECT COUNT(*) FROM qz_schedule_job WHERE deleted = 0 AND status = #{status}")
    long countSchedulesByStatus(@Param("status") int status);

    @Select("""
            SELECT COUNT(*) FROM qz_schedule_job
            WHERE deleted = 0 AND status = 1
              AND next_fire_time IS NOT NULL
              AND next_fire_time >= NOW()
              AND next_fire_time < DATE_ADD(NOW(), INTERVAL 1 DAY)
            """)
    long countSchedulesNext24h();

    @Select("""
            SELECT CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED','TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count
            FROM qz_execution_instance
            WHERE trigger_type = 'SCHEDULE'
              AND COALESCE(start_time, create_time) >= #{since}
            """)
    Map<String, Object> scheduleOutcomeSince(@Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(*) AS total,
                   CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS SIGNED) AS failed_count,
                   CAST(SUM(CASE WHEN status = 'TIMEOUT' THEN 1 ELSE 0 END) AS SIGNED) AS timeout_count,
                   CAST(AVG(CASE WHEN duration_ms IS NOT NULL THEN duration_ms END) AS SIGNED) AS avg_ms
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{start}
              AND COALESCE(start_time, create_time) < #{end}
            """)
    Map<String, Object> executionToday(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM qz_openapi_app WHERE deleted = 0")
    long countOpenapiApps();

    @Select("""
            SELECT COUNT(*) FROM qz_openapi_app a
            WHERE a.deleted = 0 AND a.status = 1
              AND EXISTS (
                SELECT 1 FROM qz_openapi_app_workflow g WHERE g.app_id = a.id
              )
            """)
    long countReadyOpenapiApps();

    @Select("""
            SELECT COUNT(*) FROM qz_execution_instance
            WHERE trigger_type = 'OPENAPI'
              AND COALESCE(start_time, create_time) >= #{since}
            """)
    long countOpenapiInvokes(@Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(*) FROM qz_execution_instance
            WHERE trigger_type = 'OPENAPI'
              AND status IN ('FAILED','TIMEOUT')
              AND COALESCE(start_time, create_time) >= #{since}
            """)
    long countOpenapiFails(@Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(*) FROM qz_execution_instance
            WHERE status IN ('FAILED','TIMEOUT')
              AND COALESCE(start_time, create_time) >= #{since}
            """)
    long countRecentFailures(@Param("since") LocalDateTime since);
}
