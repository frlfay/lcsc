package com.lcsc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcsc.entity.CrawlerTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 爬虫任务Mapper接口
 * 
 * @author lcsc-crawler
 * @since 2025-09-08
 */
@Mapper
public interface CrawlerTaskMapper extends BaseMapper<CrawlerTask> {

    /**
     * 根据任务状态查询任务列表
     */
    @Select("SELECT * FROM crawler_tasks WHERE task_status = #{status} ORDER BY created_at DESC LIMIT #{limit}")
    List<CrawlerTask> selectByStatus(@Param("status") String status, @Param("limit") int limit);

    /**
     * 根据批次ID查询任务列表
     */
    @Select("SELECT * FROM crawler_tasks WHERE batch_id = #{batchId} ORDER BY created_at DESC")
    List<CrawlerTask> selectByBatchId(@Param("batchId") String batchId);

    /**
     * 根据任务类型查询任务统计
     */
    @Select("SELECT task_type, task_status, COUNT(*) as count FROM crawler_tasks " +
            "WHERE created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY task_type, task_status")
    List<Map<String, Object>> selectTaskStatistics(@Param("startTime") LocalDateTime startTime, 
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 查询需要持久化的已完成任务
     * （Redis中已完成但数据库中不存在的任务）
     */
    @Select("SELECT COUNT(*) FROM crawler_tasks WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") String taskId);

    /**
     * 更新任务状态
     */
    @Update("UPDATE crawler_tasks SET task_status = #{status}, updated_at = NOW() WHERE task_id = #{taskId}")
    int updateTaskStatus(@Param("taskId") String taskId, @Param("status") String status);

    /**
     * 更新任务完成信息
     */
    @Update("UPDATE crawler_tasks SET task_status = #{status}, completed_at = #{completedAt}, " +
            "execution_duration_ms = #{durationMs}, task_result = #{result}, updated_at = NOW() " +
            "WHERE task_id = #{taskId}")
    int updateTaskCompletion(@Param("taskId") String taskId, 
                           @Param("status") String status,
                           @Param("completedAt") LocalDateTime completedAt,
                           @Param("durationMs") Long durationMs,
                           @Param("result") String result);

    /**
     * 查询最近完成的任务（用于持久化检查）
     */
    @Select("SELECT * FROM crawler_tasks WHERE task_status IN ('COMPLETED', 'FAILED') " +
            "AND completed_at >= #{since} ORDER BY completed_at DESC LIMIT #{limit}")
    List<CrawlerTask> selectRecentCompletedTasks(@Param("since") LocalDateTime since, 
                                               @Param("limit") int limit);

    /**
     * 查询长时间运行的任务（可能存在问题）
     */
    @Select("SELECT * FROM crawler_tasks WHERE task_status = 'PROCESSING' " +
            "AND started_at < #{threshold} ORDER BY started_at ASC")
    List<CrawlerTask> selectLongRunningTasks(@Param("threshold") LocalDateTime threshold);

    /**
     * 删除过期的已完成任务
     */
    @Select("DELETE FROM crawler_tasks WHERE task_status IN ('COMPLETED', 'FAILED') " +
            "AND completed_at < #{expireTime}")
    int deleteExpiredTasks(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 获取任务执行统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as total_tasks, " +
            "SUM(CASE WHEN task_status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_tasks, " +
            "SUM(CASE WHEN task_status = 'FAILED' THEN 1 ELSE 0 END) as failed_tasks, " +
            "SUM(CASE WHEN task_status = 'PROCESSING' THEN 1 ELSE 0 END) as processing_tasks, " +
            "AVG(execution_duration_ms) as avg_duration, " +
            "MAX(execution_duration_ms) as max_duration, " +
            "MIN(execution_duration_ms) as min_duration " +
            "FROM crawler_tasks WHERE created_at >= #{since}")
    Map<String, Object> selectExecutionStatistics(@Param("since") LocalDateTime since);

    /**
     * 获取每日任务完成统计
     */
    @Select("SELECT " +
            "DATE(completed_at) as completion_date, " +
            "task_type, " +
            "COUNT(*) as completed_count, " +
            "AVG(execution_duration_ms) as avg_duration " +
            "FROM crawler_tasks " +
            "WHERE task_status = 'COMPLETED' AND completed_at >= #{since} " +
            "GROUP BY DATE(completed_at), task_type " +
            "ORDER BY completion_date DESC, task_type")
    List<Map<String, Object>> selectDailyCompletionStats(@Param("since") LocalDateTime since);
}