package com.tianji.learning.service.impl;

import com.tianji.learning.constans.LearningConstants;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public void createLatestPointsBoardTableOfLastSeason(Integer one) {
        String tableName = LearningConstants.POINTS_BOARD_TABLE_PREFIX + one;
        getBaseMapper().createLastSeasonTable(tableName);
    }
}
