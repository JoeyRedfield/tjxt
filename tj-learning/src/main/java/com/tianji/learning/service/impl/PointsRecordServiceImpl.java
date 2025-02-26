package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {


    @Override
    public void addPointRecord(SignInMessage msg, PointsRecordType type) {
        if (msg.getUserId() == null || msg.getPoints() == null) {
            return;
        }
        int realPoints = msg.getPoints();
        Long userId = msg.getUserId();

        if (type.getMaxPoints() > 0) {
            // 先拿到 对应用户本周的 这个类型的 总的积分.
            LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(LocalDate.now());
            LocalDateTime weekEndTime = DateUtils.getWeekEndTime(LocalDate.now());

            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<PointsRecord>();
            wrapper.select("sum(points) as totalPoints");
            wrapper.eq("user_id", userId);
            wrapper.eq("type", type); // type.getValue是对应类型
            wrapper.between("create_time", weekBeginTime, weekEndTime);
            Map<String, Object> map = getMap(wrapper);

            int dbPoints = 0;
            if (map != null) {
                // 本周 该类型 获得的总的积分
                BigDecimal res = (BigDecimal) map.get("totalPoints");
                dbPoints = res.intValue();
            }
            if (dbPoints >= type.getMaxPoints()) {
                // 如果大于就返回就好, 抛出异常会导致消息队列重试
                return;
            }

            realPoints = dbPoints + realPoints;

            if (realPoints > type.getMaxPoints()) {
                // 如果需要加的分数已经超过最大点数, 那就得再计算
                realPoints = type.getMaxPoints() - dbPoints;
            }
        }

        PointsRecord pr = new PointsRecord();
        pr.setPoints(realPoints);
        pr.setType(type);
        pr.setUserId(userId);

        save(pr);
    }

    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        Long userId = UserContext.getUser();
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        // 主要是查登录用户 今天的 type和总分, 以type分组.
        wrapper.select("type", "sum(points) as points");
        wrapper.eq("user_id", userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
        wrapper.between("create_time", dayStartTime, dayEndTime);
        wrapper.groupBy("type");
        List<PointsRecord> list = list(wrapper);

        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }

        List<PointsStatisticsVO> res = new ArrayList<>();
        for (PointsRecord pointsRecord : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setPoints(pointsRecord.getPoints());
            vo.setMaxPoints(pointsRecord.getType().getMaxPoints());
            vo.setType(pointsRecord.getType().getDesc());

            res.add(vo);
        }

        return res;
    }
}
