package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zywu
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {


    void createLatestPointsBoardTableOfLastSeason(Integer one);
}
