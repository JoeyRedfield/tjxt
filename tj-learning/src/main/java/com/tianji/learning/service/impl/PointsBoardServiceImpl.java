package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardList(PointsBoardQuery query) {
        // 获取用户id
        Long userId = UserContext.getUser();
        // 查询排名和积分
        Long season = query.getSeason();
        if(season == null) {
            season = 0L;
        }
        boolean isCurrent = season.equals(0L);
        LocalDate now = LocalDate.now();
        String format = DateUtils.format(now, "yyyyMM");

        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 当前赛季查redis, 历史赛季查数据库.
        PointsBoard myBoard = isCurrent ? queryMyPointsBoard(key) : queryHistoryPointsBoard();
        // list同理

//        String boardListKey = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        List<PointsBoard> list = isCurrent ? queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) : queryHistoryBoardList();
        // 获取userId和name
        Set<Long> collect = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(collect);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> userDtoMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));

        PointsBoardVO pointsBoardVO = new PointsBoardVO();

        List<PointsBoardItemVO> temp = new ArrayList<>();
        for (PointsBoard board : list) {
            PointsBoardItemVO vo = new PointsBoardItemVO();
            vo.setName(userDtoMap.get(board.getUserId()));
            vo.setPoints(board.getPoints());
            vo.setRank(board.getRank());
            temp.add(vo);
        }

        pointsBoardVO.setRank(myBoard.getRank());
        pointsBoardVO.setPoints(myBoard.getPoints());
        pointsBoardVO.setBoardList(temp);
        return pointsBoardVO;
    }

    private List<PointsBoard> queryHistoryBoardList() {
        return CollUtils.emptyList();
    }

    public List<PointsBoard> queryCurrentBoardList(String boardListKey, @Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize - 1;
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(boardListKey, start, end);
        if(CollUtils.isEmpty(typedTuples)){
            return CollUtils.emptyList();
        }
        List<PointsBoard> list = new ArrayList<>();
        int rank = start + 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String userId = typedTuple.getValue();
            Double score = typedTuple.getScore();
            PointsBoard board = new PointsBoard();
            board.setRank(rank++);
            board.setPoints(score == null ? 0 : score.intValue());
            board.setUserId(userId == null ? null : Long.valueOf(userId));
            list.add(board);
        }
        return list;
    }

    private PointsBoard queryMyPointsBoard(String key) {

        Long userId = UserContext.getUser();
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        rank = rank == null ? 0 : rank + 1;

        Double score = redisTemplate.opsForZSet().score(key, UserContext.getUser().toString());
        score = score == null ? 0D : score;

        PointsBoard board = new PointsBoard();
        board.setUserId(UserContext.getUser());
        board.setPoints(score.intValue());
        board.setRank(rank.intValue());
        board.setSeason(0);

        return board;
    }

    private PointsBoard queryHistoryPointsBoard() {
        return new PointsBoard();
    }
}
