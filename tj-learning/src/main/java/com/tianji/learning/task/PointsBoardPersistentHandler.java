package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constans.LearningConstants;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 创建上赛季(上个月)榜单表
     */
//    @Scheduled(cron = "0 0 3 1 * ?")
//    @Scheduled(cron = "0 * * * * * ")
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        // 获取本月本日
        LocalDate now = LocalDate.now();
        log.debug("单机版建Season表任务时间: {}", now);
        // -1月就是上个月
        LocalDate time = now.minusMonths(1);
        PointsBoardSeason one = pointsBoardSeasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (one == null) {
            return;
        }
        pointsBoardSeasonService.createLatestPointsBoardTableOfLastSeason(one.getId());
    }

    // 持久化上赛季(上个月的)排行榜数据 到db中
    @XxlJob("savePointsBoard2DB")//任务名字要和xxljob控制台, 任务的jobhandler值保持一致
    public void savePointsBoard2DB() {
        // 获取本月本日
        LocalDate now = LocalDate.now();
        // -1月就是上个月
        LocalDate time = now.minusMonths(1);
        PointsBoardSeason one = pointsBoardSeasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (one == null) {
            return;
        }
        Integer seasonId = one.getId();
        String tableName = LearningConstants.POINTS_BOARD_TABLE_PREFIX + seasonId;
        TableInfoContext.setInfo(tableName); // 赛季命名, 也是表名
        // 按分页获取数据, 避免OOM

        String format = DateUtils.format(time, "yyyyMM");
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format; // 获取赛季信息的 key

        int shardIndex = XxlJobHelper.getShardIndex(); // 当前分片的索引, 从0开始
        int shardTotal = XxlJobHelper.getShardTotal(); // 总分片数
        int pageNo = shardIndex + 1;
        int pageSize = 2;

        while (true) {
            log.debug("处理 第 {} 页数据,", pageNo);
            // 从redis查赛季数据
            List<PointsBoard> list = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(list)) {
                break; // 说明数据处理完了
            }
            for (PointsBoard board : list) {
                // 因为新建的表没有rank和season
                board.setId(board.getRank().longValue());
//                board.setSeason(null);
                board.setRank(null);
            }
            pageNo += shardTotal;
            // 持久化保存到db
            pointsBoardService.saveBatch(list);
            // todo 删除redis的数据

        }
        // 清除这次赛季命名
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        log.debug("开始清理redis PointsBoard 数据");
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String format = DateUtils.format(time, "yyyyMM");
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format; // 获取赛季信息的 key
        // 3.删除
        redisTemplate.unlink(key);
    }

}
