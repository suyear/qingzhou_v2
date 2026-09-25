package com.qingzhou.modules.dashboard.mapper;

import com.qingzhou.modules.dashboard.dto.NamedCountVO;
import com.qingzhou.modules.dashboard.dto.TopItemVO;
import com.qingzhou.modules.dashboard.dto.TrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT COUNT(*) FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{start}
              AND COALESCE(start_time, create_time) < #{end}
            """)
    long countBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT DATE_FORMAT(COALESCE(start_time, create_time), '%Y-%m-%d') AS bucket,
                   COUNT(*) AS total,
                   CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count,
                   CAST(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS SIGNED) AS running_count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
            GROUP BY bucket
            ORDER BY bucket
            """)
    List<TrendRow> selectTrend(@Param("since") LocalDateTime since);

    @Select("""
            SELECT status AS name, COUNT(*) AS count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
            GROUP BY status
            ORDER BY count DESC
            """)
    List<NamedCountVO> selectStatusShare(@Param("since") LocalDateTime since);

    @Select("""
            SELECT trigger_type AS name, COUNT(*) AS count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
            GROUP BY trigger_type
            ORDER BY count DESC
            """)
    List<NamedCountVO> selectTriggerShare(@Param("since") LocalDateTime since);

    @Select("""
            SELECT workflow_id AS id,
                   COUNT(*) AS total,
                   CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
            GROUP BY workflow_id
            ORDER BY total DESC
            LIMIT #{limit}
            """)
    List<TopItemVO> selectTopWorkflows(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT component_code AS code,
                   COUNT(*) AS total,
                   CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count
            FROM qz_execution_node_log
            WHERE create_time >= #{since}
              AND component_code IS NOT NULL
              AND component_code <> ''
            GROUP BY component_code
            ORDER BY total DESC
            LIMIT #{limit}
            """)
    List<TopItemVO> selectTopComponents(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT workflow_id AS id,
                   COUNT(*) AS total,
                   CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
            GROUP BY workflow_id
            HAVING CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) > 0
            ORDER BY failed_count DESC
            LIMIT #{limit}
            """)
    List<TopItemVO> selectTopFailedWorkflows(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT component_code AS code,
                   COUNT(*) AS total,
                   CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS SIGNED) AS success_count,
                   CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) AS failed_count
            FROM qz_execution_node_log
            WHERE create_time >= #{since}
              AND component_code IS NOT NULL
              AND component_code <> ''
            GROUP BY component_code
            HAVING CAST(SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS SIGNED) > 0
            ORDER BY failed_count DESC
            LIMIT #{limit}
            """)
    List<TopItemVO> selectTopFailedComponents(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT duration_ms
            FROM qz_execution_instance
            WHERE duration_ms IS NOT NULL
              AND COALESCE(start_time, create_time) >= #{since}
              AND status IN ('SUCCESS', 'FAILED', 'TIMEOUT')
            """)
    List<Long> selectDurations(@Param("since") LocalDateTime since);

    @Select("""
            SELECT status AS name, COUNT(*) AS count
            FROM qz_execution_instance
            WHERE COALESCE(start_time, create_time) >= #{since}
              AND trigger_type = #{triggerType}
            GROUP BY status
            """)
    List<NamedCountVO> selectStatusShareByTrigger(@Param("since") LocalDateTime since,
                                                 @Param("triggerType") String triggerType);
}
