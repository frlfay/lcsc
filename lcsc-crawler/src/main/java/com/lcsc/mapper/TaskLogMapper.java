package com.lcsc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcsc.entity.TaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务日志Mapper接口
 * 扩展支持层级任务、性能统计和可视化查询
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {

    /**
     * 根据任务ID查询日志，按步骤顺序和时间排序
     */
    @Select("SELECT * FROM task_logs WHERE task_id = #{taskId} ORDER BY sequence_order, create_time DESC LIMIT #{limit}")
    List<TaskLog> selectLogsByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);

    /**
     * 查询指定时间范围内的任务日志
     */
    @Select("SELECT * FROM task_logs WHERE create_time BETWEEN #{startTime} AND #{endTime} ORDER BY create_time DESC LIMIT #{limit}")
    List<TaskLog> selectLogsByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                       @Param("endTime") LocalDateTime endTime, 
                                       @Param("limit") int limit);

    /**
     * 清理指定时间之前的日志
     */
    @Select("DELETE FROM task_logs WHERE create_time < #{beforeTime}")
    int deleteLogsBefore(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 获取任务的最新日志
     */
    @Select("SELECT * FROM task_logs WHERE task_id = #{taskId} ORDER BY create_time DESC LIMIT 1")
    TaskLog selectLatestLogByTaskId(@Param("taskId") String taskId);

    /**
     * 统计任务日志数量
     */
    @Select("SELECT COUNT(*) FROM task_logs WHERE task_id = #{taskId}")
    int countLogsByTaskId(@Param("taskId") String taskId);

    /**
     * 查询任务的完整执行流程（包含子任务）
     */
    @Select("SELECT * FROM task_logs WHERE task_id = #{taskId} OR parent_task_id = #{taskId} ORDER BY sequence_order, create_time")
    List<TaskLog> selectTaskFlow(@Param("taskId") String taskId);

    /**
     * 查询任务的子任务列表
     */
    @Select("SELECT * FROM task_logs WHERE parent_task_id = #{parentTaskId} ORDER BY sequence_order, create_time")
    List<TaskLog> selectChildTasks(@Param("parentTaskId") String parentTaskId);

    /**
     * 统计任务执行性能
     */
    @Select("SELECT " +
            "task_id, " +
            "task_type, " +
            "COUNT(*) as total_steps, " +
            "SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END) as error_count, " +
            "SUM(CASE WHEN level = 'SUCCESS' THEN 1 ELSE 0 END) as success_count, " +
            "AVG(duration_ms) as avg_duration_ms, " +
            "SUM(duration_ms) as total_duration_ms, " +
            "MIN(create_time) as start_time, " +
            "MAX(create_time) as end_time, " +
            "MAX(progress) as final_progress, " +
            "SUM(retry_count) as total_retries " +
            "FROM task_logs " +
            "WHERE task_id = #{taskId} " +
            "GROUP BY task_id, task_type")
    Map<String, Object> selectTaskPerformance(@Param("taskId") String taskId);

    /**
     * 查询活跃任务列表（最近1小时的任务）
     */
    @Select("SELECT DISTINCT task_id, task_type, MAX(create_time) as last_update " +
            "FROM task_logs " +
            "WHERE create_time > DATE_SUB(NOW(), INTERVAL 1 HOUR) " +
            "GROUP BY task_id, task_type " +
            "ORDER BY last_update DESC")
    List<Map<String, Object>> selectActiveTasks();

    /**
     * 查询任务错误热点
     */
    @Select("SELECT " +
            "step, " +
            "error_code, " +
            "COUNT(*) as error_count, " +
            "MAX(create_time) as last_error_time " +
            "FROM task_logs " +
            "WHERE level = 'ERROR' " +
            "AND create_time > DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "GROUP BY step, error_code " +
            "ORDER BY error_count DESC " +
            "LIMIT 20")
    List<Map<String, Object>> selectErrorHotspots();

    /**
     * 查询任务步骤的平均执行时间
     */
    @Select("SELECT " +
            "step, " +
            "AVG(duration_ms) as avg_duration, " +
            "MIN(duration_ms) as min_duration, " +
            "MAX(duration_ms) as max_duration, " +
            "COUNT(*) as execution_count " +
            "FROM task_logs " +
            "WHERE duration_ms IS NOT NULL " +
            "AND create_time > DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "GROUP BY step " +
            "ORDER BY avg_duration DESC")
    List<Map<String, Object>> selectStepPerformance();

    /**
     * 查询任务进度趋势
     */
    @Select("SELECT " +
            "DATE(create_time) as date, " +
            "task_type, " +
            "COUNT(DISTINCT task_id) as task_count, " +
            "SUM(CASE WHEN level = 'SUCCESS' AND step = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count " +
            "FROM task_logs " +
            "WHERE create_time > DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(create_time), task_type " +
            "ORDER BY date DESC")
    List<Map<String, Object>> selectTaskTrends();
}